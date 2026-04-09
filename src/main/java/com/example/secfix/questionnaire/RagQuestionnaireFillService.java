package com.example.secfix.questionnaire;

import com.example.secfix.completion.CompletionService;
import com.example.secfix.config.OpenAiProperties;
import com.example.secfix.domain.AnswerEvidence;
import com.example.secfix.domain.AnswerStatus;
import com.example.secfix.domain.GeneratedAnswer;
import com.example.secfix.domain.QuestionFieldType;
import com.example.secfix.domain.QuestionnaireField;
import com.example.secfix.domain.QuestionnaireItem;
import com.example.secfix.domain.QuestionnaireRun;
import com.example.secfix.embedding.EmbeddingService;
import com.example.secfix.exception.DocumentParsingException;
import com.example.secfix.prompt.PromptBuilder;
import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.repository.AnswerEvidenceRepository;
import com.example.secfix.repository.GeneratedAnswerRepository;
import com.example.secfix.repository.QuestionnaireFieldRepository;
import com.example.secfix.repository.QuestionnaireItemRepository;
import com.example.secfix.repository.QuestionnaireRunRepository;
import com.example.secfix.parser.XlsxFileSupport;
import com.example.secfix.vectorsearch.ScoredChunk;
import com.example.secfix.vectorsearch.VectorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagQuestionnaireFillService implements QuestionnaireFillService {

    private final WorkbookExtractor workbookExtractor;
    private final WorkbookSchemaInferenceService schemaInferenceService;
    private final WorkbookSchemaNormalizer schemaNormalizer;
    private final QuestionnaireExtractor questionnaireExtractor;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final CompletionService completionService;
    private final PromptBuilder promptBuilder;
    private final QuestionnaireRunRepository questionnaireRunRepository;
    private final QuestionnaireItemRepository questionnaireItemRepository;
    private final QuestionnaireFieldRepository questionnaireFieldRepository;
    private final GeneratedAnswerRepository generatedAnswerRepository;
    private final AnswerEvidenceRepository answerEvidenceRepository;
    private final OpenAiProperties openAiProperties;

    @Value("${rag.retrieval.top-k:5}")
    private int topK;

    @Override
    public QuestionnaireFillResult fill(MultipartFile questionnaire, String customerId) {
        log.info("Starting questionnaire fill for: {} (customer={})", questionnaire.getOriginalFilename(), customerId);
        XlsxFileSupport.requireXlsx(
                questionnaire.getContentType(),
                questionnaire.getOriginalFilename(),
                "questionnaire filling"
        );
        QuestionnaireRun run = initializeRun(questionnaire, customerId);

        try {
            run.markInProgress();
            questionnaireRunRepository.save(run);

            WorkbookSchema schema = inferSchema(questionnaire);
            if (schema.sheets().isEmpty()) {
                throw new DocumentParsingException("LLM found no questionnaire sheets in the uploaded file");
            }

            List<Question> questions = extractQuestions(questionnaire, schema);
            log.info("Extracted {} questions across {} sheets", questions.size(), schema.sheets().size());

            run.setTotalFields(questions.stream().mapToInt(q -> q.answerColumns().size()).sum());
            questionnaireRunRepository.save(run);

            byte[] filledWorkbook = fillWorkbook(questionnaire, questions, run, customerId);
            run.setOutputFileName(outputFilenameFor(questionnaire));
            run.markCompleted();
            questionnaireRunRepository.save(run);

            return new QuestionnaireFillResult(filledWorkbook, run.getRunId());

        } catch (RuntimeException e) {
            markRunFailed(run, e);
            throw e;
        } catch (Exception e) {
            markRunFailed(run, e);
            throw new DocumentParsingException("Unexpected error while filling questionnaire", e);
        }
    }

    private WorkbookSchema inferSchema(MultipartFile questionnaire) {
        try {
            var snapshot = workbookExtractor.extract(questionnaire.getInputStream());
            WorkbookSchema inferred = schemaInferenceService.infer(snapshot);
            return schemaNormalizer.normalize(inferred, snapshot);
        } catch (IOException e) {
            throw new DocumentParsingException("Failed to read questionnaire file", e);
        }
    }

    private List<Question> extractQuestions(MultipartFile questionnaire, WorkbookSchema schema) {
        try {
            return questionnaireExtractor.extract(questionnaire.getInputStream(), schema);
        } catch (IOException e) {
            throw new DocumentParsingException("Failed to re-read questionnaire file for extraction", e);
        }
    }

    private byte[] fillWorkbook(
            MultipartFile questionnaire,
            List<Question> questions,
            QuestionnaireRun run,
            String customerId
    ) {
        try (Workbook workbook = WorkbookFactory.create(questionnaire.getInputStream())) {
            for (Question question : questions) {
                Sheet sheet = workbook.getSheetAt(question.sheetIndex());
                Row row = sheet.getRow(question.rowIndex());
                if (row == null) row = sheet.createRow(question.rowIndex());

                QuestionnaireItem item = persistItem(run, question, customerId);

                // First pass: resolve constrained (dropdown) columns
                java.util.Map<Integer, AnswerGenerationResult> constrainedResults = new java.util.LinkedHashMap<>();
                String selectedResponse = null;
                for (AnswerColumnSchema answerCol : question.answerColumns()) {
                    if (!answerCol.isConstrained()) continue;
                    AnswerGenerationResult result = resolveAnswer(question.questionText(), answerCol, customerId, null);
                    constrainedResults.put(answerCol.columnIndex(), result);
                    if (result.status() == AnswerStatus.GENERATED) {
                        selectedResponse = result.answerText();
                    }
                }

                // Second pass: write all columns, resolving free-text with the selected response
                boolean hasConstrainedColumn = !constrainedResults.isEmpty();
                for (AnswerColumnSchema answerCol : question.answerColumns()) {
                    QuestionnaireField field = persistField(item, question, answerCol, customerId);
                    AnswerGenerationResult result;
                    if (answerCol.isConstrained()) {
                        result = constrainedResults.get(answerCol.columnIndex());
                    } else if (hasConstrainedColumn && selectedResponse == null) {
                        // Skip free-text generation when the primary response could not be answered
                        result = new AnswerGenerationResult(
                                "", AnswerStatus.INSUFFICIENT_CONTEXT, 0.0, List.of());
                    } else {
                        result = resolveAnswer(question.questionText(), answerCol, customerId, selectedResponse);
                    }

                    Cell cell = row.getCell(answerCol.columnIndex(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell == null) cell = row.createCell(answerCol.columnIndex());
                    cell.setCellValue(renderedCellValue(result, answerCol));

                    GeneratedAnswer generated = persistGeneratedAnswer(run, item, field, result, customerId);
                    persistEvidence(generated, result.evidenceChunks(), customerId);
                    incrementRunCounters(run, result.status());
                }
            }

            workbook.setForceFormulaRecalculation(true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            log.info("Questionnaire fill complete, returning {} bytes", out.size());
            return out.toByteArray();
        } catch (IOException e) {
            throw new DocumentParsingException("Failed to write answers to questionnaire workbook", e);
        }
    }

    private AnswerGenerationResult resolveAnswer(
            String questionText,
            AnswerColumnSchema answerCol,
            String customerId,
            String selectedResponse
    ) {
        float[] embedding = embeddingService.embed(questionText);
        List<ScoredChunk> chunks = vectorSearchService.search(embedding, topK, customerId);

        if (chunks.isEmpty()) {
            log.warn("No context chunks found for question: {}", questionText.substring(0, Math.min(80, questionText.length())));
            return new AnswerGenerationResult(
                    "INSUFFICIENT_CONTEXT",
                    AnswerStatus.INSUFFICIENT_CONTEXT,
                    0.0,
                    List.of()
            );
        }

        String userPrompt = promptBuilder.buildUserPrompt(
                questionText, chunks,
                answerCol.isConstrained() ? answerCol.allowedOptions() : List.of(),
                answerCol.isConstrained() ? null : selectedResponse,
                answerCol.hasMaxLength() ? answerCol.maxLength() : null
        );

        String rawAnswer = completionService.complete(PromptBuilder.SYSTEM_PROMPT, userPrompt);
        String answer = rawAnswer != null ? rawAnswer.strip() : "";
        if (answerCol.hasMaxLength() && answer.length() > answerCol.maxLength()) {
            log.warn("Truncating answer for '{}' from {} to {} chars",
                    answerCol.columnName(), answer.length(), answerCol.maxLength());
            answer = answer.substring(0, answerCol.maxLength());
        }
        if (answer.isBlank() || "INSUFFICIENT_CONTEXT".equalsIgnoreCase(answer)) {
            return new AnswerGenerationResult(
                    "INSUFFICIENT_CONTEXT",
                    AnswerStatus.INSUFFICIENT_CONTEXT,
                    topScore(chunks),
                    chunks
            );
        }

        if (answerCol.isConstrained()) {
            String normalizedOption = normalizeAllowedOption(answer, answerCol.allowedOptions());
            if (normalizedOption == null) {
                log.warn("Constrained answer mismatch for '{}': model='{}'", answerCol.columnName(), answer);
                return new AnswerGenerationResult(
                        "INSUFFICIENT_CONTEXT",
                        AnswerStatus.CONSTRAINT_VIOLATION,
                        topScore(chunks),
                        chunks
                );
            }
            answer = normalizedOption;
        }

        log.debug("Q: {} | Col: {} | A: {}", questionText.substring(0, Math.min(60, questionText.length())),
                answerCol.columnName(), answer);
        return new AnswerGenerationResult(answer, AnswerStatus.GENERATED, topScore(chunks), chunks);
    }

    private QuestionnaireRun initializeRun(MultipartFile questionnaire, String customerId) {
        QuestionnaireRun run = new QuestionnaireRun();
        String inputName = questionnaire.getOriginalFilename() != null
                ? questionnaire.getOriginalFilename()
                : "questionnaire.xlsx";
        run.setInputFileName(inputName);
        run.setCustomerId(customerId);
        return questionnaireRunRepository.save(run);
    }

    private QuestionnaireItem persistItem(QuestionnaireRun run, Question question, String customerId) {
        QuestionnaireItem item = new QuestionnaireItem();
        item.setRun(run);
        item.setSheetIndex(question.sheetIndex());
        item.setSheetName(question.sheetName());
        item.setRowIndex(question.rowIndex());
        item.setQuestionText(question.questionText());
        item.setCustomerId(customerId);
        return questionnaireItemRepository.save(item);
    }

    private QuestionnaireField persistField(
            QuestionnaireItem item,
            Question question,
            AnswerColumnSchema answerCol,
            String customerId
    ) {
        QuestionnaireField field = new QuestionnaireField();
        field.setItem(item);
        field.setColumnIndex(answerCol.columnIndex());
        field.setColumnName(answerCol.columnName());
        field.setFieldType(answerCol.isConstrained() ? QuestionFieldType.DROPDOWN : QuestionFieldType.TEXT);
        field.setConstrained(answerCol.isConstrained());
        field.setAllowedOptions(answerCol.allowedOptions() != null ? answerCol.allowedOptions() : List.of());
        field.setCellReference(new CellReference(question.rowIndex(), answerCol.columnIndex()).formatAsString());
        field.setCustomerId(customerId);
        return questionnaireFieldRepository.save(field);
    }

    private GeneratedAnswer persistGeneratedAnswer(
            QuestionnaireRun run,
            QuestionnaireItem item,
            QuestionnaireField field,
            AnswerGenerationResult result,
            String customerId
    ) {
        GeneratedAnswer entity = new GeneratedAnswer();
        entity.setRun(run);
        entity.setItem(item);
        entity.setField(field);
        entity.setAttemptNo(1);
        entity.setAnswerText(result.answerText());
        entity.setAnswerStatus(result.status());
        entity.setConfidenceScore(result.confidenceScore());
        entity.setModelName(openAiProperties.getCompletion().getModel());
        entity.setPromptVersion("context-only-v1");
        entity.setCustomerId(customerId);
        return generatedAnswerRepository.save(entity);
    }

    private void persistEvidence(GeneratedAnswer generatedAnswer, List<ScoredChunk> chunks, String customerId) {
        int rank = 1;
        for (ScoredChunk chunk : chunks) {
            AnswerEvidence evidence = new AnswerEvidence();
            evidence.setAnswer(generatedAnswer);
            evidence.setKnowledgeChunk(chunk.chunk());
            evidence.setRankPosition(rank++);
            evidence.setSimilarityScore(chunk.score());
            evidence.setSourcePageNum(chunk.chunk().getPageNum());
            evidence.setSourceDocumentTitle(safeDocumentTitle(chunk));
            evidence.setChunkSnippet(chunk.chunk().getChunkText());
            evidence.setCustomerId(customerId);
            answerEvidenceRepository.save(evidence);
        }
    }

    private String safeDocumentTitle(ScoredChunk chunk) {
        try {
            if (chunk.chunk().getDocument() == null) return null;
            return chunk.chunk().getDocument().getTitle();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void incrementRunCounters(QuestionnaireRun run, AnswerStatus status) {
        switch (status) {
            case GENERATED -> run.setAnsweredFields(run.getAnsweredFields() + 1);
            case INSUFFICIENT_CONTEXT -> run.setInsufficientContextFields(run.getInsufficientContextFields() + 1);
            case CONSTRAINT_VIOLATION, ERROR -> run.setFailedFields(run.getFailedFields() + 1);
        }
    }

    private String renderedCellValue(AnswerGenerationResult result, AnswerColumnSchema answerCol) {
        if (result.status() != AnswerStatus.GENERATED) {
            return "";
        }
        return result.answerText();
    }

    private String normalizeAllowedOption(String answer, List<String> options) {
        if (options == null || options.isEmpty()) return answer;

        for (String option : options) {
            if (option.equals(answer)) return option;
        }
        for (String option : options) {
            if (option.equalsIgnoreCase(answer)) return option;
        }
        return null;
    }

    private double topScore(List<ScoredChunk> chunks) {
        return chunks.isEmpty() ? 0.0 : chunks.getFirst().score();
    }

    private String outputFilenameFor(MultipartFile questionnaire) {
        return "filled_" + (questionnaire.getOriginalFilename() != null
                ? questionnaire.getOriginalFilename()
                : "questionnaire.xlsx");
    }

    private void markRunFailed(QuestionnaireRun run, Exception e) {
        try {
            run.markFailed(e.getMessage());
            questionnaireRunRepository.save(run);
        } catch (Exception saveException) {
            log.error("Failed to persist questionnaire run failure state", saveException);
        }
    }

    private record AnswerGenerationResult(
            String answerText,
            AnswerStatus status,
            Double confidenceScore,
            List<ScoredChunk> evidenceChunks
    ) {}
}

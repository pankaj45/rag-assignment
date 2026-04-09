package com.example.secfix.questionnaire;

import com.example.secfix.completion.CompletionService;
import com.example.secfix.config.OpenAiProperties;
import com.example.secfix.domain.*;
import com.example.secfix.embedding.EmbeddingService;
import com.example.secfix.exception.UnsupportedFileTypeException;
import com.example.secfix.prompt.PromptBuilder;
import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.SheetSchema;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;
import com.example.secfix.repository.*;
import com.example.secfix.vectorsearch.ScoredChunk;
import com.example.secfix.vectorsearch.VectorSearchService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagQuestionnaireFillServiceTest {

    private WorkbookExtractor workbookExtractor;
    @Mock
    private WorkbookSchemaInferenceService schemaInferenceService;
    private WorkbookSchemaNormalizer schemaNormalizer;
    private QuestionnaireExtractor questionnaireExtractor;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private VectorSearchService vectorSearchService;
    @Mock
    private CompletionService completionService;
    private PromptBuilder promptBuilder;
    @Mock
    private QuestionnaireRunRepository questionnaireRunRepository;
    @Mock
    private QuestionnaireItemRepository questionnaireItemRepository;
    @Mock
    private QuestionnaireFieldRepository questionnaireFieldRepository;
    @Mock
    private GeneratedAnswerRepository generatedAnswerRepository;
    @Mock
    private AnswerEvidenceRepository answerEvidenceRepository;
    private OpenAiProperties openAiProperties;

    private RagQuestionnaireFillService service;

    @BeforeEach
    void setUp() {
        workbookExtractor = new WorkbookExtractor(20);
        schemaNormalizer = new WorkbookSchemaNormalizer();
        questionnaireExtractor = new QuestionnaireExtractor();
        promptBuilder = new PromptBuilder();
        openAiProperties = new OpenAiProperties();
        OpenAiProperties.Completion completion = new OpenAiProperties.Completion();
        completion.setModel("gpt-test");
        openAiProperties.setCompletion(completion);
        service = new RagQuestionnaireFillService(
                workbookExtractor,
                schemaInferenceService,
                schemaNormalizer,
                questionnaireExtractor,
                embeddingService,
                vectorSearchService,
                completionService,
                promptBuilder,
                questionnaireRunRepository,
                questionnaireItemRepository,
                questionnaireFieldRepository,
                generatedAnswerRepository,
                answerEvidenceRepository,
                openAiProperties
        );
        ReflectionTestUtils.setField(service, "topK", 5);
    }

    @Test
    void fillTreatsLiteralInsufficientContextAsInsufficientStatus() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questionnaire.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createWorkbookBytes()
        );
        String customerId = "cust-1";

        WorkbookSchema schema = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "Sheet1",
                        1,
                        1,
                        List.of(new AnswerColumnSchema(2, "Answer", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()))
                )
        ));
        Document sourceDoc = new Document();
        sourceDoc.setTitle("Policy");
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocument(sourceDoc);
        chunk.setPageNum(2);
        chunk.setChunkText("All data at rest is encrypted.");
        chunk.setEmbedding(new float[]{1.0f, 0.0f});
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.93);

        when(schemaInferenceService.infer(any(WorkbookSnapshot.class))).thenReturn(schema);
        when(embeddingService.embed(any())).thenReturn(new float[]{1.0f, 0.0f});
        when(vectorSearchService.search(any(), eq(5), eq(customerId))).thenReturn(List.of(scoredChunk));
        when(completionService.complete(any(), any())).thenReturn("INSUFFICIENT_CONTEXT");

        when(questionnaireRunRepository.save(any())).thenAnswer(inv -> {
            QuestionnaireRun run = inv.getArgument(0);
            if (run.getRunId() == null) run.setRunId(UUID.randomUUID().toString());
            return run;
        });
        when(questionnaireItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionnaireFieldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(generatedAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireFillResult result = service.fill(file, customerId);

        assertNotNull(result.runId());
        assertTrue(result.workbookBytes().length > 0);

        try (Workbook output = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.workbookBytes()))) {
            String answerCell = output.getSheetAt(0).getRow(1).getCell(1).getStringCellValue();
            assertEquals("", answerCell, "INSUFFICIENT_CONTEXT should leave cell blank, not write literal text");
        }

        ArgumentCaptor<GeneratedAnswer> answerCaptor = ArgumentCaptor.forClass(GeneratedAnswer.class);
        verify(generatedAnswerRepository, atLeastOnce()).save(answerCaptor.capture());
        GeneratedAnswer saved = answerCaptor.getAllValues().getLast();

        assertEquals(AnswerStatus.INSUFFICIENT_CONTEXT, saved.getAnswerStatus());
        assertEquals(customerId, saved.getCustomerId());
    }

    @Test
    void fillRejectsNonXlsxQuestionnaire() {
        MockMultipartFile nonXlsx = new MockMultipartFile(
                "file",
                "questionnaire.xls",
                "application/vnd.ms-excel",
                "legacy".getBytes()
        );

        assertThrows(UnsupportedFileTypeException.class, () -> service.fill(nonXlsx, "cust-1"));
        verifyNoInteractions(questionnaireRunRepository);
    }

    @Test
    void fillLeavesDropdownBlankWhenNoAllowedOptionCanBeGenerated() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questionnaire.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createWorkbookBytes()
        );
        String customerId = "cust-1";

        WorkbookSchema schema = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "Sheet1",
                        1,
                        1,
                        List.of(new AnswerColumnSchema(
                                2,
                                "Answer",
                                AnswerColumnSchema.AnswerType.CONSTRAINED,
                                List.of("In place", "Partially in place", "Not in place", "Not applicable")
                        ))
                )
        ));

        Document sourceDoc = new Document();
        sourceDoc.setTitle("Policy");
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocument(sourceDoc);
        chunk.setPageNum(2);
        chunk.setChunkText("All data at rest is encrypted.");
        chunk.setEmbedding(new float[]{1.0f, 0.0f});
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.93);

        when(schemaInferenceService.infer(any(WorkbookSnapshot.class))).thenReturn(schema);
        when(embeddingService.embed(any())).thenReturn(new float[]{1.0f, 0.0f});
        when(vectorSearchService.search(any(), eq(5), eq(customerId))).thenReturn(List.of(scoredChunk));
        when(completionService.complete(any(), any())).thenReturn("INSUFFICIENT_CONTEXT");

        when(questionnaireRunRepository.save(any())).thenAnswer(inv -> {
            QuestionnaireRun run = inv.getArgument(0);
            if (run.getRunId() == null) run.setRunId(UUID.randomUUID().toString());
            return run;
        });
        when(questionnaireItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionnaireFieldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(generatedAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireFillResult result = service.fill(file, customerId);

        try (Workbook output = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.workbookBytes()))) {
            Cell answerCell = output.getSheetAt(0).getRow(1).getCell(1);
            assertEquals("", answerCell.getStringCellValue());
        }
    }

    @Test
    void fillPassesSelectedResponseToCommentColumn() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "questionnaire.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                createResponseAndCommentWorkbookBytes()
        );
        String customerId = "cust-1";

        WorkbookSchema schema = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "Sheet1",
                        0,
                        0,
                        List.of(
                                new AnswerColumnSchema(
                                        1,
                                        "Response",
                                        AnswerColumnSchema.AnswerType.CONSTRAINED,
                                        List.of("In place", "Partially in place", "Not in place", "Not applicable")
                                ),
                                new AnswerColumnSchema(
                                        2,
                                        "Comment",
                                        AnswerColumnSchema.AnswerType.FREE_TEXT,
                                        List.of()
                                )
                        )
                )
        ));

        Document sourceDoc = new Document();
        sourceDoc.setTitle("Policy");
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocument(sourceDoc);
        chunk.setPageNum(1);
        chunk.setChunkText("All data at rest is encrypted using AES-256.");
        chunk.setEmbedding(new float[]{1.0f, 0.0f});
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.95);

        when(schemaInferenceService.infer(any(WorkbookSnapshot.class))).thenReturn(schema);
        when(embeddingService.embed(any())).thenReturn(new float[]{1.0f, 0.0f});
        when(vectorSearchService.search(any(), eq(5), eq(customerId))).thenReturn(List.of(scoredChunk));

        // First call (constrained Response) returns a valid option; second call (Comment) returns justification
        when(completionService.complete(any(), any()))
                .thenReturn("In place")
                .thenReturn("Data at rest encryption is confirmed via AES-256 per security policy.");

        when(questionnaireRunRepository.save(any())).thenAnswer(inv -> {
            QuestionnaireRun run = inv.getArgument(0);
            if (run.getRunId() == null) run.setRunId(UUID.randomUUID().toString());
            return run;
        });
        when(questionnaireItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questionnaireFieldRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(generatedAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerEvidenceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireFillResult result = service.fill(file, customerId);

        try (Workbook output = new XSSFWorkbook(new java.io.ByteArrayInputStream(result.workbookBytes()))) {
            Cell responseCell = output.getSheetAt(0).getRow(1).getCell(1);
            Cell commentCell = output.getSheetAt(0).getRow(1).getCell(2);

            assertEquals("In place", responseCell.getStringCellValue());
            assertTrue(commentCell.getStringCellValue().contains("AES-256"));
        }

        // Verify the comment prompt included the selected response
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(completionService, times(2)).complete(any(), promptCaptor.capture());
        String commentPrompt = promptCaptor.getAllValues().get(1);
        assertTrue(commentPrompt.contains("In place"),
                "Comment prompt should reference the selected dropdown response");
        assertTrue(commentPrompt.contains("justification"),
                "Comment prompt should ask for justification");
    }

    private byte[] createResponseAndCommentWorkbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Question");
            header.createCell(1).setCellValue("Response");
            header.createCell(2).setCellValue("Comment");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Do you encrypt data at rest?");
            row.createCell(1).setCellValue("");
            row.createCell(2).setCellValue("");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createWorkbookBytes() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Question");
            header.createCell(1).setCellValue("Answer");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Do you encrypt data at rest?");
            row.createCell(1).setCellValue("");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

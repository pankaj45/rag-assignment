package com.example.secfix.questionnaire;

import com.example.secfix.completion.CompletionService;
import com.example.secfix.config.OpenAiProperties;
import com.example.secfix.exception.SchemaInferenceException;
import com.example.secfix.questionnaire.schema.SheetSnapshot;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmWorkbookSchemaInferenceService implements WorkbookSchemaInferenceService {

    private final CompletionService completionService;
    private final ObjectMapper objectMapper;
    private final OpenAiProperties openAiProperties;

    private static final String SYSTEM_PROMPT = """
            You are an expert at analyzing Excel workbook structures for security questionnaires.
            Analyze the provided workbook content and return a JSON object describing its structure.
            Return ONLY valid JSON — no explanation, no markdown fences, no extra text.
            """;

    @Override
    public WorkbookSchema infer(WorkbookSnapshot snapshot) {
        String userPrompt = buildUserPrompt(snapshot);
        log.debug("Sending workbook snapshot to LLM for schema inference");

        String rawJson = completionService.completeWithMaxTokens(
                SYSTEM_PROMPT,
                userPrompt,
                openAiProperties.getCompletion().getSchemaMaxTokens()
        );

        // Strip markdown fences if the model included them despite instructions
        String json = stripMarkdownFences(rawJson);

        try {
            WorkbookSchema schema = objectMapper.readValue(json, WorkbookSchema.class);
            log.info("LLM inferred schema: {} questionnaire sheets", schema.sheets().size());
            return schema;
        } catch (Exception e) {
            log.error("Failed to parse LLM schema response: {}", json);
            throw new SchemaInferenceException("LLM returned invalid JSON for workbook schema: " + e.getMessage(), e);
        }
    }

    private String buildUserPrompt(WorkbookSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                Analyze this Excel workbook and identify:
                1. Which sheets contain questionnaire questions (NOT summary, instructions, or index sheets).
                2. For each questionnaire sheet:
                   - headerRowIndex: the 0-based row index of the header row
                   - questionColumnIndex: the 0-based column index containing the question text
                   - answerColumns: all columns that need to be filled with answers
                3. For each answer column:
                   - columnIndex (0-based)
                   - columnName (from the header)
                   - answerType: "CONSTRAINED" if it must be one of a fixed list of options, "FREE_TEXT" otherwise
                   - allowedOptions: list of allowed values if CONSTRAINED, empty list if FREE_TEXT

                Look for instruction sheets or summary sheets — they will help you understand the format.
                Allowed options are often listed in the same row as the questions or in the header rows.

                Return JSON matching this exact structure:
                {
                  "sheets": [
                    {
                      "sheetIndex": 1,
                      "sheetName": "access control",
                      "headerRowIndex": 3,
                      "questionColumnIndex": 0,
                      "answerColumns": [
                        {
                          "columnIndex": 1,
                          "columnName": "Response",
                          "answerType": "CONSTRAINED",
                          "allowedOptions": ["In place", "Partially in place", "Not in place", "Not applicable"]
                        },
                        {
                          "columnIndex": 2,
                          "columnName": "Comment",
                          "answerType": "FREE_TEXT",
                          "allowedOptions": []
                        }
                      ]
                    }
                  ]
                }

                Workbook content:
                """);

        for (SheetSnapshot sheet : snapshot.sheets()) {
            sb.append("\n=== Sheet ").append(sheet.sheetIndex())
              .append(": ").append(sheet.sheetName()).append(" ===\n");
            List<List<String>> rows = sheet.rows();
            for (int ri = 0; ri < rows.size(); ri++) {
                sb.append("Row ").append(ri).append(": ").append(rows.get(ri)).append("\n");
            }
        }

        return sb.toString();
    }

    private String stripMarkdownFences(String text) {
        if (text == null) return "";
        String stripped = text.strip();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline > 0) stripped = stripped.substring(firstNewline + 1);
            if (stripped.endsWith("```")) stripped = stripped.substring(0, stripped.lastIndexOf("```"));
        }
        return stripped.strip();
    }
}

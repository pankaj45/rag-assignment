package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.SheetSchema;
import com.example.secfix.questionnaire.schema.SheetSnapshot;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbookSchemaNormalizerTest {

    private final WorkbookSchemaNormalizer normalizer = new WorkbookSchemaNormalizer();

    @Test
    void normalizeCorrectsOneBasedHeaderAndColumnIndexes() {
        WorkbookSnapshot snapshot = new WorkbookSnapshot(List.of(
                new SheetSnapshot(
                        0,
                        "access control",
                        List.of(
                                List.of("Title"),
                                List.of(),
                                List.of("meta"),
                                List.of("Question", "Response", "Comment", "Attachment(s)")
                        )
                )
        ));

        WorkbookSchema inferred = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "access control",
                        4,
                        1,
                        List.of(
                                new AnswerColumnSchema(2, "Response", AnswerColumnSchema.AnswerType.CONSTRAINED, List.of("Yes", "No")),
                                new AnswerColumnSchema(3, "Comment", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                        )
                )
        ));

        WorkbookSchema normalized = normalizer.normalize(inferred, snapshot);
        SheetSchema sheet = normalized.sheets().getFirst();

        assertEquals(3, sheet.headerRowIndex());
        assertEquals(0, sheet.questionColumnIndex());
        assertEquals(1, sheet.answerColumns().get(0).columnIndex());
        assertEquals(2, sheet.answerColumns().get(1).columnIndex());
    }

    @Test
    void normalizeFiltersNonFillableAdjacentColumns() {
        WorkbookSnapshot snapshot = new WorkbookSnapshot(List.of(
                new SheetSnapshot(
                        0,
                        "access control",
                        List.of(
                                List.of("title"),
                                List.of("Question", "Response", "Comment", "attachment(s)", "Follow-up Question", "Follow-up Response")
                        )
                )
        ));

        WorkbookSchema inferred = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "access control",
                        1,
                        0,
                        List.of(
                                new AnswerColumnSchema(1, "Response", AnswerColumnSchema.AnswerType.CONSTRAINED, List.of("Yes", "No")),
                                new AnswerColumnSchema(2, "Comment", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()),
                                new AnswerColumnSchema(3, "attachment(s)", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()),
                                new AnswerColumnSchema(4, "Follow-up Question", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()),
                                new AnswerColumnSchema(5, "Follow-up Response", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                        )
                )
        ));

        WorkbookSchema normalized = normalizer.normalize(inferred, snapshot);
        SheetSchema sheet = normalized.sheets().getFirst();

        assertEquals(List.of("Response", "Comment", "Follow-up Response"),
                sheet.answerColumns().stream().map(AnswerColumnSchema::columnName).toList());
        assertEquals(List.of(1, 2, 5),
                sheet.answerColumns().stream().map(AnswerColumnSchema::columnIndex).toList());
    }

    @Test
    void normalizeRepairsMissingSheetIdentityFromSnapshotOrder() {
        WorkbookSnapshot snapshot = new WorkbookSnapshot(List.of(
                new SheetSnapshot(
                        0,
                        "Summary",
                        List.of(List.of("Section", "Answered", "Total"))
                ),
                new SheetSnapshot(
                        1,
                        "access control",
                        List.of(
                                List.of("title"),
                                List.of("meta"),
                                List.of("Question", "Response", "Comment")
                        )
                ),
                new SheetSnapshot(
                        2,
                        "data security & cryptography",
                        List.of(
                                List.of("title"),
                                List.of("meta"),
                                List.of("Question", "Response", "Comment")
                        )
                )
        ));

        WorkbookSchema inferred = new WorkbookSchema(List.of(
                new SheetSchema(0, null, 0, 0, List.of(
                        new AnswerColumnSchema(1, "Response", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                )),
                new SheetSchema(0, null, 0, 0, List.of(
                        new AnswerColumnSchema(1, "Response", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                ))
        ));

        WorkbookSchema normalized = normalizer.normalize(inferred, snapshot);

        assertEquals(List.of(1, 2), normalized.sheets().stream().map(SheetSchema::sheetIndex).toList());
        assertEquals(List.of("access control", "data security & cryptography"),
                normalized.sheets().stream().map(SheetSchema::sheetName).toList());
        assertEquals(List.of(2, 2),
                normalized.sheets().stream().map(SheetSchema::headerRowIndex).toList());
    }
}

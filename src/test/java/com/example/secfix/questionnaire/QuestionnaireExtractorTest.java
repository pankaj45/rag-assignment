package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.SheetSchema;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionnaireExtractorTest {

    private final QuestionnaireExtractor extractor = new QuestionnaireExtractor();

    @Test
    void extractSkipsFollowUpResponseWhenFollowUpQuestionIsBlank() throws Exception {
        WorkbookSchema schema = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "Sheet1",
                        0,
                        0,
                        List.of(
                                new AnswerColumnSchema(1, "Response", AnswerColumnSchema.AnswerType.CONSTRAINED, List.of("Yes", "No")),
                                new AnswerColumnSchema(2, "Comment", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()),
                                new AnswerColumnSchema(5, "Follow-up Response", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                        )
                )
        ));

        List<Question> questions = extractor.extract(new ByteArrayInputStream(createWorkbookBytes()), schema);

        assertEquals(2, questions.size());
        assertEquals(List.of("Response", "Comment"),
                questions.get(0).answerColumns().stream().map(AnswerColumnSchema::columnName).toList());
        assertEquals(List.of("Response", "Comment", "Follow-up Response"),
                questions.get(1).answerColumns().stream().map(AnswerColumnSchema::columnName).toList());
        assertEquals(List.of("In place", "Partially in place", "Not in place", "Not applicable"),
                questions.get(0).answerColumns().get(0).allowedOptions());
        assertEquals(AnswerColumnSchema.AnswerType.CONSTRAINED,
                questions.get(0).answerColumns().get(0).answerType());
    }

    @Test
    void extractDetectsMaxLengthFromCustomValidation() throws Exception {
        WorkbookSchema schema = new WorkbookSchema(List.of(
                new SheetSchema(
                        0,
                        "Sheet1",
                        0,
                        0,
                        List.of(
                                new AnswerColumnSchema(1, "Response", AnswerColumnSchema.AnswerType.CONSTRAINED, List.of("Yes", "No")),
                                new AnswerColumnSchema(2, "Comment", AnswerColumnSchema.AnswerType.FREE_TEXT, List.of())
                        )
                )
        ));

        List<Question> questions = extractor.extract(
                new ByteArrayInputStream(createWorkbookWithTextLengthValidation()), schema);

        assertEquals(1, questions.size());
        AnswerColumnSchema commentCol = questions.get(0).answerColumns().stream()
                .filter(c -> c.columnName().equals("Comment")).findFirst().orElseThrow();
        assertEquals(Integer.valueOf(4000), commentCol.maxLength());
    }

    private byte[] createWorkbookWithTextLengthValidation() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Question");
            header.createCell(1).setCellValue("Response");
            header.createCell(2).setCellValue("Comment");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Do you encrypt data at rest?");
            row.createCell(23).setCellValue("Yes");
            row.createCell(24).setCellValue("No");

            DataValidationHelper helper = sheet.getDataValidationHelper();

            // Dropdown on Response column
            DataValidationConstraint listConstraint = helper.createFormulaListConstraint("X2:Y2");
            DataValidation listVal = helper.createValidation(listConstraint, new CellRangeAddressList(1, 1, 1, 1));
            sheet.addValidationData(listVal);

            // Custom text length constraint on Comment column: LTE(LEN(C2),(4000))
            DataValidationConstraint lenConstraint = helper.createCustomConstraint("LTE(LEN(C2),(4000))");
            DataValidation lenVal = helper.createValidation(lenConstraint, new CellRangeAddressList(1, 1, 2, 2));
            sheet.addValidationData(lenVal);

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
            header.createCell(1).setCellValue("Response");
            header.createCell(2).setCellValue("Comment");
            header.createCell(3).setCellValue("attachment(s)");
            header.createCell(4).setCellValue("Follow-up Question");
            header.createCell(5).setCellValue("Follow-up Response");

            var row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("Do you encrypt data at rest?");
            row1.createCell(4).setCellValue("");
            row1.createCell(23).setCellValue("In place");
            row1.createCell(24).setCellValue("Partially in place");
            row1.createCell(25).setCellValue("Not in place");
            row1.createCell(26).setCellValue("Not applicable");

            var row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("Do you review logs?");
            row2.createCell(4).setCellValue("If yes, how often are logs reviewed?");
            row2.createCell(23).setCellValue("In place");
            row2.createCell(24).setCellValue("Partially in place");
            row2.createCell(25).setCellValue("Not in place");
            row2.createCell(26).setCellValue("Not applicable");

            DataValidationHelper helper = sheet.getDataValidationHelper();
            DataValidationConstraint constraint = helper.createFormulaListConstraint("X2:AA2");
            CellRangeAddressList responseCells = new CellRangeAddressList(1, 2, 1, 1);
            DataValidation validation = helper.createValidation(constraint, responseCells);
            sheet.addValidationData(validation);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

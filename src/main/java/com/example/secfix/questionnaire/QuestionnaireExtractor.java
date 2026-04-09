package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.SheetSchema;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class QuestionnaireExtractor {

    private static final Pattern CUSTOM_LEN_LIMIT = Pattern.compile(
            "(?i)LTE\\s*\\(\\s*LEN\\s*\\([^)]+\\)\\s*,\\s*\\(?(\\d+)\\)?\\s*\\)"
    );
    private static final Pattern LEN_COMPARE_LIMIT = Pattern.compile(
            "(?i)LEN\\s*\\([^)]+\\)\\s*<=?\\s*(\\d+)"
    );

    public List<Question> extract(InputStream inputStream, WorkbookSchema schema) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            List<Question> questions = new ArrayList<>();

            for (SheetSchema sheetSchema : schema.sheets()) {
                Sheet sheet = workbook.getSheetAt(sheetSchema.sheetIndex());
                if (sheet == null) {
                    log.warn("Sheet at index {} not found, skipping", sheetSchema.sheetIndex());
                    continue;
                }

                List<String> headerValues = readHeaderValues(sheet, sheetSchema.headerRowIndex(), formatter);
                int dataStartRow = sheetSchema.headerRowIndex() + 1;
                for (int ri = dataStartRow; ri <= sheet.getLastRowNum(); ri++) {
                    Row row = sheet.getRow(ri);
                    if (row == null) continue;

                    Cell questionCell = row.getCell(
                            sheetSchema.questionColumnIndex(),
                            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                    );
                    if (questionCell == null) continue;

                    String questionText = formatter.formatCellValue(questionCell).strip();
                    if (questionText.isBlank()) continue;

                    List<AnswerColumnSchema> activeAnswerColumns = sheetSchema.answerColumns().stream()
                            .map(answerColumn -> enrichFromExcelValidation(workbook, sheet, row, answerColumn, formatter))
                            .filter(answerColumn -> isActiveForRow(answerColumn, row, headerValues, formatter))
                            .toList();

                    questions.add(new Question(
                            ri,
                            sheetSchema.sheetIndex(),
                            sheetSchema.sheetName(),
                            questionText,
                            activeAnswerColumns
                    ));
                }

                log.info(
                        "Extracted {} questions from sheet '{}'",
                        questions.stream().filter(q -> q.sheetIndex() == sheetSchema.sheetIndex()).count(),
                        sheetSchema.sheetName()
                );
            }

            return questions;
        } catch (Exception e) {
            throw new com.example.secfix.exception.DocumentParsingException(
                    "Failed to extract questions from questionnaire", e);
        }
    }

    private AnswerColumnSchema enrichFromExcelValidation(
            Workbook workbook,
            Sheet sheet,
            Row row,
            AnswerColumnSchema answerColumn,
            DataFormatter formatter
    ) {
        List<String> allowedOptions = resolveAllowedOptions(
                workbook,
                sheet,
                row.getRowNum(),
                answerColumn.columnIndex(),
                formatter
        );
        Integer maxLength = resolveMaxLength(sheet, row.getRowNum(), answerColumn.columnIndex());

        if (allowedOptions.isEmpty() && maxLength == null) {
            return answerColumn;
        }

        return new AnswerColumnSchema(
                answerColumn.columnIndex(),
                answerColumn.columnName(),
                allowedOptions.isEmpty() ? answerColumn.answerType() : AnswerColumnSchema.AnswerType.CONSTRAINED,
                allowedOptions.isEmpty() ? answerColumn.allowedOptions() : allowedOptions,
                maxLength != null ? maxLength : answerColumn.maxLength()
        );
    }

    private List<String> readHeaderValues(Sheet sheet, int headerRowIndex, DataFormatter formatter) {
        Row headerRow = sheet.getRow(headerRowIndex);
        if (headerRow == null) {
            return List.of();
        }

        List<String> headers = new ArrayList<>();
        int lastCol = headerRow.getLastCellNum();
        for (int ci = 0; ci < lastCol; ci++) {
            Cell cell = headerRow.getCell(ci, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            headers.add(cell != null ? formatter.formatCellValue(cell).strip() : "");
        }
        return headers;
    }

    private boolean isActiveForRow(
            AnswerColumnSchema answerColumn,
            Row row,
            List<String> headerValues,
            DataFormatter formatter
    ) {
        String normalizedName = normalize(answerColumn.columnName());
        if (normalizedName.contains("followupresponse") || normalizedName.contains("followupanswer")) {
            int followUpQuestionColumn = findHeaderColumn(headerValues, "followupquestion");
            if (followUpQuestionColumn >= 0) {
                Cell followUpQuestionCell = row.getCell(
                        followUpQuestionColumn,
                        Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
                );
                String followUpQuestion = followUpQuestionCell != null
                        ? formatter.formatCellValue(followUpQuestionCell).strip()
                        : "";
                return !followUpQuestion.isBlank();
            }
        }
        return true;
    }

    private List<String> resolveAllowedOptions(
            Workbook workbook,
            Sheet sheet,
            int rowIndex,
            int columnIndex,
            DataFormatter formatter
    ) {
        for (DataValidation validation : sheet.getDataValidations()) {
            if (!containsCell(validation, rowIndex, columnIndex)) {
                continue;
            }

            DataValidationConstraint constraint = validation.getValidationConstraint();
            if (constraint == null || constraint.getValidationType() != DataValidationConstraint.ValidationType.LIST) {
                continue;
            }

            List<String> explicit = sanitizeOptions(constraint.getExplicitListValues());
            if (!explicit.isEmpty()) {
                return explicit;
            }

            List<String> formulaOptions = resolveFormulaOptions(
                    workbook,
                    sheet,
                    validation,
                    rowIndex,
                    columnIndex,
                    constraint.getFormula1(),
                    formatter
            );
            if (!formulaOptions.isEmpty()) {
                return formulaOptions;
            }
        }
        return List.of();
    }

    private Integer resolveMaxLength(Sheet sheet, int rowIndex, int columnIndex) {
        for (DataValidation validation : sheet.getDataValidations()) {
            if (!containsCell(validation, rowIndex, columnIndex)) {
                continue;
            }

            DataValidationConstraint constraint = validation.getValidationConstraint();
            if (constraint == null) continue;

            int type = constraint.getValidationType();

            if (type == DataValidationConstraint.ValidationType.TEXT_LENGTH) {
                return parseIntOrNull(constraint.getFormula1());
            }

            if (type == DataValidationConstraint.ValidationType.FORMULA) {
                Integer limit = parseMaxLengthFromFormula(constraint.getFormula1());
                if (limit != null) return limit;
            }
        }
        return null;
    }

    private Integer parseMaxLengthFromFormula(String formula) {
        if (formula == null) return null;
        String stripped = formula.strip();
        if (stripped.startsWith("=")) stripped = stripped.substring(1);

        Matcher m = CUSTOM_LEN_LIMIT.matcher(stripped);
        if (m.find()) return parseIntOrNull(m.group(1));

        m = LEN_COMPARE_LIMIT.matcher(stripped);
        if (m.find()) return parseIntOrNull(m.group(1));

        return null;
    }

    private Integer parseIntOrNull(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value.strip());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean containsCell(DataValidation validation, int rowIndex, int columnIndex) {
        for (CellRangeAddress address : validation.getRegions().getCellRangeAddresses()) {
            if (address.isInRange(rowIndex, columnIndex)) {
                return true;
            }
        }
        return false;
    }

    private List<String> resolveFormulaOptions(
            Workbook workbook,
            Sheet currentSheet,
            DataValidation validation,
            int rowIndex,
            int columnIndex,
            String formula,
            DataFormatter formatter
    ) {
        if (formula == null || formula.isBlank()) {
            return List.of();
        }

        String normalizedFormula = formula.strip();
        if (normalizedFormula.startsWith("=")) {
            normalizedFormula = normalizedFormula.substring(1);
        }

        Name namedRange = workbook.getName(normalizedFormula);
        if (namedRange != null && namedRange.getRefersToFormula() != null) {
            normalizedFormula = namedRange.getRefersToFormula();
        }

        String sheetName = extractSheetName(normalizedFormula);
        String referenceText = stripSheetName(normalizedFormula);
        if (!referenceText.contains(":")) {
            return List.of();
        }

        try {
            AreaReference areaReference = new AreaReference(referenceText, SpreadsheetVersion.EXCEL2007);
            CellReference first = areaReference.getFirstCell();
            CellReference last = areaReference.getLastCell();
            CellRangeAddress targetRegion = containingRegion(validation, rowIndex, columnIndex);
            if (targetRegion == null) {
                return List.of();
            }

            int rowOffset = rowIndex - targetRegion.getFirstRow();
            int columnOffset = columnIndex - targetRegion.getFirstColumn();

            int firstRow = shift(first.getRow(), rowOffset, first.isRowAbsolute());
            int lastRow = shift(last.getRow(), rowOffset, last.isRowAbsolute());
            int firstCol = shift(first.getCol(), columnOffset, first.isColAbsolute());
            int lastCol = shift(last.getCol(), columnOffset, last.isColAbsolute());

            Sheet sourceSheet = sheetName != null ? workbook.getSheet(sheetName) : currentSheet;
            if (sourceSheet == null) {
                return List.of();
            }

            List<String> options = new ArrayList<>();
            for (int ri = Math.min(firstRow, lastRow); ri <= Math.max(firstRow, lastRow); ri++) {
                Row sourceRow = sourceSheet.getRow(ri);
                if (sourceRow == null) {
                    continue;
                }
                for (int ci = Math.min(firstCol, lastCol); ci <= Math.max(firstCol, lastCol); ci++) {
                    Cell sourceCell = sourceRow.getCell(ci, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (sourceCell == null) {
                        continue;
                    }
                    String value = formatter.formatCellValue(sourceCell).strip();
                    if (!value.isBlank() && !options.contains(value)) {
                        options.add(value);
                    }
                }
            }
            return options;
        } catch (IllegalArgumentException ignored) {
            return List.of();
        }
    }

    private CellRangeAddress containingRegion(DataValidation validation, int rowIndex, int columnIndex) {
        for (CellRangeAddress address : validation.getRegions().getCellRangeAddresses()) {
            if (address.isInRange(rowIndex, columnIndex)) {
                return address;
            }
        }
        return null;
    }

    private int shift(int value, int offset, boolean absolute) {
        return absolute ? value : Math.max(0, value + offset);
    }

    private String extractSheetName(String formula) {
        int bangIndex = formula.lastIndexOf('!');
        if (bangIndex < 0) {
            return null;
        }
        String rawSheetName = formula.substring(0, bangIndex);
        if (rawSheetName.startsWith("'") && rawSheetName.endsWith("'") && rawSheetName.length() >= 2) {
            rawSheetName = rawSheetName.substring(1, rawSheetName.length() - 1).replace("''", "'");
        }
        return rawSheetName;
    }

    private String stripSheetName(String formula) {
        int bangIndex = formula.lastIndexOf('!');
        return bangIndex >= 0 ? formula.substring(bangIndex + 1) : formula;
    }

    private List<String> sanitizeOptions(String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }

        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String stripped = value.strip();
            if (!stripped.isBlank() && !sanitized.contains(stripped)) {
                sanitized.add(stripped);
            }
        }
        return sanitized;
    }

    private int findHeaderColumn(List<String> headers, String normalizedTarget) {
        for (int i = 0; i < headers.size(); i++) {
            if (normalize(headers.get(i)).equals(normalizedTarget)) {
                return i;
            }
        }
        return -1;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replace(" ", "");
    }
}

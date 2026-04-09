package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.AnswerColumnSchema;
import com.example.secfix.questionnaire.schema.SheetSchema;
import com.example.secfix.questionnaire.schema.SheetSnapshot;
import com.example.secfix.questionnaire.schema.WorkbookSchema;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class WorkbookSchemaNormalizer {

    public WorkbookSchema normalize(WorkbookSchema inferred, WorkbookSnapshot snapshot) {
        if (inferred == null || inferred.sheets() == null || inferred.sheets().isEmpty()) {
            return new WorkbookSchema(List.of());
        }

        List<SheetSnapshot> questionnaireSnapshots = questionnaireSheetSnapshots(snapshot);
        Map<Integer, SheetSnapshot> snapshotsByIndex = new HashMap<>();
        if (snapshot != null && snapshot.sheets() != null) {
            for (SheetSnapshot sheetSnapshot : snapshot.sheets()) {
                snapshotsByIndex.put(sheetSnapshot.sheetIndex(), sheetSnapshot);
            }
        }

        List<SheetSchema> normalized = new ArrayList<>();
        int inferredOrder = 0;
        for (SheetSchema originalSheet : inferred.sheets()) {
            SheetSchema sheet = repairSheetIdentity(originalSheet, snapshotsByIndex, questionnaireSnapshots, inferredOrder++);
            SheetSnapshot sheetSnapshot = snapshotsByIndex.get(sheet.sheetIndex());
            if (sheetSnapshot == null || sheetSnapshot.rows() == null || sheetSnapshot.rows().isEmpty()) {
                normalized.add(sheet);
                continue;
            }
            normalized.add(normalizeSheet(sheet, sheetSnapshot));
        }

        if (normalized.isEmpty() && !questionnaireSnapshots.isEmpty()) {
            for (SheetSnapshot questionnaireSnapshot : questionnaireSnapshots) {
                normalized.add(normalizeSheet(
                        new SheetSchema(questionnaireSnapshot.sheetIndex(), questionnaireSnapshot.sheetName(), 0, 0, List.of()),
                        questionnaireSnapshot
                ));
            }
        }
        return new WorkbookSchema(normalized);
    }

    private SheetSchema repairSheetIdentity(
            SheetSchema sheet,
            Map<Integer, SheetSnapshot> snapshotsByIndex,
            List<SheetSnapshot> questionnaireSnapshots,
            int inferredOrder
    ) {
        if (sheet == null) {
            return fallbackSheet(questionnaireSnapshots, inferredOrder);
        }

        SheetSnapshot byName = findByName(questionnaireSnapshots, sheet.sheetName());
        if (byName != null) {
            return copySheetIdentity(sheet, byName);
        }

        SheetSnapshot byIndex = snapshotsByIndex.get(sheet.sheetIndex());
        if (looksLikeQuestionnaireSheet(byIndex)) {
            return copySheetIdentity(sheet, byIndex);
        }

        SheetSnapshot byOrder = questionnaireSnapshots.isEmpty()
                ? null
                : questionnaireSnapshots.get(Math.min(inferredOrder, questionnaireSnapshots.size() - 1));
        if (byOrder != null) {
            return copySheetIdentity(sheet, byOrder);
        }

        return sheet;
    }

    private SheetSchema fallbackSheet(List<SheetSnapshot> questionnaireSnapshots, int inferredOrder) {
        if (questionnaireSnapshots.isEmpty()) {
            return new SheetSchema(0, null, 0, 0, List.of());
        }
        SheetSnapshot snapshot = questionnaireSnapshots.get(Math.min(inferredOrder, questionnaireSnapshots.size() - 1));
        return new SheetSchema(snapshot.sheetIndex(), snapshot.sheetName(), 0, 0, List.of());
    }

    private SheetSchema copySheetIdentity(SheetSchema source, SheetSnapshot snapshot) {
        return new SheetSchema(
                snapshot.sheetIndex(),
                snapshot.sheetName(),
                source.headerRowIndex(),
                source.questionColumnIndex(),
                source.answerColumns() == null ? List.of() : source.answerColumns()
        );
    }

    private SheetSnapshot findByName(List<SheetSnapshot> snapshots, String sheetName) {
        String normalizedTarget = normalize(sheetName);
        if (normalizedTarget.isBlank()) {
            return null;
        }
        for (SheetSnapshot snapshot : snapshots) {
            if (normalize(snapshot.sheetName()).equals(normalizedTarget)) {
                return snapshot;
            }
        }
        return null;
    }

    private List<SheetSnapshot> questionnaireSheetSnapshots(WorkbookSnapshot snapshot) {
        if (snapshot == null || snapshot.sheets() == null) {
            return List.of();
        }
        return snapshot.sheets().stream()
                .filter(this::looksLikeQuestionnaireSheet)
                .toList();
    }

    private boolean looksLikeQuestionnaireSheet(SheetSnapshot snapshot) {
        if (snapshot == null || snapshot.rows() == null) {
            return false;
        }
        for (List<String> row : snapshot.rows()) {
            String combined = normalize(String.join(" ", row));
            if (combined.contains("question") && combined.contains("response")) {
                return true;
            }
        }
        return false;
    }

    private SheetSchema normalizeSheet(SheetSchema sheet, SheetSnapshot snapshot) {
        int headerRow = chooseHeaderRow(sheet, snapshot.rows());
        List<String> header = row(snapshot.rows(), headerRow);

        int questionColumn = resolveQuestionColumn(sheet.questionColumnIndex(), header);
        List<AnswerColumnSchema> normalizedAnswerColumns = normalizeAnswerColumns(
                sheet.answerColumns(),
                header,
                questionColumn
        );

        if (sheet.headerRowIndex() != headerRow || sheet.questionColumnIndex() != questionColumn
                || !Objects.equals(sheet.answerColumns(), normalizedAnswerColumns)) {
            log.info(
                    "Normalized schema for sheet '{}': header row {} -> {}, question col {} -> {}",
                    sheet.sheetName(),
                    sheet.headerRowIndex(),
                    headerRow,
                    sheet.questionColumnIndex(),
                    questionColumn
            );
        }

        return new SheetSchema(
                sheet.sheetIndex(),
                sheet.sheetName(),
                headerRow,
                questionColumn,
                normalizedAnswerColumns
        );
    }

    private int chooseHeaderRow(SheetSchema sheet, List<List<String>> rows) {
        int declared = clamp(sheet.headerRowIndex(), rows.size());

        LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
        candidates.add(declared);
        candidates.add(clamp(declared - 1, rows.size()));
        candidates.add(clamp(declared + 1, rows.size()));
        int scanLimit = Math.min(15, rows.size());
        for (int i = 0; i < scanLimit; i++) {
            candidates.add(i);
        }

        int bestRow = declared;
        int bestScore = Integer.MIN_VALUE;
        for (int candidate : candidates) {
            int score = scoreHeaderRow(candidate, rows, sheet);
            if (score > bestScore || (score == bestScore && Math.abs(candidate - declared) < Math.abs(bestRow - declared))) {
                bestScore = score;
                bestRow = candidate;
            }
        }
        return bestRow;
    }

    private int scoreHeaderRow(int rowIndex, List<List<String>> rows, SheetSchema sheet) {
        List<String> row = row(rows, rowIndex);
        if (row.isEmpty()) return Integer.MIN_VALUE / 2;

        int score = 0;
        for (String s : row) {
            String cell = normalize(s);
            if (cell.isBlank()) continue;
            if (cell.contains("question")) score += 6;
            if (looksLikeAnswerHeader(cell)) score += 2;
        }

        if (sheet.answerColumns() != null) {
            for (AnswerColumnSchema answerColumn : sheet.answerColumns()) {
                String name = normalize(answerColumn.columnName());
                if (name.isBlank()) continue;
                int exact = indexOfExact(row, name);
                if (exact >= 0) score += 4;
                else if (indexOfContains(row, name) >= 0) score += 2;
            }
        }
        return score;
    }

    private int resolveQuestionColumn(int declared, List<String> header) {
        if (header.isEmpty()) return Math.max(0, declared);

        int questionByName = indexByKeyword(header, "question");
        if (questionByName >= 0) return questionByName;

        int declaredClamped = clamp(declared, header.size());
        if (!normalize(header.get(declaredClamped)).isBlank()) {
            return declaredClamped;
        }
        return 0;
    }

    private List<AnswerColumnSchema> normalizeAnswerColumns(
            List<AnswerColumnSchema> answerColumns,
            List<String> header,
            int questionColumn
    ) {
        if (answerColumns == null || answerColumns.isEmpty()) {
            return deriveAnswerColumnsFromHeader(header, questionColumn);
        }

        List<AnswerColumnSchema> normalized = new ArrayList<>();
        Set<Integer> usedIndexes = new HashSet<>();

        for (AnswerColumnSchema answerColumn : answerColumns) {
            if (!isSupportedAnswerColumn(answerColumn)) {
                continue;
            }
            int resolvedIndex = resolveAnswerColumnIndex(answerColumn, header, questionColumn, usedIndexes);
            usedIndexes.add(resolvedIndex);
            normalized.add(new AnswerColumnSchema(
                    resolvedIndex,
                    answerColumn.columnName(),
                    answerColumn.answerType(),
                    answerColumn.allowedOptions() == null ? List.of() : answerColumn.allowedOptions(),
                    answerColumn.maxLength()
            ));
        }
        return normalized.isEmpty() ? deriveAnswerColumnsFromHeader(header, questionColumn) : normalized;
    }

    private int resolveAnswerColumnIndex(
            AnswerColumnSchema answerColumn,
            List<String> header,
            int questionColumn,
            Set<Integer> usedIndexes
    ) {
        if (header.isEmpty()) {
            return Math.max(0, answerColumn.columnIndex());
        }

        String target = normalize(answerColumn.columnName());
        if (!target.isBlank()) {
            int exact = indexOfExact(header, target);
            if (exact >= 0 && exact != questionColumn && !usedIndexes.contains(exact)) return exact;

            int contains = indexOfContains(header, target);
            if (contains >= 0 && contains != questionColumn && !usedIndexes.contains(contains)) return contains;
        }

        int declared = clamp(answerColumn.columnIndex(), header.size());
        if (declared != questionColumn && !usedIndexes.contains(declared)) return declared;

        int shiftedLeft = clamp(answerColumn.columnIndex() - 1, header.size());
        if (shiftedLeft != questionColumn && !usedIndexes.contains(shiftedLeft)) return shiftedLeft;

        int shiftedRight = clamp(answerColumn.columnIndex() + 1, header.size());
        if (shiftedRight != questionColumn && !usedIndexes.contains(shiftedRight)) return shiftedRight;

        for (int i = 0; i < header.size(); i++) {
            if (i == questionColumn || usedIndexes.contains(i)) continue;
            String cell = normalize(header.get(i));
            if (looksLikeAnswerHeader(cell)) return i;
        }

        return declared;
    }

    private List<AnswerColumnSchema> deriveAnswerColumnsFromHeader(List<String> header, int questionColumn) {
        List<AnswerColumnSchema> derived = new ArrayList<>();
        for (int i = 0; i < header.size(); i++) {
            if (i == questionColumn) continue;
            String value = normalize(header.get(i));
            if (!looksLikeAnswerHeader(value)) continue;
            derived.add(new AnswerColumnSchema(i, header.get(i), AnswerColumnSchema.AnswerType.FREE_TEXT, List.of()));
        }
        return derived;
    }

    private boolean looksLikeAnswerHeader(String normalizedHeader) {
        return !isExcludedAnswerHeader(normalizedHeader) && (
                normalizedHeader.contains("response")
                || normalizedHeader.contains("comment")
                || normalizedHeader.contains("answer")
                || normalizedHeader.contains("remark")
                || normalizedHeader.contains("justification")
                || normalizedHeader.contains("detail"));
    }

    private boolean isSupportedAnswerColumn(AnswerColumnSchema answerColumn) {
        return looksLikeAnswerHeader(normalize(answerColumn.columnName()));
    }

    private boolean isExcludedAnswerHeader(String normalizedHeader) {
        return normalizedHeader.contains("question")
                || normalizedHeader.contains("attachment")
                || normalizedHeader.contains("evidence")
                || normalizedHeader.contains("reference")
                || normalizedHeader.contains("document")
                || normalizedHeader.contains("artifact");
    }

    private int indexByKeyword(List<String> row, String keyword) {
        for (int i = 0; i < row.size(); i++) {
            if (normalize(row.get(i)).contains(keyword)) return i;
        }
        return -1;
    }

    private int indexOfExact(List<String> row, String normalizedTarget) {
        for (int i = 0; i < row.size(); i++) {
            if (normalize(row.get(i)).equals(normalizedTarget)) return i;
        }
        return -1;
    }

    private int indexOfContains(List<String> row, String normalizedTarget) {
        for (int i = 0; i < row.size(); i++) {
            String normalizedCell = normalize(row.get(i));
            if (normalizedCell.contains(normalizedTarget) || normalizedTarget.contains(normalizedCell)) {
                return i;
            }
        }
        return -1;
    }

    private List<String> row(List<List<String>> rows, int index) {
        if (rows == null || rows.isEmpty()) return List.of();
        int safeIndex = clamp(index, rows.size());
        List<String> row = rows.get(safeIndex);
        return row == null ? List.of() : row;
    }

    private int clamp(int value, int size) {
        if (size <= 0) return 0;
        if (value < 0) return 0;
        if (value >= size) return size - 1;
        return value;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replace(" ", "");
    }
}

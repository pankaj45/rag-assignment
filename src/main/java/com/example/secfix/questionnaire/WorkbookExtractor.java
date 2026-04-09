package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.SheetSnapshot;
import com.example.secfix.questionnaire.schema.WorkbookSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class WorkbookExtractor {

    private final int snapshotRows;

    public WorkbookExtractor(@Value("${rag.questionnaire.snapshot-rows:20}") int snapshotRows) {
        this.snapshotRows = snapshotRows;
    }

    public WorkbookSnapshot extract(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            DataFormatter formatter = new DataFormatter();
            List<SheetSnapshot> sheetSnapshots = new ArrayList<>();

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                List<List<String>> rows = new ArrayList<>();
                int rowCount = 0;

                for (Row row : sheet) {
                    if (rowCount >= snapshotRows) break;
                    List<String> cells = new ArrayList<>();
                    int lastCol = row.getLastCellNum();
                    for (int ci = 0; ci < lastCol; ci++) {
                        Cell cell = row.getCell(ci, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells.add(cell != null ? formatter.formatCellValue(cell).strip() : "");
                    }
                    // Trim trailing empty cells
                    while (!cells.isEmpty() && cells.getLast().isEmpty()) {
                        cells.removeLast();
                    }
                    rows.add(cells);
                    rowCount++;
                }

                sheetSnapshots.add(new SheetSnapshot(si, sheet.getSheetName(), rows));
                log.debug("Extracted snapshot for sheet '{}': {} rows", sheet.getSheetName(), rows.size());
            }

            return new WorkbookSnapshot(sheetSnapshots);
        } catch (Exception e) {
            throw new com.example.secfix.exception.DocumentParsingException(
                    "Failed to extract workbook snapshot", e);
        }
    }
}

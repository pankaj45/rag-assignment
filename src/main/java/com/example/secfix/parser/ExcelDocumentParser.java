package com.example.secfix.parser;

import com.example.secfix.exception.DocumentParsingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class ExcelDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String fileName) {
        return XlsxFileSupport.isXlsx(contentType, fileName);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        String title = stripExtension(fileName);
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            List<PageContent> pages = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                StringBuilder sb = new StringBuilder();
                sb.append("Sheet: ").append(sheet.getSheetName()).append("\n");

                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String val = formatter.formatCellValue(cell).strip();
                        if (!val.isBlank()) {
                            sb.append(val).append(" ");
                        }
                    }
                    sb.append("\n");
                }

                String text = sb.toString().strip();
                if (!text.isBlank()) {
                    pages.add(new PageContent(i + 1, text));
                }
            }

            log.info("Parsed Excel '{}': {} sheets", title, pages.size());
            return new ParsedDocument(title, pages);
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse Excel: " + fileName, e);
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

package com.example.secfix.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExcelDocumentParserTest {

    private final ExcelDocumentParser parser = new ExcelDocumentParser();

    @Test
    void supportsXlsxAndRejectsXls() {
        assertTrue(parser.supports(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "questionnaire.xlsx"
        ));
        assertTrue(parser.supports(null, "questionnaire.xlsx"));

        assertFalse(parser.supports("application/vnd.ms-excel", "legacy.xls"));
        assertFalse(parser.supports(null, "legacy.xls"));
    }
}


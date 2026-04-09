package com.example.secfix.questionnaire.schema;

import java.util.List;

public record SheetSnapshot(int sheetIndex, String sheetName, List<List<String>> rows) {}

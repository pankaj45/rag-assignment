package com.example.secfix.questionnaire.schema;

import java.util.List;

public record SheetSchema(
        int sheetIndex,
        String sheetName,
        int headerRowIndex,
        int questionColumnIndex,
        List<AnswerColumnSchema> answerColumns
) {}

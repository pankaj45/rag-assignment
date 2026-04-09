package com.example.secfix.questionnaire;

import com.example.secfix.questionnaire.schema.AnswerColumnSchema;

import java.util.List;

public record Question(
        int rowIndex,
        int sheetIndex,
        String sheetName,
        String questionText,
        List<AnswerColumnSchema> answerColumns
) {}

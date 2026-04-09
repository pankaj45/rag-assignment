package com.example.secfix.questionnaire.schema;

import java.util.List;

public record AnswerColumnSchema(
        int columnIndex,
        String columnName,
        AnswerType answerType,
        List<String> allowedOptions,
        Integer maxLength
) {
    public AnswerColumnSchema(int columnIndex, String columnName, AnswerType answerType, List<String> allowedOptions) {
        this(columnIndex, columnName, answerType, allowedOptions, null);
    }

    public enum AnswerType {
        CONSTRAINED, FREE_TEXT
    }

    public boolean isConstrained() {
        return answerType == AnswerType.CONSTRAINED && allowedOptions != null && !allowedOptions.isEmpty();
    }

    public boolean hasMaxLength() {
        return maxLength != null && maxLength > 0;
    }
}

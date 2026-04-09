package com.example.secfix.questionnaire;

public record QuestionnaireFillResult(
        byte[] workbookBytes,
        String runId
) {
}


package com.example.secfix.web.dto;

import com.example.secfix.domain.RunStatus;

import java.time.LocalDateTime;

public record QuestionnaireRunResponse(
        String runId,
        String customerId,
        RunStatus status,
        String inputFileName,
        String outputFileName,
        int totalFields,
        int answeredFields,
        int insufficientContextFields,
        int failedFields,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}

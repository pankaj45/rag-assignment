package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "questionnaire_runs",
        indexes = {
                @Index(name = "idx_questionnaire_runs_status", columnList = "status"),
                @Index(name = "idx_questionnaire_runs_created_at", columnList = "created_at"),
                @Index(name = "idx_questionnaire_runs_customer_id", columnList = "customer_id"),
                @Index(name = "idx_questionnaire_runs_customer_created", columnList = "customer_id,created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_questionnaire_runs_run_id", columnNames = "run_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, updatable = false, length = 36)
    private String runId;

    @Column(name = "input_file_name", nullable = false)
    private String inputFileName;

    @Column(name = "output_file_name")
    private String outputFileName;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "total_fields", nullable = false)
    private int totalFields;

    @Column(name = "answered_fields", nullable = false)
    private int answeredFields;

    @Column(name = "insufficient_context_fields", nullable = false)
    private int insufficientContextFields;

    @Column(name = "failed_fields", nullable = false)
    private int failedFields;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (runId == null) runId = UUID.randomUUID().toString();
        if (status == null) status = RunStatus.PENDING;
        if (startedAt == null) startedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void markInProgress() {
        status = RunStatus.IN_PROGRESS;
        if (startedAt == null) startedAt = LocalDateTime.now();
    }

    public void markCompleted() {
        status = RunStatus.COMPLETED;
        completedAt = LocalDateTime.now();
    }

    public void markFailed(String message) {
        status = RunStatus.FAILED;
        errorMessage = message;
        completedAt = LocalDateTime.now();
    }
}

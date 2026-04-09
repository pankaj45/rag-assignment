package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "generated_answers",
        indexes = {
                @Index(name = "idx_generated_answers_run_id", columnList = "run_id"),
                @Index(name = "idx_generated_answers_status", columnList = "answer_status"),
                @Index(name = "idx_generated_answers_created_at", columnList = "created_at"),
                @Index(name = "idx_generated_answers_customer_id", columnList = "customer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_generated_answers_field_attempt",
                        columnNames = {"field_id", "attempt_no"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class GeneratedAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private QuestionnaireRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private QuestionnaireItem item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "field_id", nullable = false)
    private QuestionnaireField field;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false, length = 32)
    private AnswerStatus answerStatus;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "prompt_version")
    private String promptVersion;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (attemptNo == 0) attemptNo = 1;
        createdAt = LocalDateTime.now();
    }
}

package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "questionnaire_items",
        indexes = {
                @Index(name = "idx_questionnaire_items_run_id", columnList = "run_id"),
                @Index(name = "idx_questionnaire_items_sheet_row", columnList = "sheet_index,row_index"),
                @Index(name = "idx_questionnaire_items_customer_id", columnList = "customer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_questionnaire_items_run_sheet_row",
                        columnNames = {"run_id", "sheet_index", "row_index"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private QuestionnaireRun run;

    @Column(name = "sheet_index", nullable = false)
    private int sheetIndex;

    @Column(name = "sheet_name", nullable = false)
    private String sheetName;

    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

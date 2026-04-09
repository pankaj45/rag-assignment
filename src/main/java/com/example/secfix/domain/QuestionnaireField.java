package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "questionnaire_fields",
        indexes = {
                @Index(name = "idx_questionnaire_fields_item_id", columnList = "item_id"),
                @Index(name = "idx_questionnaire_fields_column_index", columnList = "column_index"),
                @Index(name = "idx_questionnaire_fields_customer_id", columnList = "customer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_questionnaire_fields_item_column",
                        columnNames = {"item_id", "column_index"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class QuestionnaireField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private QuestionnaireItem item;

    @Column(name = "column_index", nullable = false)
    private int columnIndex;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "cell_reference", nullable = false)
    private String cellReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 32)
    private QuestionFieldType fieldType;

    @Column(name = "is_constrained", nullable = false)
    private boolean constrained;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "allowed_options_json", columnDefinition = "TEXT", nullable = false)
    private List<String> allowedOptions;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "answer_evidence",
        indexes = {
                @Index(name = "idx_answer_evidence_answer_id", columnList = "answer_id"),
                @Index(name = "idx_answer_evidence_chunk_id", columnList = "knowledge_chunk_id"),
                @Index(name = "idx_answer_evidence_customer_id", columnList = "customer_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_answer_evidence_answer_rank",
                        columnNames = {"answer_id", "rank_position"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
public class AnswerEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "answer_id", nullable = false)
    private GeneratedAnswer answer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_chunk_id")
    private KnowledgeChunk knowledgeChunk;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "similarity_score")
    private Double similarityScore;

    @Column(name = "source_document_title")
    private String sourceDocumentTitle;

    @Column(name = "source_page_num")
    private Integer sourcePageNum;

    @Column(name = "chunk_snippet", columnDefinition = "TEXT")
    private String chunkSnippet;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

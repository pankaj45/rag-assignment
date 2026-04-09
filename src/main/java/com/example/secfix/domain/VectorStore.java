package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "vector_store",
        indexes = {
                @Index(name = "idx_vector_store_customer_id", columnList = "customer_id"),
                @Index(name = "idx_vector_store_doc_customer", columnList = "document_id,customer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VectorStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chunk_id", nullable = false)
    private KnowledgeChunk chunk;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}

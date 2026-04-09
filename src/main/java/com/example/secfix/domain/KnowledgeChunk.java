package com.example.secfix.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "knowledge_chunks",
        indexes = {
                @Index(name = "idx_knowledge_chunks_customer_id", columnList = "customer_id"),
                @Index(name = "idx_knowledge_chunks_doc_customer", columnList = "document_id,customer_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_text", columnDefinition = "TEXT", nullable = false)
    private String chunkText;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "embedding_json", columnDefinition = "TEXT", nullable = false)
    @Convert(converter = FloatArrayJsonConverter.class)
    private float[] embedding;

    @Column(name = "page_num")
    private Integer pageNum;
}

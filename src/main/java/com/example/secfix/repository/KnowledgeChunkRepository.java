package com.example.secfix.repository;

import com.example.secfix.domain.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, Long> {

    List<KnowledgeChunk> findByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    List<KnowledgeChunk> findByCustomerId(String customerId);

    List<KnowledgeChunk> findByDocumentIdAndCustomerId(Long documentId, String customerId);

    void deleteByDocumentIdAndCustomerId(Long documentId, String customerId);
}

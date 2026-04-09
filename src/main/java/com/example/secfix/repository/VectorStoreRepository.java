package com.example.secfix.repository;

import com.example.secfix.domain.VectorStore;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VectorStoreRepository extends JpaRepository<VectorStore, Long> {

    void deleteByDocumentId(Long documentId);

    void deleteByDocumentIdAndCustomerId(Long documentId, String customerId);
}

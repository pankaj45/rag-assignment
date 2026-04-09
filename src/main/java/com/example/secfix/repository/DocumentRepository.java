package com.example.secfix.repository;

import com.example.secfix.domain.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByCustomerIdOrderByCreatedAtDesc(String customerId);

    Optional<Document> findByIdAndCustomerId(Long id, String customerId);

    boolean existsByIdAndCustomerId(Long id, String customerId);

    void deleteByIdAndCustomerId(Long id, String customerId);
}

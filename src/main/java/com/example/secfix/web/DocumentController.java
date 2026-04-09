package com.example.secfix.web;

import com.example.secfix.domain.Document;
import com.example.secfix.repository.DocumentRepository;
import com.example.secfix.repository.KnowledgeChunkRepository;
import com.example.secfix.repository.VectorStoreRepository;
import com.example.secfix.web.dto.DocumentSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreRepository vectorStoreRepository;

    @GetMapping
    public List<DocumentSummaryResponse> listAll(@RequestParam("customer_id") String customerId) {
        return documentRepository.findAllByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentSummaryResponse> getById(
            @PathVariable Long id,
            @RequestParam("customer_id") String customerId
    ) {
        return documentRepository.findByIdAndCustomerId(id, customerId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam("customer_id") String customerId
    ) {
        if (!documentRepository.existsByIdAndCustomerId(id, customerId)) {
            return ResponseEntity.notFound().build();
        }
        vectorStoreRepository.deleteByDocumentIdAndCustomerId(id, customerId);
        chunkRepository.deleteByDocumentIdAndCustomerId(id, customerId);
        documentRepository.deleteByIdAndCustomerId(id, customerId);
        return ResponseEntity.noContent().build();
    }

    private DocumentSummaryResponse toResponse(Document doc) {
        return new DocumentSummaryResponse(doc.getId(), doc.getTitle(),
                doc.getOriginalFileName(), doc.getCreatedAt());
    }
}

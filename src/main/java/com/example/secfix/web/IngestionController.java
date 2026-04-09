package com.example.secfix.web;

import com.example.secfix.ingestion.IngestionResult;
import com.example.secfix.ingestion.IngestionService;
import com.example.secfix.web.dto.IngestionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<IngestionResponse> ingest(
            @RequestParam("file") MultipartFile file,
            @RequestParam("customer_id") String customerId
    ) {
        IngestionResult result = ingestionService.ingest(file, customerId);
        return ResponseEntity.ok(new IngestionResponse(
                result.documentId(),
                result.chunkCount(),
                "Document ingested successfully"
        ));
    }
}

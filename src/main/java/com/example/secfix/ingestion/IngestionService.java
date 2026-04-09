package com.example.secfix.ingestion;

import org.springframework.web.multipart.MultipartFile;

public interface IngestionService {

    IngestionResult ingest(MultipartFile file, String customerId);
}

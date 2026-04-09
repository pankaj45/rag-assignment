package com.example.secfix.ingestion;

public record IngestionCompletedEvent(Long documentId, String customerId, int chunkCount) {}

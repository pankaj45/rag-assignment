package com.example.secfix.web.dto;

public record IngestionResponse(Long documentId, int chunkCount, String message) {}

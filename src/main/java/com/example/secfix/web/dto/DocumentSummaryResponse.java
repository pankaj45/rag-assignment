package com.example.secfix.web.dto;

import java.time.LocalDateTime;

public record DocumentSummaryResponse(Long id, String title, String originalFileName, LocalDateTime createdAt) {}

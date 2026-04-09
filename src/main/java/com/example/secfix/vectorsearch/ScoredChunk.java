package com.example.secfix.vectorsearch;

import com.example.secfix.domain.KnowledgeChunk;

public record ScoredChunk(KnowledgeChunk chunk, double score) {}

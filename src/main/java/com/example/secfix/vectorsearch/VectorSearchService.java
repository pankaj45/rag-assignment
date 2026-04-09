package com.example.secfix.vectorsearch;

import java.util.List;

public interface VectorSearchService {

    List<ScoredChunk> search(float[] queryEmbedding, int topK, String customerId);
}

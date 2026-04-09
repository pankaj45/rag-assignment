package com.example.secfix.embedding;

import java.util.List;

public interface EmbeddingService {

    float[] embed(String text);

    List<float[]> embedBatch(List<String> texts);
}

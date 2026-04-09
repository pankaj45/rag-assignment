package com.example.secfix.chunker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SlidingWindowChunker implements TextChunker {

    private final int chunkSizeTokens;
    private final int overlapTokens;

    public SlidingWindowChunker(
            @Value("${rag.chunker.chunk-size-tokens:500}") int chunkSizeTokens,
            @Value("${rag.chunker.overlap-tokens:50}") int overlapTokens) {
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<Chunk> chunk(String text, int pageNum) {
        if (text == null || text.isBlank()) return List.of();

        String[] words = text.split("\\s+");
        if (words.length == 0) return List.of();

        List<Chunk> chunks = new ArrayList<>();
        int step = Math.max(1, chunkSizeTokens - overlapTokens);
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSizeTokens, words.length);
            String chunkText = String.join(" ", java.util.Arrays.copyOfRange(words, start, end));
            chunks.add(new Chunk(pageNum, chunkText));
            if (end == words.length) break;
            start += step;
        }

        return chunks;
    }
}

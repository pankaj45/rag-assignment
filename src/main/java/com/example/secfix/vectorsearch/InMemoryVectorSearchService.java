package com.example.secfix.vectorsearch;

import com.example.secfix.domain.KnowledgeChunk;
import com.example.secfix.ingestion.IngestionCompletedEvent;
import com.example.secfix.repository.KnowledgeChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class InMemoryVectorSearchService implements VectorSearchService {

    private final KnowledgeChunkRepository chunkRepository;

    @Value("${rag.retrieval.min-score:0.75}")
    private double minScore;

    private final AtomicReference<Map<String, List<KnowledgeChunk>>> cache = new AtomicReference<>(null);

    @EventListener
    public void onIngestionCompleted(IngestionCompletedEvent event) {
        log.info("Ingestion completed for document {}, customer {}, invalidating vector cache",
                event.documentId(), event.customerId());
        Map<String, List<KnowledgeChunk>> current = cache.get();
        if (current == null) return;
        current.remove(event.customerId());
    }

    @Override
    public List<ScoredChunk> search(float[] queryEmbedding, int topK, String customerId) {
        List<KnowledgeChunk> chunks = getOrLoadCache(customerId);
        if (chunks.isEmpty()) return List.of();

        return chunks.stream()
                .map(chunk -> new ScoredChunk(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                .filter(sc -> sc.score() >= minScore)
                .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                .limit(topK)
                .toList();
    }

    private List<KnowledgeChunk> getOrLoadCache(String customerId) {
        Map<String, List<KnowledgeChunk>> current = cache.get();
        if (current == null) {
            current = new ConcurrentHashMap<>();
            cache.set(current);
        }

        if (!current.containsKey(customerId)) {
            List<KnowledgeChunk> chunks = chunkRepository.findByCustomerId(customerId);
            current.put(customerId, chunks);
            log.info("Loaded {} chunks into vector search cache for customer {}", chunks.size(), customerId);
        }

        return current.getOrDefault(customerId, List.of());
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}

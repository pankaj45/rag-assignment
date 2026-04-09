package com.example.secfix.vectorsearch;

import com.example.secfix.domain.KnowledgeChunk;
import com.example.secfix.ingestion.IngestionCompletedEvent;
import com.example.secfix.repository.KnowledgeChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InMemoryVectorSearchServiceTest {

    @Mock
    private KnowledgeChunkRepository chunkRepository;

    @Test
    void searchUsesTenantScopedCacheAndInvalidatesPerCustomer() {
        InMemoryVectorSearchService service = new InMemoryVectorSearchService(chunkRepository);
        ReflectionTestUtils.setField(service, "minScore", 0.0);

        KnowledgeChunk chunkA = new KnowledgeChunk();
        chunkA.setCustomerId("cust-a");
        chunkA.setEmbedding(new float[]{1.0f, 0.0f});

        KnowledgeChunk chunkB = new KnowledgeChunk();
        chunkB.setCustomerId("cust-b");
        chunkB.setEmbedding(new float[]{0.0f, 1.0f});

        when(chunkRepository.findByCustomerId("cust-a")).thenReturn(List.of(chunkA));
        when(chunkRepository.findByCustomerId("cust-b")).thenReturn(List.of(chunkB));

        List<ScoredChunk> firstA = service.search(new float[]{1.0f, 0.0f}, 5, "cust-a");
        List<ScoredChunk> secondA = service.search(new float[]{1.0f, 0.0f}, 5, "cust-a");
        List<ScoredChunk> firstB = service.search(new float[]{1.0f, 0.0f}, 5, "cust-b");

        assertEquals(1, firstA.size());
        assertEquals(1, secondA.size());
        assertEquals(chunkA, firstA.getFirst().chunk());
        assertEquals(chunkB, firstB.getFirst().chunk());

        verify(chunkRepository, times(1)).findByCustomerId("cust-a");
        verify(chunkRepository, times(1)).findByCustomerId("cust-b");

        service.onIngestionCompleted(new IngestionCompletedEvent(1L, "cust-a", 1));
        service.search(new float[]{1.0f, 0.0f}, 5, "cust-a");

        verify(chunkRepository, times(2)).findByCustomerId("cust-a");
    }
}


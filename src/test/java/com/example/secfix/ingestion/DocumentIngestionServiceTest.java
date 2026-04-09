package com.example.secfix.ingestion;

import com.example.secfix.chunker.TextChunker;
import com.example.secfix.embedding.EmbeddingService;
import com.example.secfix.exception.UnsupportedFileTypeException;
import com.example.secfix.parser.DocumentParserFactory;
import com.example.secfix.repository.DocumentRepository;
import com.example.secfix.repository.KnowledgeChunkRepository;
import com.example.secfix.repository.VectorStoreRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    private TextChunker chunker;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private KnowledgeChunkRepository chunkRepository;
    @Mock
    private VectorStoreRepository vectorStoreRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Test
    void ingestRejectsNonXlsxInputs() {
        DocumentParserFactory parserFactory = new DocumentParserFactory(List.of());
        DocumentIngestionService service = new DocumentIngestionService(
                parserFactory,
                chunker,
                embeddingService,
                documentRepository,
                chunkRepository,
                vectorStoreRepository,
                eventPublisher
        );

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "security-policy.pdf",
                "application/pdf",
                "content".getBytes()
        );

        assertThrows(UnsupportedFileTypeException.class, () -> service.ingest(pdfFile, "cust-1"));
        verifyNoInteractions(documentRepository, chunkRepository, vectorStoreRepository, embeddingService, eventPublisher);
    }
}

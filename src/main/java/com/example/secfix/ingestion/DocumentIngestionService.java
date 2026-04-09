package com.example.secfix.ingestion;

import com.example.secfix.chunker.Chunk;
import com.example.secfix.chunker.TextChunker;
import com.example.secfix.domain.Document;
import com.example.secfix.domain.KnowledgeChunk;
import com.example.secfix.domain.VectorStore;
import com.example.secfix.embedding.EmbeddingService;
import com.example.secfix.parser.DocumentParserFactory;
import com.example.secfix.parser.PageContent;
import com.example.secfix.parser.ParsedDocument;
import com.example.secfix.parser.XlsxFileSupport;
import com.example.secfix.repository.DocumentRepository;
import com.example.secfix.repository.KnowledgeChunkRepository;
import com.example.secfix.repository.VectorStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService implements IngestionService {

    private final DocumentParserFactory parserFactory;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final DocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final VectorStoreRepository vectorStoreRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public IngestionResult ingest(MultipartFile file, String customerId) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        log.info("Ingesting file: {}, type: {}, customer: {}", fileName, contentType, customerId);
        XlsxFileSupport.requireXlsx(contentType, fileName, "ingestion");

        ParsedDocument parsed;
        try {
            parsed = parserFactory.getParser(contentType, fileName)
                    .parse(file.getInputStream(), fileName);
        } catch (IOException e) {
            throw new com.example.secfix.exception.DocumentParsingException(
                    "Failed to read uploaded file: " + fileName, e);
        }

        Document document = new Document();
        document.setTitle(parsed.title());
        document.setOriginalFileName(fileName != null ? fileName : "unknown");
        document.setCustomerId(customerId);
        document = documentRepository.save(document);

        List<Chunk> allChunks = new ArrayList<>();
        for (PageContent page : parsed.pages()) {
            allChunks.addAll(chunker.chunk(page.text(), page.pageNum()));
        }

        if (allChunks.isEmpty()) {
            log.warn("No chunks produced from '{}', ingestion complete with 0 chunks", fileName);
            eventPublisher.publishEvent(new IngestionCompletedEvent(document.getId(), customerId, 0));
            return new IngestionResult(document.getId(), 0);
        }

        List<String> texts = allChunks.stream().map(Chunk::text).toList();
        List<float[]> embeddings = embeddingService.embedBatch(texts);

        int saved = 0;
        for (int i = 0; i < allChunks.size(); i++) {
            Chunk chunk = allChunks.get(i);

            KnowledgeChunk kc = new KnowledgeChunk();
            kc.setDocument(document);
            kc.setChunkText(chunk.text());
            kc.setEmbedding(embeddings.get(i));
            kc.setPageNum(chunk.pageNum());
            kc.setCustomerId(customerId);
            kc = chunkRepository.save(kc);

            VectorStore vs = new VectorStore();
            vs.setDocument(document);
            vs.setChunk(kc);
            vs.setCustomerId(customerId);
            vectorStoreRepository.save(vs);
            saved++;
        }

        log.info("Ingested '{}': {} chunks stored", parsed.title(), saved);
        eventPublisher.publishEvent(new IngestionCompletedEvent(document.getId(), customerId, saved));
        return new IngestionResult(document.getId(), saved);
    }
}

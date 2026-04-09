package com.example.secfix.parser;

import com.example.secfix.exception.DocumentParsingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class WordDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx") || lower.endsWith(".doc")
                || "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equalsIgnoreCase(contentType)
                || "application/msword".equalsIgnoreCase(contentType);
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        String title = stripExtension(fileName);
        try {
            String text = fileName.toLowerCase().endsWith(".docx")
                    ? extractDocx(inputStream)
                    : extractDoc(inputStream);

            log.info("Parsed Word document '{}': {} chars", title, text.length());
            return new ParsedDocument(title, List.of(new PageContent(1, text)));
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse Word document: " + fileName, e);
        }
    }

    private String extractDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String extractDoc(InputStream inputStream) throws Exception {
        try (HWPFDocument doc = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(doc)) {
            return extractor.getText();
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

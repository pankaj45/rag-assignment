package com.example.secfix.parser;

import com.example.secfix.exception.DocumentParsingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    @Override
    public boolean supports(String contentType, String fileName) {
        return "application/pdf".equalsIgnoreCase(contentType)
                || (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName) {
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<PageContent> pages = new ArrayList<>();
            int totalPages = doc.getNumberOfPages();

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(doc).strip();
                if (!text.isBlank()) {
                    pages.add(new PageContent(i, text));
                }
            }

            String title = fileName != null ? stripExtension(fileName) : "Untitled";
            log.info("Parsed PDF '{}': {} pages with content", title, pages.size());
            return new ParsedDocument(title, pages);
        } catch (Exception e) {
            throw new DocumentParsingException("Failed to parse PDF: " + fileName, e);
        }
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}

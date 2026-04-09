package com.example.secfix.parser;

import com.example.secfix.exception.UnsupportedFileTypeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParser getParser(String contentType, String fileName) {
        return parsers.stream()
                .filter(p -> p.supports(contentType, fileName))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "No parser found for content-type '%s' and file '%s'".formatted(contentType, fileName)));
    }
}

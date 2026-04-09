package com.example.secfix.parser;

import java.io.InputStream;

public interface DocumentParser {

    boolean supports(String contentType, String fileName);

    ParsedDocument parse(InputStream inputStream, String fileName);
}

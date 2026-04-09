package com.example.secfix.parser;

import java.util.List;

public record ParsedDocument(String title, List<PageContent> pages) {}

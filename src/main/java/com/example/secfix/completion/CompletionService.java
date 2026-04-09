package com.example.secfix.completion;

public interface CompletionService {

    String complete(String systemPrompt, String userPrompt);

    String completeWithMaxTokens(String systemPrompt, String userPrompt, int maxTokens);
}

package com.example.secfix.completion;

import com.example.secfix.config.OpenAiProperties;
import com.example.secfix.exception.CompletionException;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompletionService implements CompletionService {

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return completeWithMaxTokens(systemPrompt, userPrompt, properties.getCompletion().getMaxTokens());
    }

    @Override
    public String completeWithMaxTokens(String systemPrompt, String userPrompt, int maxTokens) {
        var messages = List.of(
                new Message("system", systemPrompt),
                new Message("user", userPrompt)
        );
        var request = new ChatRequest(
                properties.getCompletion().getModel(),
                messages,
                maxTokens,
                properties.getCompletion().getTemperature()
        );

        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<ChatResponse> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/chat/completions",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    ChatResponse.class
            );
            if (response.getBody() == null || response.getBody().choices().isEmpty()) {
                throw new CompletionException("Empty response from OpenAI completions API");
            }
            return response.getBody().choices().getFirst().message().content().strip();
        } catch (CompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException("Failed to call OpenAI completions API: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    record ChatRequest(String model, List<Message> messages, @JsonProperty("max_tokens") int maxTokens, double temperature) {}

    record Message(String role, String content) {}

    record ChatResponse(List<Choice> choices) {}

    record Choice(Message message) {}
}

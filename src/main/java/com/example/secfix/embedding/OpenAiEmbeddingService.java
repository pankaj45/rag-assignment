package com.example.secfix.embedding;

import com.example.secfix.config.OpenAiProperties;
import com.example.secfix.exception.EmbeddingException;
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
public class OpenAiEmbeddingService implements EmbeddingService {

    private final RestTemplate restTemplate;
    private final OpenAiProperties properties;

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        var request = new EmbeddingRequest(properties.getEmbedding().getModel(), texts);
        var response = callApi(request);
        return response.data().stream()
                .map(d -> toFloatArray(d.embedding()))
                .toList();
    }

    private EmbeddingResponse callApi(EmbeddingRequest request) {
        HttpHeaders headers = buildHeaders();
        try {
            ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                    properties.getBaseUrl() + "/embeddings",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    EmbeddingResponse.class
            );
            if (response.getBody() == null) {
                throw new EmbeddingException("Empty response from OpenAI embeddings API");
            }
            return response.getBody();
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            throw new EmbeddingException("Failed to call OpenAI embeddings API: " + e.getMessage(), e);
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getApiKey());
        return headers;
    }

    private float[] toFloatArray(List<Double> doubles) {
        float[] result = new float[doubles.size()];
        for (int i = 0; i < doubles.size(); i++) {
            result[i] = doubles.get(i).floatValue();
        }
        return result;
    }

    record EmbeddingRequest(String model, List<String> input) {}

    record EmbeddingResponse(List<EmbeddingData> data) {}

    record EmbeddingData(List<Double> embedding) {}
}

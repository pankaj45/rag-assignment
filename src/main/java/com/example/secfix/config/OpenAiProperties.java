package com.example.secfix.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openai")
@Getter
@Setter
public class OpenAiProperties {

    private String apiKey;
    private String baseUrl;
    private Embedding embedding = new Embedding();
    private Completion completion = new Completion();

    @Getter
    @Setter
    public static class Embedding {
        private String model = "text-embedding-ada-002";
    }

    @Getter
    @Setter
    public static class Completion {
        private String model = "gpt-4o-mini";
        private double temperature = 0.0;
        private int maxTokens = 1024;
        private int schemaMaxTokens = 2048;
    }
}

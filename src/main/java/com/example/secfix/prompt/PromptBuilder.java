package com.example.secfix.prompt;

import com.example.secfix.vectorsearch.ScoredChunk;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptBuilder {

    public static final String SYSTEM_PROMPT = """
            You are a security compliance assistant.
            Answer questions using ONLY the information provided in the context below.
            If the context does not contain enough information to answer confidently, \
            respond with exactly: INSUFFICIENT_CONTEXT
            Do not invent, assume, or add any information not present in the context.
            """;

    public String buildUserPrompt(String questionText, List<ScoredChunk> chunks, List<String> allowedOptions) {
        return buildUserPrompt(questionText, chunks, allowedOptions, null, null);
    }

    public String buildUserPrompt(
            String questionText,
            List<ScoredChunk> chunks,
            List<String> allowedOptions,
            String selectedResponse
    ) {
        return buildUserPrompt(questionText, chunks, allowedOptions, selectedResponse, null);
    }

    public String buildUserPrompt(
            String questionText,
            List<ScoredChunk> chunks,
            List<String> allowedOptions,
            String selectedResponse,
            Integer maxLength
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context:\n");

        for (ScoredChunk sc : chunks) {
            String docTitle = sc.chunk().getDocument() != null
                    ? sc.chunk().getDocument().getTitle() : "Unknown";
            sb.append("---\n")
              .append("[Source: ").append(docTitle)
              .append(", page ").append(sc.chunk().getPageNum()).append("]\n")
              .append(sc.chunk().getChunkText())
              .append("\n");
        }
        sb.append("---\n\n");
        sb.append("Question: ").append(questionText).append("\n\n");

        if (allowedOptions != null && !allowedOptions.isEmpty()) {
            sb.append("You MUST respond with EXACTLY one of the following options (copy it verbatim):\n");
            for (String option : allowedOptions) {
                sb.append("- ").append(option).append("\n");
            }
            sb.append("\nDo not add any explanation. Return only the chosen option.\n");
        } else if (selectedResponse != null && !selectedResponse.isBlank()) {
            sb.append("The selected response for this question is: \"").append(selectedResponse).append("\"\n");
            sb.append("Provide a brief justification or comment explaining this response based solely on the context above.\n");
        } else {
            sb.append("Provide a concise answer based solely on the context above.\n");
        }

        if (maxLength != null && maxLength > 0) {
            sb.append("Your response MUST NOT exceed ").append(maxLength).append(" characters.\n");
        }

        sb.append("\nAnswer:");
        return sb.toString();
    }
}

package com.javallm.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingService.class);
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper(); // For converting Map to JSON String

    public EmbeddingService(@Value("${spring.embedding.service.url}") String embeddingServiceUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(embeddingServiceUrl)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    public Mono<float[]> generateEmbedding(String text) {
        // Create the payload map
        Map<String, String> payload = Map.of("inputs", text);

        // Calculate and log the size of the payload JSON string in bytes
        try {
            String jsonPayload = objectMapper.writeValueAsString(payload);
            byte[] jsonBytes = jsonPayload.getBytes("UTF-8"); // Ensure UTF-8 encoding
            logger.info("Sending embedding request for text length: {} characters. JSON payload size: {} bytes.",
                    text.length(), jsonBytes.length);
        } catch (JsonProcessingException e) {
            logger.error("Error converting payload to JSON for logging: {}", e.getMessage());
        } catch (java.io.UnsupportedEncodingException e) {
            logger.error("UTF-8 encoding not supported: {}", e.getMessage());
        }

        return webClient.post()
                .uri("/embed")
                .bodyValue(payload) // Use the created payload map
                .retrieve()
                .bodyToMono(List.class)
                .map(response -> {
                    @SuppressWarnings("unchecked")
                    List<List<Double>> embeddings = (List<List<Double>>) response;
                    List<Double> embedding = embeddings.get(0);
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = embedding.get(i).floatValue();
                    }
                    return result;
                });
    }

    // --- Helper methods for logging WebClient requests and responses ---

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            logger.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers()
                    .forEach((name, values) -> values.forEach(value -> logger.info("  {}: {}", name, value)));
            // No need to log body here, we're doing it before sending the request
            return Mono.just(clientRequest);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            logger.info("Response Status: {}", clientResponse.statusCode());
            clientResponse.headers().asHttpHeaders()
                    .forEach((name, values) -> values.forEach(value -> logger.info("  {}: {}", name, value)));
            return Mono.just(clientResponse);
        });
    }
}
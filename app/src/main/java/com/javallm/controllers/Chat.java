package com.javallm.controllers;

import static java.util.stream.Collectors.joining;

import java.util.List;
import java.util.Map; // Import Map

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.javallm.controllers.dto.ChatDto.ChatRequest;
import com.javallm.services.EmbeddingService;
import com.javallm.services.MilvusService;
import com.javallm.services.MilvusService.QueryResult;
import io.milvus.v2.service.vector.request.data.FloatVec;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.ChatClient;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class Chat {
    // SYSTEM_PROMPT and USER_PROMPT_TEMPLATE remain the same...
    private static final String SYSTEM_PROMPT = """
            You are an expert assistant with access to a knowledge base.
            Your job is to provide accurate, helpful answers based on the context provided in each query.
            Format your response using Markdown for better readability (use headings, bold, italic, lists, code blocks, etc. as appropriate).
            If the user's question is not covered by the provided context documents, say "I don't know."
            /no_think
            """;
    private static final String USER_PROMPT_TEMPLATE = """
            Use the following retrieved documents to answer my question.

            Context:
            %s

            Question:
            %s
            """;

    private final ChatClient chatClient;
    private final MilvusService milvusService;
    private final EmbeddingService embeddingService;

    public Chat(ChatClient.Builder chatClientBuilder,
            MilvusService milvusService,
            EmbeddingService embeddingService) {
        this.chatClient = chatClientBuilder.build();
        this.milvusService = milvusService;
        this.embeddingService = embeddingService;
    }

    @PostMapping("/message")
    public Flux<Map<String, Object>> streamChat(@RequestBody ChatRequest request) {
        String question = request.getMessage();
        if (question == null || question.isEmpty()) {
            return Flux.just(Map.of("error", "Message cannot be null or empty"));
        }

        return embeddingService.generateEmbedding(question)
                .flatMapMany(vec -> {
                    // 1) Query Milvus
                    FloatVec vect = new FloatVec(vec);
                    List<QueryResult> results = milvusService.queryCollection(vect);

                    // 2) Turn your QueryResult objects into JSON‐friendly maps
                    List<Map<String, Object>> resultsList = results.stream()
                            .map(r -> Map.<String, Object>of(
                                    "title", r.getDocumentName(),
                                    "pages", r.getDocumentPages(),
                                    "fileId", r.getFileId()))

                            .toList();

                    // 3) Assemble the context for the chat model
                    String context = results.stream()
                            .map(r -> String.format(
                                    "Title: %s (pages: %s)%nExcerpt: %s",
                                    r.getDocumentName(), r.getDocumentPages(), r.getText()))
                            .collect(joining("\n---\n"));
                    String userPrompt = String.format(USER_PROMPT_TEMPLATE, context, question);

                    // 4) Create your token‐stream Flux
                    Flux<Map<String, Object>> tokenFlux = chatClient
                            .prompt()
                            .system(SYSTEM_PROMPT)
                            .user(userPrompt)
                            .stream()
                            .content()
                            .map(chunk -> Map.<String, Object>of("delta", chunk))
                            .onErrorResume(e -> Flux.just(Map.of("error", "Stream error: " + e.getMessage())));

                    // 5) Concatenate your final "results" event
                    Flux<Map<String, Object>> doneFlux = Flux.just(Map.of("results", resultsList));

                    return tokenFlux.concatWith(doneFlux);
                })
                .onErrorResume(e -> Flux.just(Map.of("error", e.getMessage())));
    }
}
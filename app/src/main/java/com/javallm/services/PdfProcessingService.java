package com.javallm.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.javallm.services.PdfTextExtractorService.TextChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.List;

@Service
public class PdfProcessingService {

        private static final Logger logger = LoggerFactory.getLogger(PdfProcessingService.class);

        private final PdfTextExtractorService pdfTextExtractorService;
        private final EmbeddingService embeddingService;
        private final MilvusService milvusService;

        public PdfProcessingService(PdfTextExtractorService pdfTextExtractorService,
                        EmbeddingService embeddingService,
                        MilvusService milvusService) {
                this.pdfTextExtractorService = pdfTextExtractorService;
                this.embeddingService = embeddingService;
                this.milvusService = milvusService;
        }

        public Flux<String> processPdf(InputStream inputStream, String documentName, String fileUUID) {
                milvusService.initializeCollection();

                // 1. Wrap the blocking text extraction and splitting call.
                return Mono.fromCallable(() -> pdfTextExtractorService.extractAndSplitText(inputStream))
                                .subscribeOn(Schedulers.boundedElastic()) // Offload the blocking work
                                .flatMapMany(chunks -> { // This is now a List<TextChunk>
                                        int totalChunks = chunks.size();
                                        logger.info("Extracted and split into {} chunks from document: {}", totalChunks,
                                                        documentName);

                                        // Initial message now reports total chunks.
                                        String initialMessage = String.format(
                                                        "{\"type\": \"TOTAL_CHUNKS\", \"totalChunks\": %d, \"documentName\": \"%s\"}",
                                                        totalChunks, documentName);

                                        // 2. Create a Flux that processes each chunk sequentially.
                                        Flux<String> processingFlux = Flux.fromIterable(chunks)
                                                        .index()
                                                        .concatMap(indexedChunk -> {
                                                                long chunkIndex = indexedChunk.getT1();
                                                                TextChunk chunk = indexedChunk.getT2();
                                                                int chunkNum = (int) chunkIndex + 1;

                                                                // --- NEW LOGGING ADDED HERE ---
                                                                logger.info("Sending chunk {} (size: {} characters) for embedding from document: {}",
                                                                                chunkNum, chunk.content().length(),
                                                                                documentName);
                                                                // --- END NEW LOGGING ---

                                                                // Generate embedding for the chunk's content.
                                                                return embeddingService
                                                                                .generateEmbedding(chunk.content())
                                                                                .flatMap(embedding -> {
                                                                                        // Create row with chunk text
                                                                                        // and its page label.
                                                                                        JsonObject row = createMilvusRow(
                                                                                                        fileUUID,
                                                                                                        chunk.content(),
                                                                                                        documentName,
                                                                                                        chunk.pageLabel(),
                                                                                                        embedding);

                                                                                        // 3. Wrap the blocking database
                                                                                        // insert.
                                                                                        return Mono.fromRunnable(
                                                                                                        () -> milvusService
                                                                                                                        .insertPDFData(List
                                                                                                                                        .of(row)))
                                                                                                        .subscribeOn(Schedulers
                                                                                                                        .boundedElastic())
                                                                                                        .then(Mono.fromCallable(
                                                                                                                        () -> {
                                                                                                                                logger.info("Processed and inserted chunk {} of {} for {}",
                                                                                                                                                chunkNum,
                                                                                                                                                totalChunks,
                                                                                                                                                documentName);
                                                                                                                                // Update
                                                                                                                                // progress
                                                                                                                                // message
                                                                                                                                // for
                                                                                                                                // chunks.
                                                                                                                                return String.format(
                                                                                                                                                "{\"type\": \"PROGRESS\", \"chunk\": %d, \"totalChunks\": %d, \"documentName\": \"%s\"}",
                                                                                                                                                chunkNum,
                                                                                                                                                totalChunks,
                                                                                                                                                documentName);
                                                                                                                        }));
                                                                                });
                                                        });

                                        String completionMessage = String.format(
                                                        "{\"type\": \"COMPLETED\", \"documentName\": \"%s\", \"message\": \"Document processing complete.\"}",
                                                        documentName);

                                        // 4. Chain the events together: initial message, progress for each chunk, and a
                                        // final completion message.
                                        return Flux.concat(
                                                        Mono.just(initialMessage),
                                                        processingFlux,
                                                        Mono.just(completionMessage));
                                })
                                .doOnError(e -> {
                                        logger.error("Failed to process PDF document '{}': {}", documentName,
                                                        e.getMessage(), e);
                                        milvusService.deleteEmbeddingsByFileId(fileUUID);
                                })
                                .onErrorResume(e -> Flux.error(
                                                new RuntimeException("Failed to process PDF: " + e.getMessage(), e)));
        }

        /**
         * Creates a JSON object for Milvus insertion, now accepting a String pageLabel.
         */
        private JsonObject createMilvusRow(String fileUUID, String chunkText, String documentName, String pageLabel,
                        float[] embedding) {
                JsonObject row = new JsonObject();
                row.addProperty("file_id", fileUUID);
                row.addProperty("text", chunkText);
                row.addProperty("document_name", documentName);
                row.addProperty("document_pages", pageLabel); // Use the string page label directly

                JsonArray vectorArray = new JsonArray();
                for (float val : embedding) {
                        vectorArray.add(val);
                }
                row.add("embedding", vectorArray);
                return row;
        }
}
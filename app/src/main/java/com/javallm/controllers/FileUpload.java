package com.javallm.controllers;

import com.javallm.controllers.dto.FileDto;
import com.javallm.controllers.dto.FileDto.FileDeleteResponse;
import com.javallm.services.FileService;
import com.javallm.services.MilvusService;
import com.javallm.services.PdfProcessingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@CrossOrigin(origins = "*")
public class FileUpload {
    private static final List<String> ALLOWED_MIME_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain");

    private final String uploadDirectory = "uploads";
    private final PdfProcessingService pdfProcessingService;
    private final FileService fileService;
    private final MilvusService milvusService;

    public FileUpload(PdfProcessingService pdfProcessingService, FileService fileService, MilvusService milvusService) {
        this.pdfProcessingService = pdfProcessingService;
        this.fileService = fileService;
        this.milvusService = milvusService;
        System.out.println("FileUpload controller initialized with upload directory: " + uploadDirectory);
    }

    @PostMapping(value = "/upload-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> uploadFileSse(@RequestPart("file") FilePart filePart) {
        System.out.println("Received SSE file upload request for: " + filePart.filename());

        if (filePart.filename() == null || filePart.filename().isEmpty()) {
            return Flux.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot upload empty file or file without a name."));
        }

        String contentType = Objects.requireNonNull(filePart.headers().getContentType()).toString();
        if (!ALLOWED_MIME_TYPES.contains(contentType)) {
            System.err.println("Unsupported file type attempted: " + contentType + " for file: " + filePart.filename());
            return Flux.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type. Only PDF, Word, Excel, and .txt files are allowed."));
        }

        String originalFilename = Objects.requireNonNull(filePart.filename());
        String fileExtension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < originalFilename.length() - 1) {
            fileExtension = originalFilename.substring(dotIndex);
        }
        String fileUUID = UUID.randomUUID().toString();
        String uniqueFileName = originalFilename + fileUUID + fileExtension;
        Path filePath = Paths.get(uploadDirectory, uniqueFileName); // This filePath will be consistent

        return Mono.fromCallable(() -> {
            Files.createDirectories(filePath.getParent());
            return filePath;
        })
                .flatMap(createdFilePath -> filePart.transferTo(createdFilePath).thenReturn(createdFilePath))
                .flatMapMany(finalFilePath -> {
                    // Now, handle the IOException from Files.newInputStream reactively
                    // Use Mono.fromCallable to wrap the potentially blocking and throwing operation
                    return Mono.fromCallable(() -> Files.newInputStream(finalFilePath))
                            .flatMapMany(inputStream -> { // Once inputStream is successfully obtained
                                // This is the Flux that emits progress messages and the final completion
                                // message from PDF processing
                                Flux<ServerSentEvent<String>> processingFlux = pdfProcessingService
                                        .processPdf(inputStream, uniqueFileName, fileUUID)
                                        .map(message -> ServerSentEvent.<String>builder()
                                                .data(message)
                                                .build())
                                        .doFinally(signalType -> { // Ensure InputStream is closed
                                            try {
                                                if (inputStream != null) {
                                                    inputStream.close();
                                                }
                                            } catch (IOException e) {
                                                System.err.println("Error closing input stream: " + e.getMessage());
                                            }
                                        });

                                // Define the database saving Mono here, which depends on finalFilePath,
                                // uniqueFileName, contentType etc.
                                Mono<ServerSentEvent<String>> saveToDbCompletionEvent = Mono.fromRunnable(() -> {
                                    fileService.saveFile(fileUUID, originalFilename, finalFilePath.toString(),
                                            contentType);
                                    System.out.println("File metadata saved to DB for: " + uniqueFileName);
                                })
                                        .subscribeOn(Schedulers.boundedElastic()) // Ensure DB operation runs on a
                                                                                  // suitable scheduler
                                        .then(Mono.just(ServerSentEvent.<String>builder() // Emit a final success event
                                                                                          // if DB save is part of
                                                                                          // stream
                                                .data(String.format(
                                                        "{\"type\": \"DATABASE_SAVE_COMPLETED\", \"documentName\": \"%s\"}",
                                                        uniqueFileName))
                                                .build()));

                                return processingFlux.concatWith(saveToDbCompletionEvent);
                            })
                            .onErrorResume(IOException.class, e -> {
                                // If Files.newInputStream throws IOException, convert it to a Flux error event
                                System.err.println("Failed to open input stream for file " + finalFilePath + ": "
                                        + e.getMessage());
                                return Flux.just(ServerSentEvent.<String>builder()
                                        .event("error")
                                        .data("{\"type\": \"ERROR\", \"message\": \"Failed to open file for processing: "
                                                + e.getMessage() + "\"}")
                                        .build());
                            });
                })
                .onErrorResume(IOException.class, e -> {
                    System.err.println("Failed to upload file " + originalFilename + ": " + e.getMessage());
                    e.printStackTrace();
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("{\"type\": \"ERROR\", \"message\": \"Failed to upload file due to an internal error: "
                                    + e.getMessage() + "\"}")
                            .build());
                })
                .onErrorResume(ResponseStatusException.class, e -> {
                    System.err.println("Upload failed due to bad request or unsupported media type: " + e.getReason());
                    return Flux.just(ServerSentEvent.<String>builder()
                            .event("error")
                            .data("{\"type\": \"ERROR\", \"message\": \"" + e.getReason() + "\", \"status\": "
                                    + e.getStatusCode().value() + "}")
                            .build());
                });
    }

    // listFiles endpoint to return a list of files
    @GetMapping(value = "", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<FileDto.FileListResponse> listFiles() {
        return Flux.fromIterable(fileService.getAllFiles())
                .map(fileEntity -> {
                    return new FileDto.FileListResponse(
                            fileEntity.getId(),
                            fileEntity.getFilename(),
                            fileEntity.getPath(),
                            fileEntity.getUploadedAt() != null
                                    ? fileEntity.getUploadedAt()
                                    : null);
                });
    }

    @DeleteMapping("/{fileId}")
    public Mono<FileDeleteResponse> deleteFile(@PathVariable String fileId) {
        System.out.println("Received request to delete file with ID: " + fileId);

        return Mono.fromRunnable(() -> milvusService.deleteEmbeddingsByFileId(fileId)) // delete embeddings
                .then(Mono.fromRunnable(() -> fileService.deleteFile(fileId))) // delete DB entry
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(new FileDeleteResponse(fileId));
    }

}
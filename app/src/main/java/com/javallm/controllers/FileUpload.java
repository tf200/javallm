package com.javallm.controllers;

import com.javallm.controllers.dto.FileDto;
import com.javallm.controllers.dto.FileDto.FileDeleteResponse;
import com.javallm.services.ExcelProcessingService;
import com.javallm.services.FileService;
import com.javallm.services.MilvusService;
import com.javallm.services.PdfProcessingService;
import com.javallm.services.WordProcessingService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.io.InputStream;
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
            "application/vnd.ms-excel.sheet.macroEnabled.12",  // .xlsm files
            "application/vnd.ms-excel.sheet.binary.macroEnabled.12",  // .xlsb files
            "text/plain");

    private final String uploadDirectory = "uploads";
    private final PdfProcessingService pdfProcessingService;
    private final WordProcessingService wordProcessingService;
    private final ExcelProcessingService excelProcessingService;  // NEW: Add Excel service
    private final FileService fileService;
    private final MilvusService milvusService;

    public FileUpload(PdfProcessingService pdfProcessingService,
            WordProcessingService wordProcessingService,
            ExcelProcessingService excelProcessingService,  // NEW: Inject Excel service
            FileService fileService,
            MilvusService milvusService) {
        this.pdfProcessingService = pdfProcessingService;
        this.wordProcessingService = wordProcessingService;
        this.excelProcessingService = excelProcessingService;  // NEW: Assign Excel service
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
        Path filePath = Paths.get(uploadDirectory, uniqueFileName);

        return Mono.fromCallable(() -> {
            Files.createDirectories(filePath.getParent());
            return filePath;
        })
                .flatMap(createdFilePath -> filePart.transferTo(createdFilePath).thenReturn(createdFilePath))
                .flatMapMany(finalFilePath -> {
                    // Handle the IOException from Files.newInputStream reactively
                    return Mono.fromCallable(() -> Files.newInputStream(finalFilePath))
                            .flatMapMany(inputStream -> {
                                // Determine which processing service to use based on file type
                                Flux<String> processingFlux = determineProcessingService(contentType, originalFilename)
                                        .flatMapMany(service -> service.apply(inputStream, uniqueFileName, fileUUID));

                                // Convert processing flux to ServerSentEvent flux
                                Flux<ServerSentEvent<String>> sseProcessingFlux = processingFlux
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

                                // Define the database saving Mono
                                Mono<ServerSentEvent<String>> saveToDbCompletionEvent = Mono.fromRunnable(() -> {
                                    fileService.saveFile(fileUUID, originalFilename, finalFilePath.toString(),
                                            contentType);
                                    System.out.println("File metadata saved to DB for: " + uniqueFileName);
                                })
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .then(Mono.just(ServerSentEvent.<String>builder()
                                                .data(String.format(
                                                        "{\"type\": \"DATABASE_SAVE_COMPLETED\", \"documentName\": \"%s\"}",
                                                        uniqueFileName))
                                                .build()));

                                return sseProcessingFlux.concatWith(saveToDbCompletionEvent);
                            })
                            .onErrorResume(IOException.class, e -> {
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

    /**
     * Determines which processing service to use based on the file's content type
     * and filename.
     * Returns a Mono that emits a function that can process the file.
     */
    private Mono<TriFunction<InputStream, String, String, Flux<String>>> determineProcessingService(
            String contentType, String filename) {

        return Mono.fromCallable(() -> {
            // Check for PDF files
            if ("application/pdf".equals(contentType)) {
                System.out.println("Processing as PDF: " + filename);
                return (InputStream inputStream, String documentName, String fileUUID) -> pdfProcessingService
                        .processPdf(inputStream, documentName, fileUUID);
            }

            // Check for Word files (.doc and .docx)
            if ("application/msword".equals(contentType) ||
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)) {
                System.out.println("Processing as Word document: " + filename);
                return (InputStream inputStream, String documentName, String fileUUID) -> wordProcessingService
                        .processWord(inputStream, documentName, fileUUID);
            }

            // NEW: Check for Excel files (.xls, .xlsx, .xlsm, .xlsb)
            if ("application/vnd.ms-excel".equals(contentType) ||
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".equals(contentType) ||
                    "application/vnd.ms-excel.sheet.macroEnabled.12".equals(contentType) ||
                    "application/vnd.ms-excel.sheet.binary.macroEnabled.12".equals(contentType)) {
                System.out.println("Processing as Excel document: " + filename);
                return (InputStream inputStream, String documentName, String fileUUID) -> excelProcessingService
                        .processExcel(inputStream, documentName, fileUUID);
            }

            // For other supported file types, you can add additional processing services
            // here
            // For now, throw an exception for unsupported types
            throw new IllegalArgumentException("No processing service available for content type: " + contentType);
        });
    }

    /**
     * Functional interface for the processing service methods.
     */
    @FunctionalInterface
    private interface TriFunction<T, U, V, R> {
        R apply(T t, U u, V v);
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

    @GetMapping("/{fileId}")
    public Mono<ResponseEntity<Resource>> getFile(@PathVariable String fileId) {
        System.out.println("Received request to get file with ID: " + fileId);

        return Mono.fromCallable(() -> {
            // Get file metadata from database
            var fileEntityOptional = fileService.getFileById(fileId);
            if (fileEntityOptional == null || fileEntityOptional.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found");
            }
            var fileEntity = fileEntityOptional.get();

            // Get the file path and create a Path object
            Path filePath = Paths.get(fileEntity.getPath());

            // Check if file exists on disk
            if (!Files.exists(filePath)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on disk");
            }

            // Create a Resource from the file
            Resource resource = new FileSystemResource(filePath);

            // Build response with appropriate headers for PDF viewing
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileEntity.getFilename() + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);
        })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ResponseStatusException.class, e -> {
                    System.err.println("Error retrieving file: " + e.getReason());
                    return Mono.just(ResponseEntity.status(e.getStatusCode()).build());
                })
                .onErrorResume(Exception.class, e -> {
                    System.err.println("Unexpected error retrieving file: " + e.getMessage());
                    e.printStackTrace();
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
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
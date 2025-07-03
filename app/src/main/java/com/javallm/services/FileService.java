// src/main/java/com/javallm/service/FileService.java
package com.javallm.services;

import com.javallm.models.FileEntity;
import com.javallm.repository.FileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class FileService {

    private final FileRepository fileRepository;

    // Spring will automatically inject the FileRepository
    public FileService(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Transactional // Ensures the entire method runs within a transaction
    public FileEntity saveFile(String id, String filename, String path, String contentType) {
        FileEntity newFile = new FileEntity(id, filename, path, contentType);
        return fileRepository.save(newFile); // Saves the new file to the database
    }

    @Transactional(readOnly = true) // For read-only operations
    public List<FileEntity> getAllFiles() {
        return fileRepository.findAll(); // Retrieves all files
    }

    @Transactional(readOnly = true)
    public Optional<FileEntity> getFileById(String id) {
        return fileRepository.findById(id); // Finds a file by its ID
    }

    @Transactional(readOnly = true)
    public List<FileEntity> searchFilesByFilename(String filenamePart) {
        return fileRepository.findByFilenameContaining(filenamePart); // Uses the custom query method
    }

    @Transactional
    public void deleteFile(String id) {
        fileRepository.deleteById(id); // Deletes a file by ID
    }
}
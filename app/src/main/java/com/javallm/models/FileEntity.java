// src/main/java/com/javallm/model/FileEntity.java
package com.javallm.models;

import jakarta.persistence.*; // Use jakarta.persistence for Spring Boot 3+
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Entity // Marks this class as a JPA entity
@Table(name = "files") // Maps this entity to the 'files' table
public class FileEntity {

    @Id // Marks this field as the primary key
    private String id; // Use Long for auto-incrementing primary keys

    @Column(name = "filename", nullable = false) // Maps to 'filename' column, not null
    private String filename;

    @Column(name = "path", nullable = false) // Maps to 'path' column, not null
    private String path;

    @Column(name = "content_type", nullable = false) // Maps to 'content_type' column, not null
    private String contentType;

    @Column(name = "uploaded_at") // Maps to 'uploaded_at' column
    private String uploadedAt; // Use LocalDateTime for TIMESTAMP columns

    // --- Constructors ---
    public FileEntity() {
        // Default constructor required by JPA
    }

    public FileEntity(String id, String filename, String path, String contentType) {
        this.id = id; // Use UUID or String for custom IDs
        this.filename = filename;
        this.path = path;
        this.contentType = contentType;
        this.uploadedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // --- Getters and Setters ---
    // You need getters and setters for all fields for JPA to access them

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(String uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    @Override
    public String toString() {
        return "FileEntity{" +
                "id=" + id +
                ", filename='" + filename + '\'' +
                ", path='" + path + '\'' +
                ", contentType='" + contentType + '\'' +
                ", uploadedAt=" + uploadedAt +
                '}';
    }
}
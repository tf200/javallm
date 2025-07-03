// src/main/java/com/javallm/repository/FileRepository.java
package com.javallm.repository;

import com.javallm.models.FileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // Optional, but good practice for clarity
public interface FileRepository extends JpaRepository<FileEntity, String> {
    // JpaRepository provides methods like:
    // save(entity)
    // findById(id)
    // findAll()
    // delete(entity)
    // count()

    // You can also define custom query methods just by naming convention:
    List<FileEntity> findByFilenameContaining(String filename);
}
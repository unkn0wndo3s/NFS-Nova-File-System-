package com.nova.nfs.repo;

import com.nova.nfs.core.FileEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileRepository {

    FileEntry save(FileEntry file);

    Optional<FileEntry> findById(UUID id);

    List<FileEntry> findAll();

    void delete(UUID id);
}

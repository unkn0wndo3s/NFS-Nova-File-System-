package com.nova.nfs.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.nfs.core.FileEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JsonFileRepository implements FileRepository {

    private final Path filePath;
    private final ObjectMapper mapper;
    private final Map<UUID, FileEntry> storage = new HashMap<>();

    public JsonFileRepository(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        loadFromDisk();
    }

    private void loadFromDisk() {
        try {
            if (Files.exists(filePath)) {
                byte[] bytes = Files.readAllBytes(filePath);
                List<FileEntry> list = mapper.readValue(bytes, new TypeReference<List<FileEntry>>() {
                });
                storage.clear();
                for (FileEntry e : list) {
                    storage.put(e.getId(), e);
                }
            } else {
                Files.createDirectories(filePath.getParent());
                saveToDisk();
            }
        } catch (IOException e) {
            System.err.println("Failed to load FileRepository: " + e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            List<FileEntry> list = new ArrayList<>(storage.values());
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
            Files.write(filePath, bytes);
        } catch (IOException e) {
            System.err.println("Failed to save FileRepository: " + e.getMessage());
        }
    }

    @Override
    public FileEntry save(FileEntry file) {
        storage.put(file.getId(), file);
        saveToDisk();
        return file;
    }

    @Override
    public Optional<FileEntry> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<FileEntry> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void delete(UUID id) {
        storage.remove(id);
        saveToDisk();
    }
}

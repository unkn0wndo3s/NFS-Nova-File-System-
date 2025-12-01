package com.nova.nfs.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nova.nfs.core.Link;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JsonLinkRepository implements LinkRepository {

    private final Path filePath;
    private final ObjectMapper mapper;
    private final Map<UUID, Link> storage = new HashMap<>();

    public JsonLinkRepository(Path filePath) {
        this.filePath = filePath;
        this.mapper = new ObjectMapper();
        loadFromDisk();
    }

    private void loadFromDisk() {
        try {
            if (Files.exists(filePath)) {
                byte[] bytes = Files.readAllBytes(filePath);
                List<Link> list = mapper.readValue(bytes, new TypeReference<List<Link>>() {});
                storage.clear();
                for (Link l : list) {
                    storage.put(l.getId(), l);
                }
            } else {
                Files.createDirectories(filePath.getParent());
                saveToDisk();
            }
        } catch (IOException e) {
            System.err.println("Failed to load LinkRepository: " + e.getMessage());
        }
    }

    private void saveToDisk() {
        try {
            List<Link> list = new ArrayList<>(storage.values());
            byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(list);
            Files.write(filePath, bytes);
        } catch (IOException e) {
            System.err.println("Failed to save LinkRepository: " + e.getMessage());
        }
    }

    @Override
    public Link save(Link link) {
        storage.put(link.getId(), link);
        saveToDisk();
        return link;
    }

    @Override
    public Optional<Link> findById(UUID id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Link> findChildren(UUID parentId) {
        List<Link> result = new ArrayList<>();
        for (Link l : storage.values()) {
            if (Objects.equals(l.getParentId(), parentId)) {
                result.add(l);
            }
        }
        return result;
    }

    @Override
    public List<Link> findAll() {
        return new ArrayList<>(storage.values());
    }

    @Override
    public void delete(UUID id) {
        storage.remove(id);
        saveToDisk();
    }
}

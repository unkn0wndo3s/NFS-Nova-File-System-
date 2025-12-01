package com.nova.nfs.service;

import com.nova.nfs.core.FileEntry;
import com.nova.nfs.core.Link;
import com.nova.nfs.core.LinkType;
import com.nova.nfs.repo.FileRepository;
import com.nova.nfs.repo.LinkRepository;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class NovaFsService {

    private final FileRepository fileRepo;
    private final LinkRepository linkRepo;
    private final Path filesRootDir;
    private final UUID rootLinkId;
    private final UUID trashLinkId;

    public Link saveLink(Link link) {
        return linkRepo.save(link);
    }

    public FileEntry saveFileEntry(FileEntry fileEntry) {
        return fileRepo.save(fileEntry);
    }


    public NovaFsService(FileRepository fileRepo,
                         LinkRepository linkRepo,
                         Path filesRootDir,
                         UUID rootLinkId,
                         UUID trashLinkId) {
        this.fileRepo = fileRepo;
        this.linkRepo = linkRepo;
        this.filesRootDir = filesRootDir;
        this.rootLinkId = rootLinkId;
        this.trashLinkId = trashLinkId;

        try {
            Files.createDirectories(filesRootDir);
        } catch (IOException e) {
            throw new RuntimeException("Unable to create files root dir: " + filesRootDir, e);
        }
    }

    public UUID getRootLinkId() {
        return rootLinkId;
    }

    public UUID getTrashLinkId() {
        return trashLinkId;
    }

    // ---------- Création / import ----------

    public Link createFolder(UUID parentId, String name) {
        Link folder = new Link(LinkType.FOLDER, name);
        folder.setParentId(parentId);
        return linkRepo.save(folder);
    }

    public Link createManagedFileWithLink(UUID parentFolderLinkId,
                                          String displayName,
                                          String extension) throws IOException {
        String fileName = UUID.randomUUID().toString() + "." + extension;
        Path dest = filesRootDir.resolve(fileName);
        Files.createFile(dest);

        FileEntry entry = new FileEntry(displayName, extension, dest.toString());
        fileRepo.save(entry);

        Link fileLink = new Link(LinkType.FILE, displayName);
        fileLink.setParentId(parentFolderLinkId);
        fileLink.setTargetFileId(entry.getId());
        return linkRepo.save(fileLink);
    }

    public Link importExistingFile(UUID parentFolderLinkId, Path sourcePath) throws IOException {
        String origName = sourcePath.getFileName().toString();
        String extension = "";
        int idx = origName.lastIndexOf('.');
        if (idx != -1 && idx < origName.length() - 1) {
            extension = origName.substring(idx + 1);
        }

        String newFileName = UUID.randomUUID().toString();
        if (!extension.isEmpty()) {
            newFileName += "." + extension;
        }

        Path dest = filesRootDir.resolve(newFileName);
        Files.createDirectories(filesRootDir);
        Files.copy(sourcePath, dest, StandardCopyOption.REPLACE_EXISTING);

        FileEntry entry = new FileEntry(origName, extension, dest.toString());
        fileRepo.save(entry);

        Link fileLink = new Link(LinkType.FILE, origName);
        fileLink.setParentId(parentFolderLinkId);
        fileLink.setTargetFileId(entry.getId());
        return linkRepo.save(fileLink);
    }

    // ---------- Navigation ----------

    public List<Link> getChildren(UUID parentId) {
        return linkRepo.findChildren(parentId);
    }

    public Optional<Link> findLink(UUID id) {
        return linkRepo.findById(id);
    }

    public Optional<FileEntry> getFileForFileLink(Link link) {
        if (link.getType() != LinkType.FILE) return Optional.empty();
        UUID fileId = link.getTargetFileId();
        if (fileId == null) return Optional.empty();
        return fileRepo.findById(fileId);
    }

    public String resolveLogicalPath(UUID linkId) {
        Link current = linkRepo.findById(linkId)
                .orElseThrow(() -> new IllegalArgumentException("link not found"));

        Deque<String> parts = new ArrayDeque<>();
        parts.addFirst(current.getDisplayName());

        UUID parentId = current.getParentId();
        while (parentId != null) {
            Link parent = linkRepo.findById(parentId)
                    .orElseThrow(() -> new IllegalStateException("broken parent chain"));

            if (parent.getType() != LinkType.ROOT) {
                parts.addFirst(parent.getDisplayName());
            }

            parentId = parent.getParentId();
        }

        return "/" + String.join("/", parts);
    }

    // ---------- Move / Corbeille / Delete ----------

    public void moveFile(UUID fileLinkId, UUID newParentFolderId) {
        Link link = linkRepo.findById(fileLinkId)
                .orElseThrow(() -> new IllegalArgumentException("fileLink not found"));

        if (link.getType() != LinkType.FILE) {
            throw new IllegalArgumentException("Link is not FILE type");
        }

        link.setParentId(newParentFolderId);
        linkRepo.save(link);
    }

    public void moveFileToTrash(UUID fileLinkId) {
        Link link = linkRepo.findById(fileLinkId)
                .orElseThrow(() -> new IllegalArgumentException("fileLink not found"));

        if (link.getType() != LinkType.FILE) {
            throw new IllegalArgumentException("Link is not FILE type");
        }

        link.setParentId(trashLinkId);
        linkRepo.save(link);
    }

    public void deleteFilePermanently(UUID fileLinkId) throws IOException {
        Link link = linkRepo.findById(fileLinkId)
                .orElseThrow(() -> new IllegalArgumentException("fileLink not found"));

        if (link.getType() != LinkType.FILE) {
            throw new IllegalArgumentException("Link is not FILE type");
        }

        UUID fileId = link.getTargetFileId();
        if (fileId != null) {
            fileRepo.findById(fileId).ifPresent(entry -> {
                Path p = Paths.get(entry.getPhysicalPath());
                if (Files.exists(p)) {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        System.err.println("Failed to delete file: " + p + " - " + e.getMessage());
                    }
                }
                fileRepo.delete(fileId);
            });
        }

        linkRepo.delete(fileLinkId);
    }

    // ---------- Nettoyage / cohérence ----------

    public void cleanupDanglingFileLinks() {
        List<Link> allLinks = linkRepo.findAll();
        Set<UUID> existingFiles = fileRepo.findAll().stream()
                .map(FileEntry::getId)
                .collect(Collectors.toSet());

        for (Link l : allLinks) {
            if (l.getType() != LinkType.FILE) continue;

            UUID fid = l.getTargetFileId();
            boolean invalid = (fid == null) || !existingFiles.contains(fid);
            if (invalid) {
                linkRepo.delete(l.getId());
            }
        }
    }

    public void attachOrphanFilesToRoot() {
        List<FileEntry> allFiles = fileRepo.findAll();
        List<Link> allLinks = linkRepo.findAll();

        Set<UUID> filesWithLink = allLinks.stream()
                .filter(l -> l.getType() == LinkType.FILE && l.getTargetFileId() != null)
                .map(Link::getTargetFileId)
                .collect(Collectors.toSet());

        for (FileEntry f : allFiles) {
            if (!filesWithLink.contains(f.getId())) {
                Link fileLink = new Link(LinkType.FILE, f.getDisplayName());
                fileLink.setParentId(rootLinkId);
                fileLink.setTargetFileId(f.getId());
                linkRepo.save(fileLink);
            }
        }
    }
}

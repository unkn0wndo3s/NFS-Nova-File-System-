package com.nova.nfs.core;

import java.util.UUID;

/**
 * Représente un fichier physique géré par NFS (dans C:/NFS/files).
 */
public class FileEntry {

    private UUID id;
    private String displayName;
    private String extension;
    private String physicalPath; // stocké en String pour JSON

    public FileEntry() {
        // pour Jackson
    }

    public FileEntry(String displayName, String extension, String physicalPath) {
        this.id = UUID.randomUUID();
        this.displayName = displayName;
        this.extension = extension;
        this.physicalPath = physicalPath;
    }

    public UUID getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getExtension() {
        return extension;
    }

    public String getPhysicalPath() {
        return physicalPath;
    }

    public void setPhysicalPath(String physicalPath) {
        this.physicalPath = physicalPath;
    }
}

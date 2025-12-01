package com.nova.nfs.core;

import java.util.UUID;

/**
 * Link logique : ROOT, FOLDER, FILE, TRASH.
 * FILE -> targetFileId (FileEntry)
 * FOLDER/ROOT/TRASH -> seulement parentId.
 */
public class Link {

    private UUID id;
    private LinkType type;
    private String displayName;
    private UUID parentId;     // null pour ROOT
    private UUID targetFileId; // seulement pour type FILE

    public Link() {
        // pour Jackson
    }

    public Link(LinkType type, String displayName) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.displayName = displayName;
    }

    public UUID getId() {
        return id;
    }

    public LinkType getType() {
        return type;
    }

    public void setType(LinkType type) {
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public UUID getTargetFileId() {
        return targetFileId;
    }

    public void setTargetFileId(UUID targetFileId) {
        this.targetFileId = targetFileId;
    }

    @Override
    public String toString() {
        return displayName != null ? displayName : (type != null ? type.name() : "Link");
    }
}

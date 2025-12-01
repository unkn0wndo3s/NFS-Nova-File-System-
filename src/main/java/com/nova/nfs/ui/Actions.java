package com.nova.nfs.ui;

import com.nova.nfs.core.FileEntry;
import com.nova.nfs.core.Link;
import com.nova.nfs.core.LinkType;
import com.nova.nfs.service.NovaFsService;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;

public class Actions {

    public static void importFromWindows(Stage stage, NovaFsService nfs, Link currentFolder) {
        if (currentFolder == null || (currentFolder.getType() != LinkType.FOLDER
                && currentFolder.getType() != LinkType.ROOT
                && currentFolder.getType() != LinkType.TRASH)) {
            System.out.println("Current selection is not a folder. Import cancelled.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select files or folders to import into NFS");
        var files = chooser.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) {
            return;
        }

        for (File f : files) {
            try {
                if (f.isDirectory()) {
                    nfs.importDirectoryRecursive(currentFolder.getId(), f.toPath());
                } else {
                    nfs.importExistingFile(currentFolder.getId(), f.toPath());
                }
            } catch (IOException e) {
                System.err.println("Failed to import: " + f + " - " + e.getMessage());
            }
        }
    }

    public static void exportSelectedToWindows(Stage stage, NovaFsService nfs, Link selectedLink) {
        if (selectedLink == null || selectedLink.getType() != LinkType.FILE) {
            System.out.println("No file selected for export.");
            return;
        }

        var optFile = nfs.getFileForFileLink(selectedLink);
        if (optFile.isEmpty()) {
            System.out.println("FileEntry not found for link.");
            return;
        }

        FileEntry entry = optFile.get();
        Path src = Paths.get(entry.getPhysicalPath());
        if (!Files.exists(src)) {
            System.out.println("Physical file does not exist: " + src);
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose export destination");
        File dir = chooser.showDialog(stage);
        if (dir == null) {
            return;
        }

        String name = entry.getDisplayName();
        if (entry.getExtension() != null && !entry.getExtension().isEmpty()) {
            if (!name.toLowerCase().endsWith("." + entry.getExtension().toLowerCase())) {
                name = name + "." + entry.getExtension();
            }
        }

        Path dest = dir.toPath().resolve(name);
        try {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to export file: " + e.getMessage());
        }
    }

    public static void moveToTrash(NovaFsService nfs, Link selectedLink) {
        if (selectedLink == null || selectedLink.getType() != LinkType.FILE) {
            System.out.println("No file selected for trash.");
            return;
        }
        nfs.moveFileToTrash(selectedLink.getId());
    }

    public static void openFile(NovaFsService nfs, Link link) {
        if (link == null || link.getType() != LinkType.FILE) {
            return;
        }

        var optFile = nfs.getFileForFileLink(link);
        if (optFile.isEmpty()) {
            System.out.println("FileEntry not found for link.");
            return;
        }

        FileEntry entry = optFile.get();
        File f = new File(entry.getPhysicalPath());
        if (!f.exists()) {
            System.out.println("Physical file does not exist: " + f);
            return;
        }

        if (!Desktop.isDesktopSupported()) {
            System.out.println("Desktop API not supported.");
            return;
        }

        try {
            Desktop.getDesktop().open(f);
        } catch (IOException e) {
            System.err.println("Failed to open file: " + e.getMessage());
        }
    }
}

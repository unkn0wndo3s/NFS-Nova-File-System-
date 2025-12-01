package com.nova.nfs.ui;

import com.nova.nfs.core.Link;
import com.nova.nfs.core.LinkType;
import com.nova.nfs.repo.JsonFileRepository;
import com.nova.nfs.repo.JsonLinkRepository;
import com.nova.nfs.repo.LinkRepository;
import com.nova.nfs.service.NovaFsService;
import com.nova.nfs.util.Bootstrap;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class NfsExplorerApp extends Application {

    private NovaFsService nfs;
    private TreeView<Link> treeView;
    private TableView<Link> tableView;

    @Override
    public void start(Stage primaryStage) {
        // Racine NFS sur Windows
        Path baseDir = Path.of("C:/NFS");
        Path dataDir = baseDir.resolve("data");
        Path filesRoot = baseDir.resolve("files");

        var fileRepo = new JsonFileRepository(dataDir.resolve("files.json"));
        LinkRepository linkRepo = new JsonLinkRepository(dataDir.resolve("links.json"));

        // root + trash
        Link root = Bootstrap.ensureRoot(linkRepo);
        Link trash = Bootstrap.ensureTrash(linkRepo, root.getId());

        nfs = new NovaFsService(fileRepo, linkRepo, filesRoot, root.getId(), trash.getId());

        // nettoyage cohérence
        nfs.cleanupDanglingFileLinks();
        nfs.attachOrphanFilesToRoot();

        treeView = new TreeView<>();
        tableView = new TableView<>();

        setupTree(root);
        setupTable();

        SplitPane split = new SplitPane(treeView, tableView);
        split.setDividerPositions(0.3);

        ToolBar toolbar = buildToolbar(primaryStage);

        BorderPane rootPane = new BorderPane();
        rootPane.setTop(toolbar);
        rootPane.setCenter(split);

        Scene scene = new Scene(rootPane, 1200, 800);
        primaryStage.setTitle("Nova File System Explorer");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private ToolBar buildToolbar(Stage stage) {
        Button importBtn = new Button("Import");
        importBtn.setOnAction(e -> {
            Link folder = getCurrentFolderLink();
            Actions.importFromWindows(stage, nfs, folder);
            refreshCurrentFolder();
            refreshTree();
        });

        Button newFolderBtn = new Button("New Folder");
        newFolderBtn.setOnAction(e -> {
            Link parent = getCurrentFolderLink();
            if (parent != null && (parent.getType() == LinkType.FOLDER || parent.getType() == LinkType.ROOT)) {
                TextInputDialog dialog = new TextInputDialog("New Folder");
                dialog.setTitle("New folder");
                dialog.setHeaderText("Folder Name:");
                dialog.setContentText("Name:");
                dialog.showAndWait().ifPresent(name -> {
                    nfs.createFolder(parent.getId(), name);
                    refreshCurrentFolder();
                    refreshTree();
                });
            }
        });

        Button renameBtn = new Button("Rename");
        renameBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            if (selected == null) return;

            TextInputDialog dialog = new TextInputDialog(selected.getDisplayName());
            dialog.setTitle("Rename");
            dialog.setHeaderText("Rename item");
            dialog.setContentText("New name:");
            dialog.showAndWait().ifPresent(newName -> {
                selected.setDisplayName(newName);

                // Si fichier → MAJ FileEntry aussi
                nfs.getFileForFileLink(selected).ifPresent(file -> {
                    file.setDisplayName(newName);
                    nfs.saveFileEntry(file);
                });

                nfs.saveLink(selected);
                refreshCurrentFolder();
                refreshTree();
            });
        });

        Button exportBtn = new Button("Export");
        exportBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            Actions.exportSelectedToWindows(stage, nfs, selected);
        });

        Button trashBtn = new Button("Move to Trash");
        trashBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            Actions.moveToTrash(nfs, selected);
            refreshCurrentFolder();
            refreshTree();
        });

        Button openBtn = new Button("Open");
        openBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            Actions.openFile(nfs, selected);
        });

        return new ToolBar(importBtn, newFolderBtn, renameBtn, exportBtn, trashBtn, openBtn);
    }

    private void setupTree(Link rootLink) {
        TreeItem<Link> rootItem = new TreeItem<>(rootLink);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);

        loadFolderChildrenRecursive(rootItem);

        treeView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) {
                onFolderSelected(newV.getValue());
            }
        });
    }

    private void loadFolderChildrenRecursive(TreeItem<Link> parentItem) {
        Link parentLink = parentItem.getValue();
        List<Link> children = nfs.getChildren(parentLink.getId());

        for (Link child : children) {
            if (child.getType() == LinkType.FOLDER || child.getType() == LinkType.TRASH) {
                TreeItem<Link> item = new TreeItem<>(child);
                item.setExpanded(true);
                parentItem.getChildren().add(item);
                loadFolderChildrenRecursive(item);
            }
        }
    }

    private void setupTable() {
        TableColumn<Link, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDisplayName()));

        TableColumn<Link, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getType().name()));

        tableView.getColumns().addAll(nameCol, typeCol);
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Drag & drop depuis Windows vers le dossier courant
        tableView.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
            }
            event.consume();
        });

        tableView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                Link folder = getCurrentFolderLink();
                for (var f : db.getFiles()) {
                    try {
                        nfs.importExistingFile(folder.getId(), f.toPath());
                    } catch (Exception e) {
                        System.err.println("Import failed: " + e);
                    }
                }
                refreshCurrentFolder();
                refreshTree();
            }
            event.setDropCompleted(true);
            event.consume();
        });

        tableView.setRowFactory(tv -> {
            TableRow<Link> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Link link = row.getItem();
                    if (link.getType() == LinkType.FOLDER || link.getType() == LinkType.TRASH) {
                        selectFolderInTree(link.getId());
                    } else if (link.getType() == LinkType.FILE) {
                        Actions.openFile(nfs, link);
                    }
                }
            });
            return row;
        });

        VBox.setVgrow(tableView, Priority.ALWAYS);
    }

    private void onFolderSelected(Link folderLink) {
        tableView.getItems().clear();
        var children = nfs.getChildren(folderLink.getId());
        tableView.getItems().addAll(children);
    }

    private Link getCurrentFolderLink() {
        TreeItem<Link> sel = treeView.getSelectionModel().getSelectedItem();
        if (sel == null) return treeView.getRoot().getValue();
        return sel.getValue();
    }

    private void refreshCurrentFolder() {
        Link folder = getCurrentFolderLink();
        onFolderSelected(folder);
    }

    private void selectFolderInTree(UUID linkId) {
        TreeItem<Link> rootItem = treeView.getRoot();
        TreeItem<Link> found = findItemById(rootItem, linkId);
        if (found != null) {
            treeView.getSelectionModel().select(found);
        }
    }

    private TreeItem<Link> findItemById(TreeItem<Link> item, UUID id) {
        if (item.getValue().getId().equals(id)) {
            return item;
        }
        for (TreeItem<Link> child : item.getChildren()) {
            TreeItem<Link> found = findItemById(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private void refreshTree() {
        Link root = nfs.findLink(nfs.getRootLinkId()).orElseThrow();
        TreeItem<Link> rootItem = new TreeItem<>(root);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
        loadFolderChildrenRecursive(rootItem);
        // On re-sélectionne le dossier courant pour resync table (optionnel)
        refreshCurrentFolder();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

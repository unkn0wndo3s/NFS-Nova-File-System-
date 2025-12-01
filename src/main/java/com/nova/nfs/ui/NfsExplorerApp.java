package com.nova.nfs.ui;

import com.nova.nfs.core.Link;
import com.nova.nfs.core.LinkType;
import com.nova.nfs.repo.JsonFileRepository;
import com.nova.nfs.repo.JsonLinkRepository;
import com.nova.nfs.repo.LinkRepository;
import com.nova.nfs.service.NovaFsService;
import com.nova.nfs.util.Bootstrap;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class NfsExplorerApp extends Application {

    private NovaFsService nfs;
    private TreeView<Link> treeView;
    private TableView<Link> tableView;

    @Override
    public void start(Stage primaryStage) {
        Path baseDir = Path.of("C:/NFS");
        Path dataDir = baseDir.resolve("data");
        Path filesRoot = baseDir.resolve("files");

        var fileRepo = new JsonFileRepository(dataDir.resolve("files.json"));
        LinkRepository linkRepo = new JsonLinkRepository(dataDir.resolve("links.json"));

        Link root = Bootstrap.ensureRoot(linkRepo);
        Link trash = Bootstrap.ensureTrash(linkRepo, root.getId());

        nfs = new NovaFsService(fileRepo, linkRepo, filesRoot, root.getId(), trash.getId());

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
            refreshTreePreserveSelection(); // rafraîchir l'arbre pour voir les nouveaux dossiers
            refreshCurrentFolder(); // rafraîchir la table pour voir les nouveaux fichiers
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
                    refreshTreePreserveSelection();
                });
            }
        });

        Button renameBtn = new Button("Rename");
        renameBtn.setOnAction(e -> renameSelected());

        Button exportBtn = new Button("Export");
        exportBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            Actions.exportSelectedToWindows(stage, nfs, selected);
        });

        Button trashBtn = new Button("Move to Trash");
        trashBtn.setOnAction(e -> {
            Link selected = tableView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.getType() == LinkType.FILE) {
                Actions.moveToTrash(nfs, selected);
                refreshCurrentFolder();
                // pas besoin de toucher à l'arbre
            }
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

        // DnD cible : déplacer fichier/dossier vers un dossier de l'arbre
        treeView.setCellFactory(tv -> {
            TreeCell<Link> cell = new TreeCell<>() {
                @Override
                protected void updateItem(Link item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : item.getDisplayName());
                }
            };

            cell.setOnDragOver(event -> {
                var db = event.getDragboard();
                if (db.hasString() && cell.getItem() != null) {
                    Link target = cell.getItem();
                    if (target.getType() == LinkType.FOLDER
                            || target.getType() == LinkType.ROOT
                            || target.getType() == LinkType.TRASH) {
                        event.acceptTransferModes(TransferMode.MOVE);
                    }
                }
                event.consume();
            });

            cell.setOnDragDropped(event -> {
                var db = event.getDragboard();
                boolean success = false;
                if (db.hasString() && cell.getItem() != null) {
                    try {
                        UUID draggedId = UUID.fromString(db.getString());
                        Link target = cell.getItem();
                        nfs.moveLink(draggedId, target.getId());
                        success = true;
                        refreshTreePreserveSelection();
                    } catch (Exception ex) {
                        System.err.println("Move failed: " + ex.getMessage());
                    }
                }
                event.setDropCompleted(success);
                event.consume();
            });

            return cell;
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

        // DnD depuis Windows (fichiers + dossiers)
        tableView.setOnDragOver(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        tableView.setOnDragDropped(event -> {
            var db = event.getDragboard();
            if (db.hasFiles()) {
                Link folder = getCurrentFolderLink();
                for (File f : db.getFiles()) {
                    try {
                        if (f.isDirectory()) {
                            nfs.importDirectoryRecursive(folder.getId(), f.toPath());
                        } else {
                            nfs.importExistingFile(folder.getId(), f.toPath());
                        }
                    } catch (Exception e) {
                        System.err.println("Import failed: " + e);
                    }
                }
                refreshCurrentFolder();
            }
            event.setDropCompleted(true);
            event.consume();
        });

        // Lignes : double clic, DnD interne et menu contextuel clic droit
        tableView.setRowFactory(tv -> {
            TableRow<Link> row = new TableRow<>();

            // DOUBLE CLIC
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

            // DnD source : déplacer un link vers un dossier dans l'arbre
            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Link link = row.getItem();
                    var db = row.startDragAndDrop(TransferMode.MOVE);
                    ClipboardContent content = new ClipboardContent();
                    content.putString(link.getId().toString());
                    db.setContent(content);
                    event.consume();
                }
            });

            // CONTEXT MENU (clic droit)
            ContextMenu menu = new ContextMenu();

            MenuItem openItem = new MenuItem("Open");
            openItem.setOnAction(e -> {
                Link link = row.getItem();
                if (link == null) return;
                if (link.getType() == LinkType.FILE) {
                    Actions.openFile(nfs, link);
                } else if (link.getType() == LinkType.FOLDER || link.getType() == LinkType.TRASH) {
                    selectFolderInTree(link.getId());
                }
            });

            MenuItem renameItem = new MenuItem("Rename");
            renameItem.setOnAction(e -> {
                Link link = row.getItem();
                if (link != null) renameLink(link);
            });

            MenuItem moveTrashItem = new MenuItem("Move to Trash");
            moveTrashItem.setOnAction(e -> {
                Link link = row.getItem();
                if (link != null && link.getType() == LinkType.FILE) {
                    Actions.moveToTrash(nfs, link);
                    refreshCurrentFolder();
                }
            });

            MenuItem exportItem = new MenuItem("Export");
            exportItem.setOnAction(e -> {
                Link link = row.getItem();
                if (link != null) {
                    Actions.exportSelectedToWindows(
                            (Stage) tableView.getScene().getWindow(), nfs, link);
                }
            });

            MenuItem deleteItem = new MenuItem("Delete permanently");
            deleteItem.setOnAction(e -> {
                Link link = row.getItem();
                if (link != null && link.getType() == LinkType.FILE) {
                    try {
                        nfs.deleteFilePermanently(link.getId());
                        refreshCurrentFolder();
                    } catch (Exception ex) {
                        System.err.println("Delete failed: " + ex.getMessage());
                    }
                }
            });

            menu.getItems().addAll(openItem, renameItem, moveTrashItem, exportItem, deleteItem);

            // attacher/détacher le menu selon la ligne vide ou pas
            row.contextMenuProperty().bind(
                    Bindings.when(row.emptyProperty().not())
                            .then(menu)
                            .otherwise((ContextMenu) null)
            );

            VBox.setVgrow(tableView, Priority.ALWAYS);
            return row;
        });
    }

    private void renameSelected() {
        Link selected = tableView.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        renameLink(selected);
    }

    private void renameLink(Link link) {
        TextInputDialog dialog = new TextInputDialog(link.getDisplayName());
        dialog.setTitle("Rename");
        dialog.setHeaderText("Rename item");
        dialog.setContentText("New name:");
        dialog.showAndWait().ifPresent(newName -> {
            link.setDisplayName(newName);
            nfs.getFileForFileLink(link).ifPresent(file -> {
                file.setDisplayName(newName);
                nfs.saveFileEntry(file);
            });
            nfs.saveLink(link);
            refreshCurrentFolder();
            // l'arbre ne change que si c'est un dossier → pas grave s'il reste un peu en retard
        });
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

    /**
     * Refresh de l'arbre en gardant le dossier courant sélectionné
     * pour éviter les sauts et l'effet chiant de reset complet.
     */
    private void refreshTreePreserveSelection() {
        UUID currentId = getCurrentFolderLink().getId();

        Link root = nfs.findLink(nfs.getRootLinkId()).orElseThrow();
        TreeItem<Link> rootItem = new TreeItem<>(root);
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
        loadFolderChildrenRecursive(rootItem);

        TreeItem<Link> sel = findItemById(rootItem, currentId);
        if (sel != null) {
            treeView.getSelectionModel().select(sel);
        }

        refreshCurrentFolder();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

package com.isna.controller;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.geometry.Pos;
import javafx.application.Platform;
import javafx.scene.web.HTMLEditor;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import com.isna.model.Note;
import com.google.firebase.database.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MainController {
    @FXML private TreeView<String> treeView;
    @FXML private HTMLEditor htmlEditor;
    @FXML private Button saveNoteButton;
    private Map<String, String> notes = new HashMap<>();
    private String userId;
    private ValueEventListener noteListener;

    @FXML
    public void initialize() {
        initializeTreeView();
        htmlEditor.setVisible(false);
        saveNoteButton.setVisible(false);

        htmlEditor.setOnKeyReleased(event -> handleAutoSave()); // Auto-save functionality

        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleTreeViewDoubleClick();
            }
        });
    }

    public void setUserId(String userId) {
        this.userId = userId;
        initializeFirebaseListener();
    }

    private void initializeTreeView() {
        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
        treeView.setShowRoot(false);

        treeView.setCellFactory(tv -> new TreeCell<String>() {
            private final Button deleteButton = new Button("Delete");
            private final HBox hBox = new HBox();
            private final Label label = new Label();

            {
                HBox.setHgrow(label, Priority.ALWAYS);
                label.setMaxWidth(Double.MAX_VALUE);
                hBox.getChildren().addAll(label, deleteButton);
                hBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(item);
                    deleteButton.setOnAction(event -> {
                        TreeItem<String> currentItem = getTreeItem();
                        confirmAndDelete(currentItem);
                    });
                    setGraphic(hBox);
                    setText(null);
                }
            }
        });
    }

    private void initializeFirebaseListener() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notes").child(userId);
        noteListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                notes.clear();
                treeView.getRoot().getChildren().clear();
                for (DataSnapshot noteSnapshot : dataSnapshot.getChildren()) {
                    String title = noteSnapshot.getKey();
                    if (noteSnapshot.hasChildren()) {
                        TreeItem<String> folderItem = new TreeItem<>(title, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
                        treeView.getRoot().getChildren().add(folderItem);
                        addSubItems(folderItem, noteSnapshot);
                    } else {
                        String content = noteSnapshot.getValue(String.class);
                        notes.put(title, content);
                        TreeItem<String> noteItem = new TreeItem<>(title);
                        treeView.getRoot().getChildren().add(noteItem);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Failed to read data: " + databaseError.getCode());
            }
        };
        ref.addValueEventListener(noteListener);
    }

    private void addSubItems(TreeItem<String> parent, DataSnapshot snapshot) {
        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
            String childTitle = childSnapshot.getKey();
            if (childSnapshot.hasChildren()) {
                TreeItem<String> subFolder = new TreeItem<>(childTitle, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
                parent.getChildren().add(subFolder);
                addSubItems(subFolder, childSnapshot);
            } else {
                String content = childSnapshot.getValue(String.class);
                notes.put(getFullPath(parent) + "/" + childTitle, content);
                TreeItem<String> noteItem = new TreeItem<>(childTitle);
                parent.getChildren().add(noteItem);
            }
        }
    }

    @FXML
    private void handleSaveNote() {
        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
        if (selectedNote != null && selectedNote.getParent() != null) {
            String noteContent = htmlEditor.getHtmlText();
            notes.put(getFullPath(selectedNote), noteContent);

            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(selectedNote));
            noteRef.setValueAsync(noteContent).addListener(() -> {
                Platform.runLater(() -> {
                    refreshTreeView();  // Refresh tree view to reflect new data
                    showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
                    htmlEditor.setVisible(false);
                    saveNoteButton.setVisible(false);
                });
            }, Platform::runLater);
        }
    }

    @FXML
    private void handleNewFolder() {
        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
        TextInputDialog dialog = new TextInputDialog("Folder Name");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Enter the name for the new folder:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> {
            if (!name.trim().isEmpty()) {
                TreeItem<String> newFolder = new TreeItem<>(name, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
                if (selectedItem == null || selectedItem.getValue().equals("Root")) {
                    treeView.getRoot().getChildren().add(newFolder);
                } else if (!selectedItem.isLeaf() || selectedItem.getParent() != null) {
                    selectedItem.getChildren().add(newFolder);
                    selectedItem.setExpanded(true);
                } else {
                    selectedItem.getParent().getChildren().add(newFolder);
                }
                treeView.getSelectionModel().select(newFolder);

                // Simpan folder ke Firebase
                DatabaseReference folderRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newFolder));
                folderRef.setValueAsync(new HashMap<String, Object>());
            }
        });
    }

    @FXML
    private void handleNewNote() {
        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
        if (selectedFolder == null || selectedFolder.isLeaf() && (selectedFolder.getParent() == null || selectedFolder.getParent().getValue().equals("Root"))) {
            showAlert("Error", "Please select a folder or note to add a new note.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog("Note Title");
        dialog.setTitle("New Note");
        dialog.setHeaderText("Enter the title for the new note:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(title -> {
            if (!title.trim().isEmpty()) {
                TreeItem<String> newNote = new TreeItem<>(title);
                if (selectedFolder.isLeaf() && selectedFolder.getParent() != null && !selectedFolder.getParent().getValue().equals("Root")) {
                    selectedFolder.getParent().getChildren().add(newNote);
                } else {
                    selectedFolder.getChildren().add(newNote);
                }
                selectedFolder.setExpanded(true);
                treeView.getSelectionModel().select(newNote);
                notes.put(getFullPath(newNote), "");

                htmlEditor.setHtmlText("");
                htmlEditor.setVisible(true);
                saveNoteButton.setVisible(true);

                // Simpan catatan ke Firebase
                DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newNote));
                noteRef.setValueAsync("");
            }
        });
    }

    private void handleTreeViewDoubleClick() {
        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && !selectedItem.getValue().equals("Root")) {
            String fullPath = getFullPath(selectedItem);
            if (notes.containsKey(fullPath)) {
                htmlEditor.setHtmlText(notes.get(fullPath));
                htmlEditor.setVisible(true);
                saveNoteButton.setVisible(true);
            } else {
                htmlEditor.setVisible(false);
                saveNoteButton.setVisible(false);
            }
        } else {
            htmlEditor.setVisible(false);
            saveNoteButton.setVisible(false);
        }
    }

    @FXML
    private void handleLogout() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/isna/view/login.fxml"));
        Stage stage = (Stage) treeView.getScene().getWindow();
        Scene scene = new Scene(loader.load());
        stage.setScene(scene);
        stage.show();
    }

    private void refreshTreeView() {
        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
        notes.forEach((title, content) -> {
            if (title.contains("/")) {
                String[] parts = title.split("/");
                TreeItem<String> currentParent = rootItem;
                for (int i = 0; i < parts.length - 1; i++) {
                    currentParent = findOrCreateFolder(currentParent, parts[i]);
                }
                TreeItem<String> noteItem = new TreeItem<>(parts[parts.length - 1]);
                currentParent.getChildren().add(noteItem);
            } else {
                TreeItem<String> noteItem = new TreeItem<>(title);
                rootItem.getChildren().add(noteItem);
            }
        });
        treeView.setShowRoot(false);
    }

    private TreeItem<String> findOrCreateFolder(TreeItem<String> root, String folderName) {
        for (TreeItem<String> child : root.getChildren()) {
            if (child.getValue().equals(folderName)) {
                return child;
            }
        }
        TreeItem<String> newFolder = new TreeItem<>(folderName, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
        root.getChildren().add(newFolder);
        return newFolder;
    }

    private String getFullPath(TreeItem<String> item) {
        StringBuilder fullPath = new StringBuilder(item.getValue());
        TreeItem<String> parent = item.getParent();
        while (parent != null && !parent.getValue().equals("Root")) {
            fullPath.insert(0, parent.getValue() + "/");
            parent = parent.getParent();
        }
        return fullPath.toString();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void confirmAndDelete(TreeItem<String> item) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to delete '" + item.getValue() + "'?");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            item.getParent().getChildren().remove(item);
            notes.remove(getFullPath(item));
            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(item));
            noteRef.removeValueAsync();
        }
    }

    private void handleAutoSave() {
        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
        if (selectedNote != null && htmlEditor.isVisible()) {
            String noteContent = htmlEditor.getHtmlText();
            notes.put(getFullPath(selectedNote), noteContent);

            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(selectedNote));
            noteRef.setValueAsync(noteContent).addListener(() -> Platform.runLater(() -> {
                System.out.println("Auto-saved note: " + selectedNote.getValue());
            }), Platform::runLater);
        }
    }
}




//package com.isna.controller;
//
//import javafx.fxml.FXML;
//import javafx.scene.control.*;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.geometry.Pos;
//import javafx.application.Platform;
//import javafx.scene.web.HTMLEditor;
//import javafx.stage.Stage;
//import javafx.scene.Scene;
//import javafx.fxml.FXMLLoader;
//import com.isna.model.Note;
//import com.google.firebase.database.*;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//public class MainController {
//    @FXML private TreeView<String> treeView;
//    @FXML private HTMLEditor htmlEditor;
//    @FXML private Button saveNoteButton;
//    private Map<String, String> notes = new HashMap<>();
//    private String userId;
//    private ValueEventListener noteListener;
//
//    @FXML
//    public void initialize() {
//        initializeTreeView();
//        htmlEditor.setVisible(false);
//        saveNoteButton.setVisible(false);
//
//        htmlEditor.setOnKeyReleased(event -> handleAutoSave()); // Auto-save functionality
//
//        treeView.setOnMouseClicked(event -> {
//            if (event.getClickCount() == 2) {
//                handleTreeViewDoubleClick();
//            }
//        });
//    }
//
//    public void setUserId(String userId) {
//        this.userId = userId;
//        initializeFirebaseListener();
//    }
//
//    private void initializeTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        treeView.setShowRoot(false);
//
//        treeView.setCellFactory(tv -> new TreeCell<String>() {
//            private final Button deleteButton = new Button("Delete");
//            private final HBox hBox = new HBox();
//            private final Label label = new Label();
//
//            {
//                HBox.setHgrow(label, Priority.ALWAYS);
//                label.setMaxWidth(Double.MAX_VALUE);
//                hBox.getChildren().addAll(label, deleteButton);
//                hBox.setAlignment(Pos.CENTER_LEFT);
//            }
//
//            @Override
//            protected void updateItem(String item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty || item == null) {
//                    setText(null);
//                    setGraphic(null);
//                } else {
//                    label.setText(item);
//                    deleteButton.setOnAction(event -> {
//                        TreeItem<String> currentItem = getTreeItem();
//                        confirmAndDelete(currentItem);
//                    });
//                    setGraphic(hBox);
//                    setText(null);
//                }
//            }
//        });
//    }
//
//    private void initializeFirebaseListener() {
//        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notes").child(userId);
//        noteListener = new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                notes.clear();
//                treeView.getRoot().getChildren().clear();
//                for (DataSnapshot noteSnapshot : dataSnapshot.getChildren()) {
//                    String title = noteSnapshot.getKey();
//                    if (noteSnapshot.hasChildren()) {
//                        TreeItem<String> folderItem = new TreeItem<>(title, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                        treeView.getRoot().getChildren().add(folderItem);
//                        addSubItems(folderItem, noteSnapshot);
//                    } else {
//                        String content = noteSnapshot.getValue(String.class);
//                        notes.put(title, content);
//                        TreeItem<String> noteItem = new TreeItem<>(title);
//                        treeView.getRoot().getChildren().add(noteItem);
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//                System.err.println("Failed to read data: " + databaseError.getCode());
//            }
//        };
//        ref.addValueEventListener(noteListener);
//    }
//
//    private void addSubItems(TreeItem<String> parent, DataSnapshot snapshot) {
//        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
//            String childTitle = childSnapshot.getKey();
//            if (childSnapshot.hasChildren()) {
//                TreeItem<String> subFolder = new TreeItem<>(childTitle, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                parent.getChildren().add(subFolder);
//                addSubItems(subFolder, childSnapshot);
//            } else {
//                String content = childSnapshot.getValue(String.class);
//                notes.put(childTitle, content);
//                TreeItem<String> noteItem = new TreeItem<>(childTitle);
//                parent.getChildren().add(noteItem);
//            }
//        }
//    }
//
//    @FXML
//    private void handleSaveNote() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null && selectedNote.getParent() != null) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(selectedNote));
//            noteRef.setValueAsync(noteContent).addListener(() -> {
//                Platform.runLater(() -> {
//                    refreshTreeView();  // Refresh tree view to reflect new data
//                    showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
//                    htmlEditor.setVisible(false);
//                    saveNoteButton.setVisible(false);
//                });
//            }, Platform::runLater);
//        }
//    }
//
//    @FXML
//    private void handleNewFolder() {
//        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
//        TextInputDialog dialog = new TextInputDialog("Folder Name");
//        dialog.setTitle("New Folder");
//        dialog.setHeaderText("Enter the name for the new folder:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(name -> {
//            if (!name.trim().isEmpty()) {
//                TreeItem<String> newFolder = new TreeItem<>(name, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                if (selectedItem == null || selectedItem.getValue().equals("Root")) {
//                    treeView.getRoot().getChildren().add(newFolder);
//                } else if (!selectedItem.isLeaf() || selectedItem.getParent() != null) {
//                    selectedItem.getChildren().add(newFolder);
//                    selectedItem.setExpanded(true);
//                } else {
//                    selectedItem.getParent().getChildren().add(newFolder);
//                }
//                treeView.getSelectionModel().select(newFolder);
//
//                // Simpan folder ke Firebase
//                DatabaseReference folderRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newFolder));
//                folderRef.setValueAsync(new HashMap<String, Object>());
//            }
//        });
//    }
//
//    @FXML
//    private void handleNewNote() {
//        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
//        if (selectedFolder == null || selectedFolder.isLeaf() && (selectedFolder.getParent() == null || selectedFolder.getParent().getValue().equals("Root"))) {
//            showAlert("Error", "Please select a folder or note to add a new note.");
//            return;
//        }
//        TextInputDialog dialog = new TextInputDialog("Note Title");
//        dialog.setTitle("New Note");
//        dialog.setHeaderText("Enter the title for the new note:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(title -> {
//            if (!title.trim().isEmpty()) {
//                TreeItem<String> newNote = new TreeItem<>(title);
//                if (selectedFolder.isLeaf() && selectedFolder.getParent() != null && !selectedFolder.getParent().getValue().equals("Root")) {
//                    selectedFolder.getParent().getChildren().add(newNote);
//                } else {
//                    selectedFolder.getChildren().add(newNote);
//                }
//                selectedFolder.setExpanded(true);
//                treeView.getSelectionModel().select(newNote);
//                notes.put(getFullPath(newNote), "");
//
//                htmlEditor.setHtmlText("");
//                htmlEditor.setVisible(true);
//                saveNoteButton.setVisible(true);
//
//                // Simpan catatan ke Firebase
//                DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newNote));
//                noteRef.setValueAsync("");
//            }
//        });
//    }
//
//    private void handleTreeViewDoubleClick() {
//        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
//        if (selectedItem != null && notes.containsKey(getFullPath(selectedItem))) {
//            htmlEditor.setHtmlText(notes.get(getFullPath(selectedItem)));
//            htmlEditor.setVisible(true);
//            saveNoteButton.setVisible(true);
//        } else {
//            htmlEditor.setVisible(false);
//            saveNoteButton.setVisible(false);
//        }
//    }
//
//    @FXML
//    private void handleLogout() throws IOException {
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/isna/view/login.fxml"));
//        Stage stage = (Stage) treeView.getScene().getWindow();
//        Scene scene = new Scene(loader.load());
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    private void refreshTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        notes.forEach((title, content) -> {
//            if (title.contains("/")) {
//                String[] parts = title.split("/");
//                TreeItem<String> currentParent = rootItem;
//                for (int i = 0; i < parts.length - 1; i++) {
//                    currentParent = findOrCreateFolder(currentParent, parts[i]);
//                }
//                TreeItem<String> noteItem = new TreeItem<>(parts[parts.length - 1]);
//                currentParent.getChildren().add(noteItem);
//            } else {
//                TreeItem<String> noteItem = new TreeItem<>(title);
//                rootItem.getChildren().add(noteItem);
//            }
//        });
//        treeView.setShowRoot(false);
//    }
//
//    private TreeItem<String> findOrCreateFolder(TreeItem<String> root, String folderName) {
//        for (TreeItem<String> child : root.getChildren()) {
//            if (child.getValue().equals(folderName)) {
//                return child;
//            }
//        }
//        TreeItem<String> newFolder = new TreeItem<>(folderName, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        root.getChildren().add(newFolder);
//        return newFolder;
//    }
//
//    private String getFullPath(TreeItem<String> item) {
//        StringBuilder fullPath = new StringBuilder(item.getValue());
//        TreeItem<String> parent = item.getParent();
//        while (parent != null && !parent.getValue().equals("Root")) {
//            fullPath.insert(0, parent.getValue() + "/");
//            parent = parent.getParent();
//        }
//        return fullPath.toString();
//    }
//
//    private void showAlert(String title, String message) {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle(title);
//        alert.setHeaderText(null);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }
//
//    private void confirmAndDelete(TreeItem<String> item) {
//        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
//        alert.setTitle("Confirm Delete");
//        alert.setHeaderText(null);
//        alert.setContentText("Are you sure you want to delete '" + item.getValue() + "'?");
//        Optional<ButtonType> result = alert.showAndWait();
//        if (result.isPresent() && result.get() == ButtonType.OK) {
//            item.getParent().getChildren().remove(item);
//            notes.remove(getFullPath(item));
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(item));
//            noteRef.removeValueAsync();
//        }
//    }
//
//    private void handleAutoSave() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null && htmlEditor.isVisible()) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(getFullPath(selectedNote), noteContent);
//
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(selectedNote));
//            noteRef.setValueAsync(noteContent).addListener(() -> Platform.runLater(() -> {
//                System.out.println("Auto-saved note: " + selectedNote.getValue());
//            }), Platform::runLater);
//        }
//    }
//}



//
//package com.isna.controller;
//
//import javafx.fxml.FXML;
//import javafx.scene.control.*;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.geometry.Pos;
//import javafx.application.Platform;
//import javafx.scene.web.HTMLEditor;
//import javafx.stage.Stage;
//import javafx.scene.Scene;
//import javafx.fxml.FXMLLoader;
//import com.isna.model.Note;
//import com.google.firebase.database.*;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//public class MainController {
//    @FXML private TreeView<String> treeView;
//    @FXML private HTMLEditor htmlEditor;
//    @FXML private Button saveNoteButton;
//    private Map<String, String> notes = new HashMap<>();
//    private String userId;
//    private ValueEventListener noteListener;
//
//    @FXML
//    public void initialize() {
//        initializeTreeView();
//        htmlEditor.setVisible(false);
//        saveNoteButton.setVisible(false);
//
//        htmlEditor.setOnKeyReleased(event -> handleAutoSave()); // Auto-save functionality
//    }
//
//    public void setUserId(String userId) {
//        this.userId = userId;
//        initializeFirebaseListener();
//    }
//
//    private void initializeTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        treeView.setShowRoot(false);
//
//        treeView.setCellFactory(tv -> new TreeCell<String>() {
//            private final Button deleteButton = new Button("Delete");
//            private final HBox hBox = new HBox();
//            private final Label label = new Label();
//
//            {
//                HBox.setHgrow(label, Priority.ALWAYS);
//                label.setMaxWidth(Double.MAX_VALUE);
//                hBox.getChildren().addAll(label, deleteButton);
//                hBox.setAlignment(Pos.CENTER_LEFT);
//            }
//
//            @Override
//            protected void updateItem(String item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty || item == null) {
//                    setText(null);
//                    setGraphic(null);
//                } else {
//                    label.setText(item);
//                    deleteButton.setOnAction(event -> {
//                        TreeItem<String> currentItem = getTreeItem();
//                        confirmAndDelete(currentItem);
//                    });
//                    setGraphic(hBox);
//                    setText(null);
//                }
//            }
//        });
//    }
//
//    private void initializeFirebaseListener() {
//        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notes").child(userId); // Menggunakan referensi ke "notes" dan ID pengguna
//        noteListener = new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                notes.clear();
//                treeView.getRoot().getChildren().clear();
//                for (DataSnapshot noteSnapshot : dataSnapshot.getChildren()) {
//                    String title = noteSnapshot.getKey();
//                    if (noteSnapshot.hasChildren()) {
//                        // Ini adalah folder
//                        TreeItem<String> folderItem = new TreeItem<>(title, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                        treeView.getRoot().getChildren().add(folderItem);
//                        for (DataSnapshot childSnapshot : noteSnapshot.getChildren()) {
//                            String childTitle = childSnapshot.getKey();
//                            String content = childSnapshot.getValue(String.class);
//                            notes.put(childTitle, content);
//                            TreeItem<String> noteItem = new TreeItem<>(childTitle);
//                            folderItem.getChildren().add(noteItem);
//                        }
//                    } else {
//                        // Ini adalah catatan di root
//                        String content = noteSnapshot.getValue(String.class);
//                        notes.put(title, content);
//                        TreeItem<String> noteItem = new TreeItem<>(title);
//                        treeView.getRoot().getChildren().add(noteItem);
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//                System.err.println("Failed to read data: " + databaseError.getCode());
//            }
//        };
//        ref.addValueEventListener(noteListener);
//    }
//
//    @FXML
//    private void handleSaveNote() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null && selectedNote.getParent() != null) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            DatabaseReference noteRef;
//            if (selectedNote.getParent().getValue().equals("Root")) {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getValue());
//            } else {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getParent().getValue()).child(selectedNote.getValue());
//            }
//            noteRef.setValueAsync(noteContent).addListener(() -> {
//                Platform.runLater(() -> {
//                    refreshTreeView();  // Refresh tree view to reflect new data
//                    showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
//                    htmlEditor.setVisible(false);
//                    saveNoteButton.setVisible(false);
//                });
//            }, Platform::runLater);
//        }
//    }
//
//    @FXML
//    private void handleNewFolder() {
//        TextInputDialog dialog = new TextInputDialog("Folder Name");
//        dialog.setTitle("New Folder");
//        dialog.setHeaderText("Enter the name for the new folder:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(name -> {
//            if (!name.trim().isEmpty()) {
//                TreeItem<String> newFolder = new TreeItem<>(name, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                treeView.getRoot().getChildren().add(newFolder);
//                treeView.getSelectionModel().select(newFolder);
//
//                // Simpan folder ke Firebase
//                DatabaseReference folderRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(name);
//                folderRef.setValueAsync(new HashMap<String, Object>());
//            }
//        });
//    }
//
//    @FXML
//    private void handleNewNote() {
//        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
//        if (selectedFolder == null || selectedFolder.getValue().equals("Root")) {
//            showAlert("Error", "Please select a folder to add a new note.");
//            return;
//        }
//        TextInputDialog dialog = new TextInputDialog("Note Title");
//        dialog.setTitle("New Note");
//        dialog.setHeaderText("Enter the title for the new note:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(title -> {
//            if (!title.trim().isEmpty()) {
//                TreeItem<String> newNote = new TreeItem<>(title);
//                selectedFolder.getChildren().add(newNote);
//                treeView.getSelectionModel().select(newNote);
//                notes.put(title, "");
//                htmlEditor.setHtmlText("");
//                htmlEditor.setVisible(true);
//                saveNoteButton.setVisible(true);
//
//                // Simpan catatan ke Firebase
//                DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedFolder.getValue()).child(title);
//                noteRef.setValueAsync("");
//            }
//        });
//    }
//
//    @FXML
//    private void handleLogout() throws IOException {
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/isna/view/login.fxml"));
//        Stage stage = (Stage) treeView.getScene().getWindow();
//        Scene scene = new Scene(loader.load());
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    private void refreshTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        notes.forEach((title, content) -> {
//            if (title.contains("/")) {
//                String[] parts = title.split("/", 2);
//                TreeItem<String> folderItem = findOrCreateFolder(rootItem, parts[0]);
//                TreeItem<String> noteItem = new TreeItem<>(parts[1]);
//                folderItem.getChildren().add(noteItem);
//            } else {
//                TreeItem<String> noteItem = new TreeItem<>(title);
//                rootItem.getChildren().add(noteItem);
//            }
//        });
//        treeView.setShowRoot(false);
//    }
//
//    private TreeItem<String> findOrCreateFolder(TreeItem<String> root, String folderName) {
//        for (TreeItem<String> child : root.getChildren()) {
//            if (child.getValue().equals(folderName)) {
//                return child;
//            }
//        }
//        TreeItem<String> newFolder = new TreeItem<>(folderName, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        root.getChildren().add(newFolder);
//        return newFolder;
//    }
//
//    private void showAlert(String title, String message) {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle(title);
//        alert.setHeaderText(null);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }
//
//    private void confirmAndDelete(TreeItem<String> item) {
//        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
//        alert.setTitle("Confirm Delete");
//        alert.setHeaderText(null);
//        alert.setContentText("Are you sure you want to delete '" + item.getValue() + "'?");
//        Optional<ButtonType> result = alert.showAndWait();
//        if (result.isPresent() && result.get() == ButtonType.OK) {
//            item.getParent().getChildren().remove(item);
//            notes.remove(item.getValue());
//            DatabaseReference noteRef;
//            if (item.getParent().getValue().equals("Root")) {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(item.getValue());
//            } else {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(item.getParent().getValue()).child(item.getValue());
//            }
//            noteRef.removeValueAsync();
//        }
//    }
//
//    private void handleAutoSave() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null && htmlEditor.isVisible()) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            DatabaseReference noteRef;
//            if (selectedNote.getParent().getValue().equals("Root")) {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getValue());
//            } else {
//                noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getParent().getValue()).child(selectedNote.getValue());
//            }
//            noteRef.setValueAsync(noteContent).addListener(() -> Platform.runLater(() -> {
//                System.out.println("Auto-saved note: " + selectedNote.getValue());
//            }), Platform::runLater);
//        }
//    }
//}



//package com.isna.controller;
//
//import javafx.fxml.FXML;
//import javafx.scene.control.*;
//import javafx.scene.control.TreeView;
//import javafx.scene.control.TreeItem;
//import javafx.scene.image.Image;
//import javafx.scene.image.ImageView;
//import javafx.scene.web.HTMLEditor;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.geometry.Pos;
//import javafx.application.Platform;
//import javafx.stage.Stage;
//import javafx.scene.Scene;
//import javafx.fxml.FXMLLoader;
//import com.isna.model.Note;
//import com.google.firebase.database.*;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//public class MainController {
//    @FXML private TreeView<String> treeView;
//    @FXML private HTMLEditor htmlEditor;
//    @FXML private Button saveNoteButton;
//    private Map<String, String> notes = new HashMap<>();
//    private String userId;
//    private ValueEventListener noteListener;
//
//    @FXML
//    public void initialize() {
//        initializeTreeView();
//        htmlEditor.setVisible(false);
//        saveNoteButton.setVisible(false);
//
//        htmlEditor.setOnKeyReleased(event -> handleAutoSave()); // Auto-save functionality
//    }
//
//    public void setUserId(String userId) {
//        this.userId = userId;
//        initializeFirebaseListener();
//    }
//
//    private void initializeTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        treeView.setShowRoot(false);
//
//        treeView.setCellFactory(tv -> new TreeCell<String>() {
//            private final Button deleteButton = new Button("Delete");
//            private final HBox hBox = new HBox();
//            private final Label label = new Label();
//
//            {
//                HBox.setHgrow(label, Priority.ALWAYS);
//                label.setMaxWidth(Double.MAX_VALUE);
//                hBox.getChildren().addAll(label, deleteButton);
//                hBox.setAlignment(Pos.CENTER_LEFT);
//            }
//
//            @Override
//            protected void updateItem(String item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty || item == null) {
//                    setText(null);
//                    setGraphic(null);
//                } else {
//                    label.setText(item);
//                    deleteButton.setOnAction(event -> {
//                        TreeItem<String> currentItem = getTreeItem();
//                        confirmAndDelete(currentItem);
//                    });
//                    setGraphic(hBox);
//                    setText(null);
//                }
//            }
//        });
//    }
//
//    private void initializeFirebaseListener() {
//        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("notes").child(userId); // Menggunakan referensi ke "notes" dan ID pengguna
//        noteListener = new ValueEventListener() {
//            @Override
//            public void onDataChange(DataSnapshot dataSnapshot) {
//                notes.clear();
//                treeView.getRoot().getChildren().clear();
//                for (DataSnapshot noteSnapshot : dataSnapshot.getChildren()) {
//                    String title = noteSnapshot.getKey(); // Mengambil kunci catatan sebagai judul
//                    String content = noteSnapshot.getValue(String.class); // Mengambil isi catatan
//                    notes.put(title, content);
//                    TreeItem<String> noteItem = new TreeItem<>(title);
//                    // Cek apakah folder milik pengguna yang sedang login
//                    if (!title.equals("Root")) { // Jika bukan folder Root
//                        treeView.getRoot().getChildren().add(noteItem);
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(DatabaseError databaseError) {
//                System.err.println("Failed to read data: " + databaseError.getCode());
//            }
//        };
//        ref.addValueEventListener(noteListener);
//    }
//
//    @FXML
//    private void handleSaveNote() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getValue()); // Menggunakan referensi ke "notes" dan ID pengguna
//            noteRef.setValueAsync(noteContent).addListener(() -> {
//                Platform.runLater(() -> {
//                    refreshTreeView();  // Refresh tree view to reflect new data
//                    showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
//                    htmlEditor.setVisible(false);
//                    saveNoteButton.setVisible(false);
//                });
//            }, Platform::runLater);
//        }
//    }
//
//    @FXML
//    private void handleNewFolder() {
//        TextInputDialog dialog = new TextInputDialog("Folder Name");
//        dialog.setTitle("New Folder");
//        dialog.setHeaderText("Enter the name for the new folder:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(name -> {
//            if (!name.trim().isEmpty()) {
//                TreeItem<String> newFolder = new TreeItem<>(name, new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//                treeView.getRoot().getChildren().add(newFolder);
//                treeView.getSelectionModel().select(newFolder);
//            }
//        });
//    }
//
//    @FXML
//    private void handleNewNote() {
//        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
//        if (selectedFolder == null || selectedFolder.getValue().equals("Root")) {
//            showAlert("Error", "Please select a folder to add a new note.");
//            return;
//        }
//        TextInputDialog dialog = new TextInputDialog("Note Title");
//        dialog.setTitle("New Note");
//        dialog.setHeaderText("Enter the title for the new note:");
//        Optional<String> result = dialog.showAndWait();
//        result.ifPresent(title -> {
//            if (!title.trim().isEmpty()) {
//                TreeItem<String> newNote = new TreeItem<>(title);
//                selectedFolder.getChildren().add(newNote);
//                treeView.getSelectionModel().select(newNote);
//                notes.put(title, "");
//                htmlEditor.setHtmlText("");
//                htmlEditor.setVisible(true);
//                saveNoteButton.setVisible(true);
//            }
//        });
//    }
//
//    @FXML
//    private void handleLogout() throws IOException {
//        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/isna/view/login.fxml"));
//        Stage stage = (Stage) treeView.getScene().getWindow();
//        Scene scene = new Scene(loader.load());
//        stage.setScene(scene);
//        stage.show();
//    }
//
//    private void refreshTreeView() {
//        treeView.getRoot().getChildren().clear();
//        notes.forEach((title, content) -> {
//            TreeItem<String> noteItem = new TreeItem<>(title);
//            treeView.getRoot().getChildren().add(noteItem);
//        });
//    }
//
//    private void showAlert(String title, String message) {
//        Alert alert = new Alert(Alert.AlertType.INFORMATION);
//        alert.setTitle(title);
//        alert.setHeaderText(null);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }
//
//    private void confirmAndDelete(TreeItem<String> item) {
//        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
//        alert.setTitle("Confirm Delete");
//        alert.setHeaderText(null);
//        alert.setContentText("Are you sure you want to delete '" + item.getValue() + "'?");
//        Optional<ButtonType> result = alert.showAndWait();
//        if (result.isPresent() && result.get() == ButtonType.OK) {
//            item.getParent().getChildren().remove(item);
//            notes.remove(item.getValue());
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(item.getValue());
//            noteRef.removeValueAsync();
//        }
//    }
//
//    private void handleAutoSave() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null && htmlEditor.isVisible()) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(selectedNote.getValue());
//            noteRef.setValueAsync(noteContent).addListener(() -> Platform.runLater(() -> {
//                System.out.println("Auto-saved note: " + selectedNote.getValue());
//            }), Platform::runLater);
//        }
//    }
//}



//
//package com.isna.controller;
//
//import com.google.api.core.ApiFuture;
//import com.google.firebase.database.*;
//import javafx.application.Platform;
//import javafx.fxml.FXML;
//import javafx.fxml.FXMLLoader;
//import javafx.geometry.Pos;
//import javafx.scene.Scene;
//import javafx.scene.control.*;
//import javafx.scene.image.Image;
//import javafx.scene.layout.HBox;
//import javafx.scene.layout.Priority;
//import javafx.scene.web.HTMLEditor;
//import javafx.scene.control.Alert.AlertType;
//import javafx.stage.Stage;
//import javafx.scene.image.ImageView;
//
//import java.io.IOException;
//import java.util.HashMap;
//import java.util.Map;
//import java.util.Optional;
//
//public class MainController {
//    @FXML private TreeView<String> treeView;
//    @FXML private HTMLEditor htmlEditor;
//    @FXML private Button saveNoteButton;
//
//    private Map<String, String> notes = new HashMap<>();
//    private String userId;
//    private ValueEventListener noteListener;
//
//    @FXML
//    public void initialize() {
//        initializeTreeView();
//        initializeFirebaseListener();
//        htmlEditor.setVisible(false);
//        saveNoteButton.setVisible(false);
//    }
//
//    public static class FolderTreeItem extends TreeItem<String> {
//        public FolderTreeItem(String folderName) {
//            super(folderName);
//        }
//    }
//
//    private void initializeTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root", new ImageView(new Image(getClass().getResourceAsStream("/com/isna/images/folder.png"))));
//        rootItem.setExpanded(true);
//        treeView.setRoot(rootItem);
//        treeView.setShowRoot(false);
//
//        treeView.setCellFactory(tv -> new TreeCell<String>() {
//            private final Button deleteButton = new Button("Delete");
//            private final HBox hBox = new HBox();
//            private final Label label = new Label();
//
//            {
//                HBox.setHgrow(label, Priority.ALWAYS);
//                label.setMaxWidth(Double.MAX_VALUE);
//                hBox.getChildren().addAll(label, deleteButton);
//                hBox.setAlignment(Pos.CENTER_LEFT);
//            }
//
//            @Override
//            protected void updateItem(String item, boolean empty) {
//                super.updateItem(item, empty);
//                if (empty || item == null) {
//                    setText(null);
//                    setGraphic(null);
//                } else {
//                    label.setText(item);
//                    deleteButton.setOnAction(event -> {
//                        TreeItem<String> currentItem = getTreeItem();
//                        currentItem.getParent().getChildren().remove(currentItem);
//                        showAlert("Deleted", "Item deleted successfully.");
//                    });
//                    setGraphic(hBox);
//                    setText(null);
//                }
//            }
//        });
//
//        treeView.setOnMouseClicked(event -> {
//            TreeItem<String> item = treeView.getSelectionModel().getSelectedItem();
//            if (item instanceof FolderTreeItem) {
//                htmlEditor.setVisible(false);
//                saveNoteButton.setVisible(false);
//            } else if (item != null && item.isLeaf()) {
//                htmlEditor.setVisible(true);
//                saveNoteButton.setVisible(true);
//                htmlEditor.setHtmlText(notes.getOrDefault(item.getValue(), ""));
//            }
//        });
//    }
//
//    private void initializeFirebaseListener() {
//        if (userId != null && !userId.isEmpty()) {
//            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("users/" + userId + "/notes");
//            if (noteListener != null) {
//                ref.removeEventListener(noteListener);
//            }
//            noteListener = new ValueEventListener() {
//                @Override
//                public void onDataChange(DataSnapshot dataSnapshot) {
//                    notes.clear();
//                    treeView.getRoot().getChildren().clear();
//
//                    for (DataSnapshot child : dataSnapshot.getChildren()) {
//                        String noteId = child.getKey();
//                        String title = child.child("title").getValue(String.class);
//                        String content = child.child("content").getValue(String.class);
//                        notes.put(title, content);
//
//                        TreeItem<String> noteItem = new TreeItem<>(title);
//                        treeView.getRoot().getChildren().add(noteItem);
//                    }
//                }
//
//                @Override
//                public void onCancelled(DatabaseError databaseError) {
//                    System.out.println("Pembacaan gagal: " + databaseError.getCode());
//                }
//            };
//            ref.addValueEventListener(noteListener);
//        }
//    }
//
//    @FXML
//    private void handleSaveNote() {
//        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
//        if (selectedNote != null) {
//            String noteContent = htmlEditor.getHtmlText();
//            notes.put(selectedNote.getValue(), noteContent);
//
//            // Save to Firebase
//            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes/" + userId)
//                    .child(selectedNote.getValue());
//            ApiFuture<Void> future = noteRef.setValueAsync(noteContent);
//
//            future.addListener(() -> {
//                if (future.isDone()) {
//                    Platform.runLater(() -> {
//                        // Update the notes map with the newly saved content
//                        notes.put(selectedNote.getValue(), noteContent);
//
//                        // Update TreeView
//                        updateTreeView();
//
//                        // Show success message
//                        showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
//
//                        // Hide HTMLEditor and save button after saving
//                        htmlEditor.setVisible(false);
//                        saveNoteButton.setVisible(false);
//                    });
//                } else {
//                    Platform.runLater(() -> showAlert("Error", "Failed to save note."));
//                }
//            }, Platform::runLater);
//        }
//    }
//
//
//    @FXML
//    private void handleNewFolder() {
//        TreeItem<String> initialSelected = treeView.getSelectionModel().getSelectedItem();
//        TreeItem<String> selected; // Tidak perlu deklarasi sebagai final
//
//        // Cek apakah item yang dipilih adalah folder atau catatan (leaf)
//        if (initialSelected == null || initialSelected.isLeaf()) {
//            // Jika tidak ada yang dipilih atau yang dipilih adalah catatan, gunakan root
//            selected = treeView.getRoot();
//        } else {
//            // Jika yang dipilih adalah folder, gunakan itu
//            selected = initialSelected;
//        }
//
//        TextInputDialog dialog = new TextInputDialog("Folder Name");
//        dialog.setTitle("New Folder");
//        dialog.setHeaderText("Create New Folder");
//        dialog.setContentText("Enter folder name:");
//        dialog.showAndWait().ifPresent(name -> {
//            if (!name.trim().isEmpty()) {
//                FolderTreeItem newFolder = new FolderTreeItem(name);
//                selected.getChildren().add(newFolder);
//                selected.setExpanded(true);
//                treeView.getSelectionModel().select(newFolder); // Select the newly added folder
//            }
//        });
//    }
//
//
//
//
//
//    @FXML
//    private void handleNewNote() {
//        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
//        if (selectedFolder == null) {
//            showAlert("Error", "Please select a folder to add a new note.");
//            return;
//        }
//
//        if (selectedFolder instanceof FolderTreeItem) {
//            TextInputDialog dialog = new TextInputDialog("Note Title");
//            dialog.setTitle("New Note");
//            dialog.setHeaderText("Add New Note");
//            dialog.setContentText("Enter note title:");
//            dialog.showAndWait().ifPresent(title -> {
//                if (!title.trim().isEmpty() && selectedFolder.getChildren().stream().noneMatch(n -> n.getValue().equals(title))) {
//                    TreeItem<String> newNote = new TreeItem<>(title);
//                    selectedFolder.getChildren().add(newNote);
//                    selectedFolder.setExpanded(true);
//                    notes.put(title, "");  // Initialize note content in the map
//                    htmlEditor.setHtmlText("");
//                    htmlEditor.setVisible(true);
//                    saveNoteButton.setVisible(true);
//                } else {
//                    showAlert("Error", "A note with this title already exists in the folder or invalid title.");
//                }
//            });
//        } else {
//            showAlert("Error", "Please select a folder to add a new note.");
//        }
//    }
//
//    private void updateTreeView() {
//        // Clear existing TreeView items
//        treeView.getRoot().getChildren().clear();
//
//        // Re-add folders and notes to TreeView
//        for (Map.Entry<String, String> entry : notes.entrySet()) {
//            String title = entry.getKey();
//            String content = entry.getValue();
//
//            // Check if the title contains a slash ("/") indicating it's a note inside a folder
//            int slashIndex = title.indexOf('/');
//            if (slashIndex != -1) {
//                String folderName = title.substring(0, slashIndex);
//                String noteTitle = title.substring(slashIndex + 1);
//
//                // Search for the folder in the TreeView
//                TreeItem<String> folderItem = findFolderItem(folderName);
//                if (folderItem == null) {
//                    // If folder not found, create a new one
//                    folderItem = new FolderTreeItem(folderName);
//                    treeView.getRoot().getChildren().add(folderItem);
//                }
//
//                // Add the note to the folder
//                TreeItem<String> noteItem = new TreeItem<>(noteTitle);
//                folderItem.getChildren().add(noteItem);
//            } else {
//                // If it's a root note (not inside a folder), add directly to root
//                TreeItem<String> noteItem = new TreeItem<>(title);
//                treeView.getRoot().getChildren().add(noteItem);
//            }
//        }
//    }
//
//    private TreeItem<String> findFolderItem(String folderName) {
//        for (TreeItem<String> item : treeView.getRoot().getChildren()) {
//            if (item instanceof FolderTreeItem && item.getValue().equals(folderName)) {
//                return item;
//            }
//        }
//        return null;
//    }
//
//
//
//    private void showAlert(String title, String message) {
//        Alert alert = new Alert(AlertType.INFORMATION);
//        alert.setTitle(title);
//        alert.setHeaderText(null);
//        alert.setContentText(message);
//        alert.showAndWait();
//    }
//
//    @FXML
//    private void handleLogout() {
//        try {
//            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/isna/view/login.fxml"));
//            Stage stage = (Stage) treeView.getScene().getWindow();
//            Scene scene = new Scene(loader.load());
//            stage.setScene(scene);
//            stage.show();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//}
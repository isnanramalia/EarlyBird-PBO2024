package com.isna.controller;

import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.web.HTMLEditor;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
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

    // Inisialisasi komponen UI dan event handler
    @FXML
    public void initialize() {
        initializeTreeView();
        htmlEditor.setVisible(false);
        saveNoteButton.setVisible(false);

        treeView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleTreeViewDoubleClick();
            }
        });
    }

    // Mengatur ID pengguna dan menginisialisasi listener Firebase
    public void setUserId(String userId) {
        this.userId = userId;
        initializeFirebaseListener();
    }

    // Menyiapkan TreeView dengan root dan cell factory
    private void initializeTreeView() {
        TreeItem<String> rootItem = new TreeItem<>("Root"); // memubat root item
        rootItem.setExpanded(true);
        treeView.setRoot(rootItem);
        treeView.setShowRoot(false);

        treeView.setCellFactory(tv -> new TreeCell<String>() {
            private final Button deleteButton = new Button("Delete");
            private final HBox hBox = new HBox(); // kontainer horizontal
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
                if (empty || item == null) { // Memeriksa apakah item kosong
                    setText(null);
                    setGraphic(null);
                } else {
                    label.setText(item); // mengatur teks label sesuai dgn item
                    if (getTreeItem() != null && !getTreeItem().isLeaf()) {  // memeriska apkh item bkn node
                        label.setStyle("-fx-background-color: lightblue;");
                    }

                    // event handler utk tombol hapus
                    deleteButton.setOnAction(event -> {
                        TreeItem<String> currentItem = getTreeItem(); // mendapatkan item saat ini
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
                notes.clear(); // membersihkan catatan lokal
                treeView.getRoot().getChildren().clear(); // membersihkan treeview
                for (DataSnapshot noteSnapshot : dataSnapshot.getChildren()) {
                    String title = noteSnapshot.getKey(); // dpt judul note
                    if (noteSnapshot.hasChildren()) { // apkh node adl folder
                        TreeItem<String> folderItem = new TreeItem<>(title);
                        treeView.getRoot().getChildren().add(folderItem); // menambahkan sub item ke folder
                        addSubItems(folderItem, noteSnapshot);
                    } else {
                        String content = noteSnapshot.getValue(String.class); // mendapat konten note
                        notes.put(title, content);
                        TreeItem<String> noteItem = new TreeItem<>(title);
                        treeView.getRoot().getChildren().add(noteItem); // nambah catatan ke root
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Failed to read data: " + databaseError.getCode());
            }
        };
        ref.addValueEventListener(noteListener); // nambahin listener ke referensi firebase
    }

    // Menambah sub-item ke parent item di TreeView
    private void addSubItems(TreeItem<String> parent, DataSnapshot snapshot) {
        for (DataSnapshot childSnapshot : snapshot.getChildren()) {
            String childTitle = childSnapshot.getKey();
            if (childSnapshot.hasChildren()) { // apkh sub note adl folder
                TreeItem<String> subFolder = new TreeItem<>(childTitle);
                parent.getChildren().add(subFolder);
                addSubItems(subFolder, childSnapshot); // Rekursif menambah sub-item
            } else {
                String content = childSnapshot.getValue(String.class); // mendapatkan konten
                notes.put(getFullPath(parent) + "/" + childTitle, content);
                TreeItem<String> noteItem = new TreeItem<>(childTitle);
                parent.getChildren().add(noteItem);
            }
        }
    }

    @FXML
    private void handleSaveNote() {
        TreeItem<String> selectedNote = treeView.getSelectionModel().getSelectedItem();
        if (selectedNote != null && selectedNote.getParent() != null) { // apkh note valid
            String noteContent = htmlEditor.getHtmlText(); // ambil konten dr htmleditor
            notes.put(getFullPath(selectedNote), noteContent);

            // nyimpan ke firebase
            DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(selectedNote));
            noteRef.setValue(noteContent, (databaseError, databaseReference) -> {
                Platform.runLater(() -> {
                    if (databaseError == null) {
                        showAlert("Save Note", "Note '" + selectedNote.getValue() + "' successfully saved.");
                        expandToNode(selectedNote);
                    } else {
                        showAlert("Save Note", "Failed to save note: " + databaseError.getMessage());
                    }
                });
            });
        }
    }

    @FXML
    private void handleNewFolder() {
        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
        TextInputDialog dialog = new TextInputDialog("Folder Name");
        dialog.setTitle("New Folder");
        dialog.setHeaderText("Enter the name for the new folder:");
        Optional<String> result = dialog.showAndWait();
        result.ifPresent(name -> { // jika nama terisi
            if (!name.trim().isEmpty()) { // jk nama tidak kosong
                TreeItem<String> newFolder = new TreeItem<>(name);
                if (selectedItem == null || selectedItem.getValue().equals("Root")) { // jika gakda root dipilih
                    treeView.getRoot().getChildren().add(newFolder); // nambah folder di root
                } else if (!selectedItem.isLeaf() || selectedItem.getParent() != null) { // jika node (leaf)
                    selectedItem.getChildren().add(newFolder);
                    selectedItem.setExpanded(true);
                } else {
                    selectedItem.getParent().getChildren().add(newFolder);
                }
                treeView.getSelectionModel().select(newFolder);

                // Simpan folder ke Firebase
                DatabaseReference folderRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newFolder));
                folderRef.setValue(new HashMap<String, Object>(), (databaseError, databaseReference) -> {
                    if (databaseError != null) {
                        showAlert("Error", "Failed to save folder: " + databaseError.getMessage());
                    }
                });
            }
        });
    }

    @FXML
    private void handleNewNote() {
        TreeItem<String> selectedFolder = treeView.getSelectionModel().getSelectedItem();
        if (selectedFolder == null || (selectedFolder.isLeaf() && (selectedFolder.getParent() == null || selectedFolder.getParent().getValue().equals("Root")))) {
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
                    selectedFolder.getParent().getChildren().add(newNote); // nabah note ke parent dr folder
                } else {
                    selectedFolder.getChildren().add(newNote); // nmbah note ke folder yg dipilih
                }
                selectedFolder.setExpanded(true);
                treeView.getSelectionModel().select(newNote); // memilih note baru
                notes.put(getFullPath(newNote), "");

                htmlEditor.setHtmlText("");
                htmlEditor.setVisible(true);
                saveNoteButton.setVisible(true);

                // Simpan catatan ke Firebase
                DatabaseReference noteRef = FirebaseDatabase.getInstance().getReference("notes").child(userId).child(getFullPath(newNote));
                noteRef.setValue("", (databaseError, databaseReference) -> {
                    if (databaseError != null) {
                        showAlert("Error", "Failed to save note: " + databaseError.getMessage());
                    }
                });
            }
        });
    }

    private void handleTreeViewDoubleClick() {
        TreeItem<String> selectedItem = treeView.getSelectionModel().getSelectedItem();
        if (selectedItem != null && !selectedItem.getValue().equals("Root")) {
            String fullPath = getFullPath(selectedItem);
            if (notes.containsKey(fullPath)) { // apkh note sdh ada di treeview
                htmlEditor.setHtmlText(notes.get(fullPath));
                htmlEditor.setVisible(true);
                saveNoteButton.setVisible(true);
            } else {
                htmlEditor.setHtmlText("");
                htmlEditor.setVisible(false);
                saveNoteButton.setVisible(false);
            }
        } else {
            htmlEditor.setHtmlText("");
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

//    private void refreshTreeView() {
//        TreeItem<String> rootItem = new TreeItem<>("Root");
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

//    private TreeItem<String> findOrCreateFolder(TreeItem<String> root, String folderName) {
//        for (TreeItem<String> child : root.getChildren()) {
//            if (child.getValue().equals(folderName)) {
//                return child;
//            }
//        }
//        TreeItem<String> newFolder = new TreeItem<>(folderName);
//        root.getChildren().add(newFolder);
//        return newFolder;
//    }

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

    private void expandToNode(TreeItem<String> item) {
        if (item != null) {
            TreeItem<String> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
        }
    }
}
//package com.isna.service;
//
//import com.google.firebase.database.DatabaseReference;
//import com.isna.model.Note;
//import com.google.firebase.database.FirebaseDatabase;
//
//public class FirebaseNoteService {
//    private FirebaseDatabase firebaseDatabase;
//
//    public FirebaseNoteService() {
//        this.firebaseDatabase = FirebaseDatabase.getInstance();
//    }
//
//    public void saveNote(Note note, String userId) {
//        DatabaseReference notesRef = firebaseDatabase.getReference("users").child(userId).child("notes");
//        DatabaseReference newNoteRef = notesRef.push();
//        newNoteRef.setValueAsync(note);
//    }
//
//    public void deleteNote(String noteId, String userId) {
//        // Hapus catatan dari folder pengguna berdasarkan userId dan noteId
//        firebaseDatabase.getReference("users").child(userId).child("notes").child(noteId).removeValueAsync();
//    }
//}

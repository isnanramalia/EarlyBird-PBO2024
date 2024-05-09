package com.isna.service;

import com.google.firebase.database.*;
import com.isna.model.User;
import org.mindrot.jbcrypt.BCrypt;

public class UserManager {

    public interface Callback<T> {
        void onCallback(T result);
    }

    private static final DatabaseReference USERS_REF = FirebaseDatabase.getInstance().getReference("users");

    public static void registerUser(String email, String password, String fullName, String phoneNumber, UserManager.Callback<Boolean> callback) {
        email = email.toLowerCase().trim();  // Pastikan email disimpan dalam lowercase dan tanpa spasi berlebih
        Query query = USERS_REF.orderByChild("email").equalTo(email);
        String finalEmail = email;
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    callback.onCallback(false);
                } else {
                    String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
                    User newUser = new User(finalEmail, hashedPassword, fullName, phoneNumber);
                    USERS_REF.push().setValue(newUser, (databaseError, databaseReference) -> {
                        callback.onCallback(databaseError == null);
                    });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Kesalahan database: " + databaseError.getMessage());
                callback.onCallback(false);
            }
        });
    }
    public static void authenticateUser(String email, String password, UserManager.Callback<User> callback) {
        email = email.toLowerCase().trim();  // Pastikan email dalam lowercase untuk pencocokan
        Query query = USERS_REF.orderByChild("email").equalTo(email);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                        User user = child.getValue(User.class);
                        if (user != null && BCrypt.checkpw(password, user.getPassword())) {
                            callback.onCallback(user);
                            return;
                        }
                    }
                }
                callback.onCallback(null);
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                System.err.println("Kesalahan database: " + databaseError.getMessage());
                callback.onCallback(null);
            }
        });
    }
}

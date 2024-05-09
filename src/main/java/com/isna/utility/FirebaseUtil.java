package com.isna.utility;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.FileInputStream;
import java.io.IOException;

public class FirebaseUtil {
    private static final String FIREBASE_KEY_PATH = "C:/Users/isnan/IdeaProjects/notetaking-10055-firebase-adminsdk-k9cgm-13794111e0.json";

    static {
        initializeFirebase();
    }

    public static void initializeFirebase() {
        try {
            FileInputStream serviceAccount = new FileInputStream(FIREBASE_KEY_PATH);
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://notetakingjava-default-rtdb.firebaseio.com")
                    .build();

            if (FirebaseApp.getApps().isEmpty()) { // Prevent re-initializing
                FirebaseApp.initializeApp(options);
            }
        } catch (IOException e) {
            throw new RuntimeException("Firebase initialization failed.", e);
        }
    }
}
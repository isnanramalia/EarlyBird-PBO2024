package com.isna.model;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private String email;
    private String password; // Passwords should be hashed in production applications
    private String fullName;
    private String phoneNumber;

    // Konstruktor tanpa argumen diperlukan untuk deserialisasi data Firebase
    public User() {}

    public User(String email, String password, String fullName, String phoneNumber) {
        setEmail(email);  // Gunakan setter untuk memastikan email selalu dalam lowercase
        this.password = password; // Hash password in real-world app
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
    }

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email.toLowerCase(); } // Pastikan email dalam lowercase
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
}

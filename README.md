# Aplikasi Note-Taking

Aplikasi ini adalah aplikasi pencatatan untuk menyimpan dan mengelola catatan pribadi dengan format teks seperti Rich Text atau Markdown. Catatan disimpan di server, memungkinkan akses mudah setelah login.

## Fitur

- **Autentikasi Pengguna**: Pengguna harus mendaftar atau login. Formulir registrasi memiliki field: email, password, nama lengkap, dan nomor HP. Formulir login hanya membutuhkan email dan password.
- **Antarmuka Utama**: Setelah login, tampil halaman utama dengan **Daftar Catatan** (TreeView) dan **Penampil Catatan**.
- **Manajemen Catatan**: Bisa membuat catatan dan folder baru. Catatan dapat di-edit langsung (mode interaktif) atau dengan mode terpisah (edit & tampil).
- **Penyimpanan Online**: Catatan disimpan di layanan backend seperti Firebase, Supabase, atau backend yang di-hosting.
- **Privasi Pengguna**: Setiap pengguna hanya bisa melihat catatan miliknya sendiri.

## Tech

- **Frontend**: JavaFX (FXML)
- **Backend**: Java, Firebase

## Unduh

Berikut langkah untuk menjalankan aplikasi Java di IntelliJ IDEA:

1. **Buka Project**: Buka project Java di IntelliJ IDEA.
2. **Pastikan Ada `main()` Method**: Buat atau pastikan ada class Java dengan metode `main()`. 
3. **Konfigurasi Jalankan**:
   - Klik `Edit Configurations...` di toolbar.
   - Tambah konfigurasi baru `Application`.
   - Pilih `Main class` yang ada metode `main()`.
   - Simpan.
4. **Jalankan Aplikasi**:
   - Klik ikon **Run** (segitiga hijau) di toolbar atau tekan `Shift + F10`.
5. **Lihat Output di Console**: Output akan muncul di jendela console di bagian bawah.

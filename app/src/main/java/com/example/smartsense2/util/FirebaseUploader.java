package com.example.smartsense2.util;

import android.net.Uri;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

public final class FirebaseUploader {

    public interface OkCallback { void run(); }
    public interface ErrCallback { void run(String err); }

    private FirebaseUploader() {}

    public static void uploadCsv(Uri fileUri, String filename, OkCallback ok, ErrCallback err) {
        StorageReference root = FirebaseStorage.getInstance().getReference();
        StorageReference ref = root.child("csv/" + filename);

        ref.putFile(fileUri)
                .addOnSuccessListener(taskSnapshot -> ok.run())
                .addOnFailureListener(e -> err.run(e.getMessage()));
    }
}

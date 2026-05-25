package com.gg_tech_bharat.gdialer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class EditContactActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1002;

    private EditText etName, etPhone, etNotes;
    private ImageView ivAvatar;
    private View layoutPhotoPicker;
    private TextView btnCancel, btnSave;

    private int contactId = -1;
    private String photoUriString = "";
    private ContactModel currentContact;
    private AppDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_contact);

        database = AppDatabase.getDatabase(this);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etNotes = findViewById(R.id.etNotes);
        ivAvatar = findViewById(R.id.ivEditAvatar);
        layoutPhotoPicker = findViewById(R.id.layoutPhotoPicker);
        btnCancel = findViewById(R.id.btnCancel);
        btnSave = findViewById(R.id.btnSave);

        // Retrieve params
        contactId = getIntent().getIntExtra("EXTRA_CONTACT_ID", -1);
        String initialNumber = getIntent().getStringExtra("EXTRA_NUMBER");

        if (initialNumber != null && !initialNumber.isEmpty()) {
            etPhone.setText(initialNumber);
        }

        if (contactId != -1) {
            // Edit existing contact
            loadContactDetails();
        }

        // Action Listeners
        btnCancel.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveContact());
        layoutPhotoPicker.setOnClickListener(v -> openImagePicker());
    }

    private void loadContactDetails() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            currentContact = database.contactDao().getContactById(contactId);

            if (currentContact != null) {
                runOnUiThread(() -> {
                    etName.setText(currentContact.getName());
                    etPhone.setText(currentContact.getNumber());
                    etNotes.setText(currentContact.getNotes());
                    photoUriString = currentContact.getPhotoUri();
                    Utils.loadContactPhoto(this, photoUriString, ivAvatar);
                });
            }
        });
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Select Profile Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri selectedImageUri = data.getData();
            saveProfileImageToInternalStorage(selectedImageUri);
        }
    }

    private void saveProfileImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;

            File dir = new File(getFilesDir(), "profile_pics");
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(dir, "contact_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(file);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.close();
            inputStream.close();

            photoUriString = file.getAbsolutePath();
            Utils.loadContactPhoto(this, photoUriString, ivAvatar);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to load image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (phone.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (currentContact == null) {
                // New Contact
                ContactModel newContact = new ContactModel(name, phone, photoUriString, false, false, notes);
                database.contactDao().insert(newContact);
            } else {
                // Update Contact
                currentContact.setName(name);
                currentContact.setNumber(phone);
                currentContact.setNotes(notes);
                currentContact.setPhotoUri(photoUriString);
                database.contactDao().update(currentContact);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}

package com.gg_tech_bharat.gdialer;

import android.content.ContentProviderOperation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class EditContactActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1002;
    private static final int CROP_IMAGE_REQUEST = 1004;

    private EditText etName, etPhone, etNotes;
    private ImageView ivAvatar;
    private View layoutPhotoPicker;
    private TextView btnCancel, btnSave;

    private int contactId = -1;
    private String photoUriString = "";
    private Uri sourceImageUri = null;
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

        contactId = getIntent().getIntExtra("EXTRA_CONTACT_ID", -1);
        String initialNumber = getIntent().getStringExtra("EXTRA_NUMBER");

        if (initialNumber != null && !initialNumber.isEmpty()) {
            etPhone.setText(initialNumber);
        }

        if (contactId != -1) {
            loadContactDetails();
        }

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
        if (resultCode == RESULT_OK) {
            if (requestCode == PICK_IMAGE_REQUEST && data != null && data.getData() != null) {
                sourceImageUri = data.getData();
                startCrop(sourceImageUri);
            } else if (requestCode == CROP_IMAGE_REQUEST) {
                Utils.loadContactPhoto(this, photoUriString, ivAvatar);
            }
        }
    }

    private void startCrop(Uri uri) {
        try {
            File dir = new File(getFilesDir(), "profile_pics");
            if (!dir.exists()) dir.mkdirs();
            File cropFile = new File(dir, "crop_" + System.currentTimeMillis() + ".jpg");
            photoUriString = cropFile.getAbsolutePath();
            Uri destinationUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cropFile);

            Intent intent = new Intent("com.android.camera.action.CROP");
            intent.setDataAndType(uri, "image/*");
            intent.putExtra("crop", "true");
            intent.putExtra("aspectX", 1);
            intent.putExtra("aspectY", 1);
            intent.putExtra("outputX", 512);
            intent.putExtra("outputY", 512);
            intent.putExtra("scale", true);
            intent.putExtra("return-data", false);
            intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, destinationUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            java.util.List<android.content.pm.ResolveInfo> resInfoList = getPackageManager().queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            for (android.content.pm.ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, destinationUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }

            startActivityForResult(intent, CROP_IMAGE_REQUEST);
        } catch (Exception e) {
            Log.e("EditContactActivity", "Crop error", e);
            saveProfileImageToInternalStorage(uri);
        }
    }

    private void saveProfileImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return;
            File dir = new File(getFilesDir(), "profile_pics");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "contact_" + System.currentTimeMillis() + ".jpg");
            FileOutputStream outputStream = new FileOutputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) outputStream.write(buffer, 0, bytesRead);
            outputStream.close();
            inputStream.close();
            photoUriString = file.getAbsolutePath();
            Utils.loadContactPhoto(this, photoUriString, ivAvatar);
        } catch (Exception e) {
            Toast.makeText(this, "Image load failed", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveContact() {
        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String notes = etNotes.getText().toString().trim();

        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "Name and primary Phone required", Toast.LENGTH_SHORT).show();
            return;
        }

        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.GET_ACCOUNTS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{
                    android.Manifest.permission.WRITE_CONTACTS, 
                    android.Manifest.permission.GET_ACCOUNTS
            }, 1003);
            return;
        }

        performSaveSync(name, phone, notes);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1003) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                saveContact();
            } else {
                Toast.makeText(this, "Permission required to sync with Google", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void performSaveSync(String name, String phone, String notes) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Aggressive Sync to Google Account
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();
            android.accounts.Account[] googleAccounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
            android.accounts.Account targetAccount = (googleAccounts.length > 0) ? googleAccounts[0] : null;
            
            String accountName = (targetAccount != null) ? targetAccount.name : null;
            String accountType = (targetAccount != null) ? targetAccount.type : null;

            int rawIndex = ops.size();
            ops.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accountType)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accountName)
                    .withValue(ContactsContract.RawContacts.DIRTY, 1) // Mark as changed for sync
                    .build());

            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(android.provider.ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name).build());

            // Primary Phone
            ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                    .withValue(android.provider.ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                    .withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE).build());

            // Sync Photo
            if (photoUriString != null && !photoUriString.isEmpty()) {
                try {
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(photoUriString);
                    if (bitmap != null) {
                        java.io.ByteArrayOutputStream stream = new java.io.ByteArrayOutputStream();
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream);
                        byte[] photoBytes = stream.toByteArray();
                        ops.add(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                                .withValue(android.provider.ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes).build());
                    }
                } catch (Exception e) { Log.e("EditContactActivity", "Photo sync failed", e); }
            }

            try {
                getContentResolver().applyBatch(ContactsContract.AUTHORITY, ops);
                
                // AGGRESSIVE CLOUD SYNC: Request sync for all Google accounts
                android.accounts.Account[] accounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                for (android.accounts.Account account : accounts) {
                    android.os.Bundle extras = new android.os.Bundle();
                    extras.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
                    extras.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                    android.content.ContentResolver.requestSync(account, ContactsContract.AUTHORITY, extras);
                }
            } catch (Exception e) { Log.e("EditContactActivity", "Sync failed", e); }

            // Local DB save
            if (currentContact == null) {
                database.contactDao().insert(new ContactModel(name, phone, photoUriString, false, false, notes));
            } else {
                currentContact.setName(name);
                currentContact.setNumber(phone);
                currentContact.setNotes(notes);
                currentContact.setPhotoUri(photoUriString);
                database.contactDao().update(currentContact);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Contact saved and synced to Google", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}

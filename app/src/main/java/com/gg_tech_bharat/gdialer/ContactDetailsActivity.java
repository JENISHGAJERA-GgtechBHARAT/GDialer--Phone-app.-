package com.gg_tech_bharat.gdialer;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.List;

public class ContactDetailsActivity extends AppCompatActivity {

    private ImageButton btnBack, btnFavoriteToggle;
    private ShapeableImageView ivAvatar;
    private TextView tvName, tvNumber;
    private View layoutCall, layoutMessage, layoutWhatsApp;
    private View layoutBottomEdit, layoutBottomBlock, layoutBottomDelete;
    private ImageView ivBottomBlockIcon;
    private TextView tvBottomBlockText;
    private RecyclerView rvHistory;

    private String phoneNumber;
    private ContactModel currentContact;
    private AppDatabase database;
    private RecentAdapter historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_details);

        database = AppDatabase.getDatabase(this);

        phoneNumber = getIntent().getStringExtra("EXTRA_NUMBER");
        if (phoneNumber == null) {
            phoneNumber = "+91 98765 43210";
        }

        // Bind views
        btnBack = findViewById(R.id.btnBack);
        btnFavoriteToggle = findViewById(R.id.btnFavoriteToggle);
        ivAvatar = findViewById(R.id.ivDetailAvatar);
        tvName = findViewById(R.id.tvDetailName);
        tvNumber = findViewById(R.id.tvDetailNumber);
        layoutCall = findViewById(R.id.layoutCall);
        layoutMessage = findViewById(R.id.layoutMessage);
        layoutWhatsApp = findViewById(R.id.layoutWhatsApp);
        rvHistory = findViewById(R.id.rvDetailCallHistory);

        layoutBottomEdit = findViewById(R.id.layoutBottomEdit);
        layoutBottomBlock = findViewById(R.id.layoutBottomBlock);
        layoutBottomDelete = findViewById(R.id.layoutBottomDelete);
        ivBottomBlockIcon = findViewById(R.id.ivBottomBlockIcon);
        tvBottomBlockText = findViewById(R.id.tvBottomBlockText);

        // Configure history recycler
        rvHistory.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new RecentAdapter(this);
        rvHistory.setAdapter(historyAdapter);

        // Listeners
        btnBack.setOnClickListener(v -> finish());
        btnFavoriteToggle.setOnClickListener(v -> toggleFavorite());
        layoutCall.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Utils.makePhoneCall(this, phoneNumber);
        });
        layoutMessage.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Utils.sendSMS(this, phoneNumber, "");
        });
        layoutWhatsApp.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Utils.openWhatsApp(this, phoneNumber);
        });

        layoutBottomEdit.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            Intent intent = new Intent(this, EditContactActivity.class);
            if (currentContact != null) {
                intent.putExtra("EXTRA_CONTACT_ID", currentContact.getId());
            } else {
                intent.putExtra("EXTRA_NUMBER", phoneNumber);
            }
            startActivity(intent);
        });

        layoutBottomBlock.setOnClickListener(v -> toggleBlock());
        layoutBottomDelete.setOnClickListener(v -> deleteContact());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDetails();
        checkBlockState();
    }

    private void loadDetails() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Find contact
            currentContact = database.contactDao().getContactByNumber(phoneNumber);

            runOnUiThread(() -> {
                if (currentContact != null) {
                    tvName.setText(currentContact.getName());
                    tvNumber.setText(currentContact.getNumber());
                    updateFavoriteIcon(currentContact.isFavorite());
                    Utils.loadContactPhoto(this, currentContact.getPhotoUri(), ivAvatar);
                } else {
                    // Temporary contact wrapper
                    tvName.setText("Unknown Contact");
                    tvNumber.setText(phoneNumber);
                    updateFavoriteIcon(false);
                    Utils.loadContactPhoto(this, "", ivAvatar);
                }
            });

            // Load call log history specifically for this number
            runOnUiThread(() -> {
                database.recentDao().getCallHistoryForNumber(phoneNumber).observe(this, recents -> {
                    if (recents != null) {
                        historyAdapter.setRecents(recents);
                    }
                });
            });
        });
    }

    private void checkBlockState() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BlockedNumber blocked = database.blockedNumberDao().getBlockedByNumber(phoneNumber);
            runOnUiThread(() -> {
                boolean isBlocked = blocked != null;
                ivBottomBlockIcon.setImageResource(R.drawable.ic_info);
                ivBottomBlockIcon.setColorFilter(getResources().getColor(isBlocked ? R.color.accent_red : R.color.white));
                tvBottomBlockText.setText(isBlocked ? "Unblock" : "Block");
                tvBottomBlockText.setTextColor(getResources().getColor(isBlocked ? R.color.accent_red : R.color.white));
            });
        });
    }

    private void toggleBlock() {
        Utils.triggerHaptic(layoutBottomBlock);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            BlockedNumber blocked = database.blockedNumberDao().getBlockedByNumber(phoneNumber);
            if (blocked != null) {
                // Unblock
                database.blockedNumberDao().delete(blocked);
                if (currentContact != null) {
                    currentContact.setSpam(false);
                    database.contactDao().update(currentContact);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "Number unblocked", Toast.LENGTH_SHORT).show();
                    checkBlockState();
                });
            } else {
                // Block
                database.blockedNumberDao().insert(new BlockedNumber(phoneNumber));
                if (currentContact != null) {
                    currentContact.setSpam(true);
                    database.contactDao().update(currentContact);
                }
                runOnUiThread(() -> {
                    Toast.makeText(this, "Number blocked", Toast.LENGTH_SHORT).show();
                    checkBlockState();
                });
            }
        });
    }

    private void deleteContact() {
        if (currentContact == null) {
            Toast.makeText(this, "Unknown contact cannot be deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete " + currentContact.getName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        // 1. DELETE FROM GOOGLE / SYSTEM CONTACTS
                        try {
                            android.net.Uri contactUri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(phoneNumber));
                            String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup._ID, android.provider.ContactsContract.PhoneLookup.LOOKUP_KEY};
                            try (android.database.Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
                                if (cursor != null) {
                                    while (cursor.moveToNext()) {
                                        String lookupKey = cursor.getString(1);
                                        android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                                        getContentResolver().delete(uri, null, null);
                                        Log.d("ContactDetails", "System contact deleted: " + lookupKey);
                                    }
                                }
                            }
                        } catch (Exception e) { Log.e("ContactDetails", "System deletion failed", e); }

                        // 2. DELETE FROM LOCAL DATABASE
                        database.contactDao().delete(currentContact);

                        // 3. FORCE GOOGLE SYNC TRIGGER
                        android.accounts.Account[] accounts = android.accounts.AccountManager.get(this).getAccountsByType("com.google");
                        for (android.accounts.Account account : accounts) {
                            android.os.Bundle extras = new android.os.Bundle();
                            extras.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true);
                            extras.putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                            android.content.ContentResolver.requestSync(account, android.provider.ContactsContract.AUTHORITY, extras);
                        }

                        runOnUiThread(() -> {
                            Toast.makeText(this, "Contact deleted and synced with Google", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toggleFavorite() {
        if (currentContact == null) {
            Toast.makeText(this, "Save this number to contacts first", Toast.LENGTH_SHORT).show();
            return;
        }

        Utils.triggerHaptic(btnFavoriteToggle);
        final boolean newFavState = !currentContact.isFavorite();
        currentContact.setFavorite(newFavState);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.contactDao().update(currentContact);
            runOnUiThread(() -> {
                updateFavoriteIcon(newFavState);
                Toast.makeText(ContactDetailsActivity.this,
                        newFavState ? "Added to Favorites" : "Removed from Favorites",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void updateFavoriteIcon(boolean isFav) {
        btnFavoriteToggle.setImageResource(isFav ? R.drawable.ic_star : R.drawable.ic_star_border);
        btnFavoriteToggle.setColorFilter(getResources().getColor(isFav ? R.color.accent_green : R.color.white));
    }
}

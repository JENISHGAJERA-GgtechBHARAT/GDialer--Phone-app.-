package com.gg_tech_bharat.gdialer;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class SpeedDialActivity extends AppCompatActivity {

    private LinearLayout container;
    private SharedPreferences prefs;
    private AppDatabase database;
    private List<ContactModel> allContacts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_dial);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Speed Dial Settings");
        }

        container = findViewById(R.id.layoutSpeedDialContainer);
        prefs = getSharedPreferences("SpeedDialPrefs", Context.MODE_PRIVATE);
        database = AppDatabase.getDatabase(this);

        // Load contacts from DB
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> contacts = database.contactDao().searchContactsWithRanking("%%", "%%");
            if (contacts != null) {
                allContacts.addAll(contacts);
            }
            runOnUiThread(this::refreshList);
        });
    }

    private void refreshList() {
        container.removeAllViews();

        for (int i = 2; i <= 9; i++) {
            final int key = i;
            String assignment = prefs.getString("key_" + key, null); // Format: "Name|Number"

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(16, 24, 16, 24);
            
            // Set row background with thin outline
            row.setBackgroundResource(R.drawable.glass_card);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, 8, 0, 8);
            row.setLayoutParams(rowParams);

            // Digit Indicator
            TextView tvDigit = new TextView(this);
            tvDigit.setText(String.valueOf(key));
            tvDigit.setTextColor(getResources().getColor(R.color.accent_green));
            tvDigit.setTextSize(sp24ToFloat());
            tvDigit.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams digitParams = new LinearLayout.LayoutParams(60, ViewGroup.LayoutParams.WRAP_CONTENT);
            digitParams.setMargins(0, 0, 16, 0);
            tvDigit.setLayoutParams(digitParams);
            row.addView(tvDigit);

            // Info Area
            LinearLayout infoLayout = new LinearLayout(this);
            infoLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            infoLayout.setLayoutParams(infoParams);

            TextView tvName = new TextView(this);
            TextView tvNum = new TextView(this);

            if (assignment != null) {
                String[] parts = assignment.split("\\|");
                String name = parts[0];
                String num = parts.length > 1 ? parts[1] : "";
                
                tvName.setText(name);
                tvName.setTextColor(Color.WHITE);
                tvName.setTextSize(sp16ToFloat());
                
                tvNum.setText(num);
                tvNum.setTextColor(getResources().getColor(R.color.text_secondary));
                tvNum.setTextSize(sp13ToFloat());
            } else {
                tvName.setText("Not assigned");
                tvName.setTextColor(getResources().getColor(R.color.text_secondary));
                tvName.setTextSize(sp16ToFloat());
                
                tvNum.setText("Tap to set speed dial shortcut");
                tvNum.setTextColor(getResources().getColor(R.color.text_secondary));
                tvNum.setTextSize(sp12ToFloat());
            }

            infoLayout.addView(tvName);
            infoLayout.addView(tvNum);
            row.addView(infoLayout);

            // Row click: Select contact
            row.setOnClickListener(v -> showContactSelectionDialog(key));

            // Clear Button
            if (assignment != null) {
                ImageButton btnClear = new ImageButton(this);
                btnClear.setImageResource(R.drawable.ic_backspace);
                btnClear.setBackgroundColor(Color.TRANSPARENT);
                btnClear.setColorFilter(Color.RED);
                btnClear.setPadding(8, 8, 8, 8);
                btnClear.setOnClickListener(v -> {
                    prefs.edit().remove("key_" + key).apply();
                    Toast.makeText(this, "Cleared Speed Dial " + key, Toast.LENGTH_SHORT).show();
                    refreshList();
                });
                row.addView(btnClear);
            }

            container.addView(row);
        }
    }

    private void showContactSelectionDialog(int key) {
        if (allContacts.isEmpty()) {
            Toast.makeText(this, "No contacts found to assign. Syncing details...", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] contactNames = new String[allContacts.size()];
        for (int i = 0; i < allContacts.size(); i++) {
            contactNames[i] = allContacts.get(i).getName() + " (" + allContacts.get(i).getNumber() + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Assign Speed Dial " + key)
                .setItems(contactNames, (dialog, which) -> {
                    ContactModel contact = allContacts.get(which);
                    String val = contact.getName() + "|" + contact.getNumber();
                    prefs.edit().putString("key_" + key, val).apply();
                    Toast.makeText(this, contact.getName() + " assigned to Speed Dial " + key, Toast.LENGTH_SHORT).show();
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private float spToFloat() {
        return 12f;
    }

    private float spToFloat2() {
        return 16f;
    }

    private float sp13ToFloat() { return 13f; }
    private float sp12ToFloat() { return 12f; }
    private float sp16ToFloat() { return 16f; }
    private float sp24ToFloat() { return 24f; }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}

package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CallRecordingSettingsActivity extends AppCompatActivity {

    private SwitchMaterial switchAutoRecord;
    private RadioGroup radioGroupRecordMode;
    private RadioButton radioRecordAll, radioRecordUnsaved, radioRecordSelected;
    private LinearLayout layoutOptionsContainer, layoutSelectedNumbers;
    private TextView tvSelectedNumbersCount;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        prefs = getSharedPreferences("DialerPrefs", MODE_PRIVATE);
        boolean useSystem = prefs.getBoolean("use_system_theme", true);
        if (useSystem) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean darkMode = prefs.getBoolean("dark_mode", true);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_recording_settings);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        switchAutoRecord = findViewById(R.id.switchAutoRecord);
        radioGroupRecordMode = findViewById(R.id.radioGroupRecordMode);
        radioRecordAll = findViewById(R.id.radioRecordAll);
        radioRecordUnsaved = findViewById(R.id.radioRecordUnsaved);
        radioRecordSelected = findViewById(R.id.radioRecordSelected);
        layoutOptionsContainer = findViewById(R.id.layoutOptionsContainer);
        layoutSelectedNumbers = findViewById(R.id.layoutSelectedNumbers);
        tvSelectedNumbersCount = findViewById(R.id.tvSelectedNumbersCount);

        // Load saved preferences
        boolean autoRecordEnabled = prefs.getBoolean("auto_record_enabled", false);
        String mode = prefs.getString("auto_record_mode", "all");

        if (switchAutoRecord != null) {
            switchAutoRecord.setChecked(autoRecordEnabled);
        }
        if (layoutOptionsContainer != null) {
            layoutOptionsContainer.setVisibility(autoRecordEnabled ? View.VISIBLE : View.GONE);
        }

        if (radioRecordAll != null && "all".equals(mode)) {
            radioRecordAll.setChecked(true);
        } else if (radioRecordUnsaved != null && "unsaved".equals(mode)) {
            radioRecordUnsaved.setChecked(true);
        } else if (radioRecordSelected != null && "selected".equals(mode)) {
            radioRecordSelected.setChecked(true);
        }

        updateSelectedNumbersCount();

        // Switch listener
        if (switchAutoRecord != null) {
            switchAutoRecord.setOnCheckedChangeListener((buttonView, isChecked) -> {
                Utils.triggerHaptic(buttonView);
                if (isChecked) {
                    boolean consentGiven = prefs.getBoolean("auto_record_consent_given", false);
                    if (!consentGiven) {
                        new AlertDialog.Builder(CallRecordingSettingsActivity.this, R.style.SamsungCustomDialog)
                            .setTitle("Call Recording Consent")
                            .setMessage("Recording telephone conversations may be subject to local, state, and federal laws. By enabling this feature, you agree that you have obtained consent from all parties involved if required by law, and you accept all legal responsibilities.")
                            .setPositiveButton("I Agree", (dialog, which) -> {
                                prefs.edit().putBoolean("auto_record_consent_given", true).apply();
                                prefs.edit().putBoolean("auto_record_enabled", true).apply();
                                if (layoutOptionsContainer != null) {
                                    layoutOptionsContainer.setVisibility(View.VISIBLE);
                                }
                                checkAndPromptBatteryOptimization();
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> {
                                switchAutoRecord.setChecked(false);
                            })
                            .setOnCancelListener(dialog -> {
                                switchAutoRecord.setChecked(false);
                            })
                            .show();
                    } else {
                        prefs.edit().putBoolean("auto_record_enabled", true).apply();
                        if (layoutOptionsContainer != null) {
                            layoutOptionsContainer.setVisibility(View.VISIBLE);
                        }
                        checkAndPromptBatteryOptimization();
                    }
                } else {
                    prefs.edit().putBoolean("auto_record_enabled", false).apply();
                    if (layoutOptionsContainer != null) {
                        layoutOptionsContainer.setVisibility(View.GONE);
                    }
                }
            });
        }

        // Radio group listener
        if (radioGroupRecordMode != null) {
            radioGroupRecordMode.setOnCheckedChangeListener((group, checkedId) -> {
                Utils.triggerHaptic(group);
                String selectedMode = "all";
                if (checkedId == R.id.radioRecordAll) {
                    selectedMode = "all";
                } else if (checkedId == R.id.radioRecordUnsaved) {
                    selectedMode = "unsaved";
                } else if (checkedId == R.id.radioRecordSelected) {
                    selectedMode = "selected";
                }
                prefs.edit().putString("auto_record_mode", selectedMode).apply();
            });
        }

        // Selected numbers click listener
        if (layoutSelectedNumbers != null) {
            layoutSelectedNumbers.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                showSelectedNumbersDialog();
            });
        }
    }

    private void updateSelectedNumbersCount() {
        if (tvSelectedNumbersCount == null) return;
        Set<String> selectedNumbers = prefs.getStringSet("auto_record_selected_numbers", new HashSet<>());
        tvSelectedNumbersCount.setText(selectedNumbers.size() + " numbers");
    }

    private void showSelectedNumbersDialog() {
        Set<String> selectedNumbersSet = prefs.getStringSet("auto_record_selected_numbers", new HashSet<>());
        final List<String> numbersList = new ArrayList<>(selectedNumbersSet);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.SamsungCustomDialog);
        builder.setTitle("Selected numbers");

        if (numbersList.isEmpty()) {
            builder.setMessage("No selected numbers. Add numbers to record automatically.");
        } else {
            String[] items = new String[numbersList.size()];
            for (int i = 0; i < numbersList.size(); i++) {
                items[i] = numbersList.get(i);
            }
            builder.setItems(items, (dialog, which) -> {
                showConfirmDeleteDialog(numbersList.get(which));
            });
        }

        builder.setPositiveButton("Add", (dialog, which) -> {
            showAddNumberDialog();
        });
        builder.setNegativeButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.text_primary));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.text_primary));
        });
        dialog.show();
    }

    private void showAddNumberDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_message, null);
        final EditText editText = dialogView.findViewById(R.id.etCustomMessage);
        if (editText != null) {
            editText.setHint("Add phone number");
            editText.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        }

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                .setTitle("Add number")
                .setView(dialogView)
                .setPositiveButton("Add", (d, which) -> {
                    if (editText != null) {
                        String number = editText.getText().toString().trim();
                        if (!number.isEmpty()) {
                            Set<String> selectedNumbers = new HashSet<>(prefs.getStringSet("auto_record_selected_numbers", new HashSet<>()));
                            selectedNumbers.add(number);
                            prefs.edit().putStringSet("auto_record_selected_numbers", selectedNumbers).apply();
                            updateSelectedNumbersCount();
                            showSelectedNumbersDialog(); 
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.text_primary));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.text_primary));
        });
        dialog.show();
    }

    private void showConfirmDeleteDialog(String number) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                .setTitle("Remove number")
                .setMessage("Stop auto recording calls for " + number + "?")
                .setPositiveButton("Remove", (d, which) -> {
                    Set<String> selectedNumbers = new HashSet<>(prefs.getStringSet("auto_record_selected_numbers", new HashSet<>()));
                    selectedNumbers.remove(number);
                    prefs.edit().putStringSet("auto_record_selected_numbers", selectedNumbers).apply();
                    updateSelectedNumbersCount();
                    showSelectedNumbersDialog(); 
                })
                .setNegativeButton("Cancel", (d, which) -> showSelectedNumbersDialog())
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.text_primary));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.text_primary));
        });
        dialog.show();
    }

    private void checkAndPromptBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                    .setTitle("Battery Optimization")
                    .setMessage("To ensure call recording works reliably in the background, please disable battery optimization for GDialer.")
                    .setPositiveButton("Disable", (dialog, which) -> {
                        try {
                            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            android.util.Log.e("CallRecordingSettings", "Failed to open ignore battery optimization intent", e);
                        }
                    })
                    .setNegativeButton("Not Now", null)
                    .show();
            }
        }
    }
}

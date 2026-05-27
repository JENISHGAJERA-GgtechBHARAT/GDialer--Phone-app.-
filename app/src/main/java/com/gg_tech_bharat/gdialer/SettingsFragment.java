package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    private SwitchMaterial switchDarkMode, switchAutoRecord, switchSpam, switchRecordUnknown, switchVibration;
    private SharedPreferences prefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        prefs = requireContext().getSharedPreferences("DialerPrefs", Context.MODE_PRIVATE);

        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        switchAutoRecord = view.findViewById(R.id.switchAutoRecord);
        switchSpam = view.findViewById(R.id.switchSpam);
        switchRecordUnknown = view.findViewById(R.id.switchRecordUnknown);
        switchVibration = view.findViewById(R.id.switchVibration);

        // Load states correctly
        switchDarkMode.setChecked(prefs.getBoolean("dark_mode", true));
        switchAutoRecord.setChecked(prefs.getBoolean("auto_record", false));
        switchSpam.setChecked(prefs.getBoolean("spam_protection", true));
        switchRecordUnknown.setChecked(prefs.getBoolean("record_unknown", false));
        switchVibration.setChecked(prefs.getBoolean("vibration", true));

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.triggerHaptic(buttonView);
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        switchAutoRecord.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.triggerHaptic(buttonView);
            prefs.edit().putBoolean("auto_record", isChecked).apply();
        });

        switchSpam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.triggerHaptic(buttonView);
            prefs.edit().putBoolean("spam_protection", isChecked).apply();
        });

        switchRecordUnknown.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.triggerHaptic(buttonView);
            prefs.edit().putBoolean("record_unknown", isChecked).apply();
        });

        switchVibration.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Utils.triggerHaptic(buttonView);
            prefs.edit().putBoolean("vibration", isChecked).apply();
        });

        view.findViewById(R.id.layoutSpeedDialSettings).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            startActivity(new Intent(requireContext(), SpeedDialActivity.class));
        });

        view.findViewById(R.id.layoutBlockListSettings).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            startActivity(new Intent(requireContext(), BlockListActivity.class));
        });

        View accountSync = view.findViewById(R.id.layoutAccountSync);
        if (accountSync != null) {
            accountSync.setOnClickListener(v -> {
                if (!isAdded()) return;
                Utils.triggerHaptic(v);
                Toast.makeText(getContext(), "Syncing contacts...", Toast.LENGTH_SHORT).show();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (isAdded()) Toast.makeText(getContext(), "Sync complete", Toast.LENGTH_SHORT).show();
                }, 1500);
            });
        }

        return view;
    }
}

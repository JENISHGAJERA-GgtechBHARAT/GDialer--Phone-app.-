package com.gg_tech_bharat.gdialer;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telecom.TelecomManager;
import android.net.Uri;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_SET_DEFAULT_DIALER = 3005;

    private ViewPager2 viewPager;
    private TextView tabKeypad, tabRecents, tabContacts, tabVoicemail;
    private AppDatabase database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.content.SharedPreferences prefs = getSharedPreferences("DialerPrefs", MODE_PRIVATE);
        boolean useSystem = prefs.getBoolean("use_system_theme", true);
        if (useSystem) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            boolean darkMode = prefs.getBoolean("dark_mode", true);
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                    darkMode ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            database = AppDatabase.getDatabase(this);
            
            // Background cleanup after UI starts
            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    if (database != null) database.contactDao().deleteDuplicates();
                });
            }, 3000);

            runOnUiThread(() -> {
                database.contactDao().getAllContacts().observe(this, contacts -> {
                    if (contacts != null) {
                        AppDatabase.databaseWriteExecutor.execute(() -> ContactCache.setCachedContacts(contacts));
                    }
                });
            });
        });

        viewPager = findViewById(R.id.viewPager);
        tabKeypad = findViewById(R.id.tabKeypad);
        tabRecents = findViewById(R.id.tabRecents);
        tabContacts = findViewById(R.id.tabContacts);
        tabVoicemail = findViewById(R.id.tabVoicemail);

        setupViewPager();
        setupBottomNavigation();

        handleIntent(getIntent());

        if (!PermissionManager.hasAllPermissions(this)) {
            PermissionManager.requestPermissions(this);
        } else {
            checkDefaultDialer();
            checkOverlayPermission();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionManager.hasAllPermissions(this)) {
            checkDefaultDialer();
            checkOverlayPermission();
        }
    }

    private void setupViewPager() {
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull @Override public Fragment createFragment(int position) {
                switch (position) {
                    case 0: return new KeypadFragment();
                    case 1: return new RecentsFragment();
                    case 2: return new ContactsFragment();
                    case 3: return new VoicemailFragment();
                    default: return new KeypadFragment();
                }
            }
            @Override public int getItemCount() { return 4; }
        });

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) { updateTabSelection(position); }
        });
        viewPager.setUserInputEnabled(false);
    }

    private void setupBottomNavigation() {
        tabKeypad.setOnClickListener(v -> { Utils.triggerHaptic(v); viewPager.setCurrentItem(0, true); });
        tabRecents.setOnClickListener(v -> { Utils.triggerHaptic(v); viewPager.setCurrentItem(1, true); });
        tabContacts.setOnClickListener(v -> { Utils.triggerHaptic(v); viewPager.setCurrentItem(2, true); });
        tabVoicemail.setOnClickListener(v -> { Utils.triggerHaptic(v); viewPager.setCurrentItem(3, true); });
    }

    public void switchToTab(int index) {
        if (viewPager != null) viewPager.setCurrentItem(index, true);
    }

    private void updateTabSelection(int p) {
        resetTabStyle(tabKeypad); resetTabStyle(tabRecents); resetTabStyle(tabContacts); resetTabStyle(tabVoicemail);
        switch (p) {
            case 0: highlightTab(tabKeypad); break;
            case 1: highlightTab(tabRecents); break;
            case 2: highlightTab(tabContacts); break;
            case 3: highlightTab(tabVoicemail); break;
        }
    }

    private void resetTabStyle(TextView t) {
        t.setTextColor(getResources().getColor(R.color.text_secondary));
        t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    }

    private void highlightTab(TextView t) {
        t.setTextColor(getResources().getColor(R.color.text_primary));
        t.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    }

    private void checkDefaultDialer() {
        TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
        if (tm != null) {
            String def = tm.getDefaultDialerPackage();
            if (def == null || !def.equals(getPackageName())) {
                requestDefaultDialerRole();
            } else {
                Log.d("MainActivity", "Role Active: System Binder Connected");
            }
        }
    }

    private void requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager rm = (RoleManager) getSystemService(Context.ROLE_SERVICE);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), REQUEST_CODE_SET_DEFAULT_DIALER);
            }
        } else {
            Intent intent = new Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER);
            intent.putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, getPackageName());
            startActivity(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER && resultCode != RESULT_OK) {
            Toast.makeText(this, "GDialer needs to be the default app to function correctly", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action) || Intent.ACTION_CALL.equals(action)) {
            Uri data = intent.getData();
            if (data != null && "tel".equals(data.getScheme())) {
                String number = data.getSchemeSpecificPart();
                if (number != null && !number.isEmpty()) {
                    viewPager.setCurrentItem(0, false); // Switch to Keypad
                    new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                        Fragment f = getSupportFragmentManager().findFragmentByTag("f0");
                        if (f instanceof KeypadFragment) {
                            ((KeypadFragment) f).setDialedNumber(number);
                        }
                    }, 200);
                    return;
                }
            }
        }
        viewPager.setCurrentItem(1, false); // Default to Recents
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (PermissionManager.hasAllPermissions(this)) {
            checkDefaultDialer();
            checkOverlayPermission();
        }
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("System Overlay Permission")
                        .setMessage("GDialer needs the 'Display over other apps' permission to reliably show caller screens and answer dialogs on Oppo and Realme devices. Please enable it in the next screen.")
                        .setPositiveButton("Grant Permission", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(this, "Could not open settings screen", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Later", null)
                        .setCancelable(false)
                        .show();
            }
        }
    }
}

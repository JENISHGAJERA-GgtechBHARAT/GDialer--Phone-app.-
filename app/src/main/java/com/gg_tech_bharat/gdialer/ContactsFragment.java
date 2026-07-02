package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ContactsFragment extends Fragment {

    private RecyclerView rvContacts;
    private ContactAdapter contactsAdapter;
    private AppDatabase database;
    private EditText etSearch;
    private android.widget.ImageButton btnClearSearch;
    private View layoutSelectionBar, layoutRecentContactsHeader;
    private TextView btnCloseAllRecentContacts;
    private LinearLayout layoutSelectAll, layoutDeleteSelected, layoutSideIndex;
    private TextView tvSelectedCount, tvSelectAllText;
    private OnBackPressedCallback onBackPressedCallback;

    public static boolean sHasSyncedThisSession = false;
    private static final java.util.concurrent.ExecutorService syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        database = AppDatabase.getDatabase(requireContext());

        rvContacts = view.findViewById(R.id.rvContacts);
        etSearch = view.findViewById(R.id.etSearchContacts);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar);
        layoutRecentContactsHeader = view.findViewById(R.id.layoutRecentContactsHeader);
        btnCloseAllRecentContacts = view.findViewById(R.id.btnCloseAllRecentContacts);
        layoutSelectAll = view.findViewById(R.id.layoutSelectAll);
        layoutDeleteSelected = view.findViewById(R.id.layoutDeleteSelected);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        tvSelectAllText = view.findViewById(R.id.tvSelectAllText);
        layoutSideIndex = view.findViewById(R.id.layoutSideIndex);

        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        rvContacts.setLayoutManager(lm);
        contactsAdapter = new ContactAdapter(requireContext());
        rvContacts.setAdapter(contactsAdapter);

        setupOnBackPressedCallback();

        contactsAdapter.setOnSelectionModeListener((isSelectionMode, selectedCount) -> {
            layoutSelectionBar.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            tvSelectedCount.setText(String.format(java.util.Locale.getDefault(), "%d selected", selectedCount));
            updateOnBackPressedCallbackState();
            tvSelectAllText.setText(selectedCount == contactsAdapter.getItemCount() && selectedCount > 0 ? "Deselect All" : "Select All");
        });

        layoutSelectAll.setOnClickListener(v -> contactsAdapter.selectAll(contactsAdapter.getSelectedContactIds().size() != contactsAdapter.getItemCount()));

        layoutDeleteSelected.setOnClickListener(v -> {
            int count = contactsAdapter.getSelectedContactIds().size();
            if (count == 0) return;
            new AlertDialog.Builder(requireContext()).setTitle("Delete").setMessage("Delete " + count + " contacts and their call history?").setPositiveButton("Delete", (dialog, which) -> {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    java.util.Set<Integer> ids = contactsAdapter.getSelectedContactIds();
                    List<ContactModel> all = database.contactDao().getAllContactsSync();
                    for (ContactModel c : all) {
                        if (ids.contains(c.getId())) {
                            // SYNC DELETE TO GOOGLE
                            deleteFromSystemContacts(c.getNumber());
                            
                            database.recentDao().deleteByNumber(c.getNumber());
                            database.contactDao().delete(c);
                        }
                    }
                    if (getActivity() != null) getActivity().runOnUiThread(() -> contactsAdapter.setSelectionMode(false));
                });
            }).setNegativeButton("Cancel", null).show();
        });

        syncDeviceContacts();

        database.contactDao().getAllContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts != null) contactsAdapter.setContacts(contacts);
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable r;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (r != null) h.removeCallbacks(r);
                String query = s.toString();
                if (btnClearSearch != null) btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                
                // Show/Hide Recent Contacts Header based on search state
                if (layoutRecentContactsHeader != null) {
                    layoutRecentContactsHeader.setVisibility(query.isEmpty() && etSearch.hasFocus() ? View.VISIBLE : View.GONE);
                }

                if (query.isEmpty()) {
                    loadDefaultContacts();
                } else {
                    r = () -> {
                        AppDatabase.databaseWriteExecutor.execute(() -> {
                            List<ContactModel> filtered = database.contactDao().searchContactsWithRanking("%" + query + "%", query + "%");
                            if (getActivity() != null) getActivity().runOnUiThread(() -> contactsAdapter.setContacts(filtered));
                        });
                    };
                    h.postDelayed(r, 250);
                }
                updateOnBackPressedCallbackState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (layoutRecentContactsHeader != null) {
                layoutRecentContactsHeader.setVisibility(hasFocus && etSearch.getText().toString().isEmpty() ? View.VISIBLE : View.GONE);
            }
            if (hasFocus && etSearch.getText().toString().isEmpty()) {
                loadRecentContactsForSearch();
            }
            updateOnBackPressedCallbackState();
        });

        if (btnCloseAllRecentContacts != null) {
            btnCloseAllRecentContacts.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                // Clear "Recent Contacts" from search view (using a smooth fade)
                view.findViewById(R.id.rvContacts).animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    contactsAdapter.setContacts(new ArrayList<>());
                    view.findViewById(R.id.rvContacts).setAlpha(1f);
                    if (layoutRecentContactsHeader != null) layoutRecentContactsHeader.setVisibility(View.GONE);
                    com.google.android.material.snackbar.Snackbar.make(view, "Recent contacts cleared", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                }).start();
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                etSearch.setText("");
                com.google.android.material.snackbar.Snackbar.make(view, "Search cleared", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
            });
        }

        view.findViewById(R.id.btnMenuSettings).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });

        view.findViewById(R.id.fabDialpad).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).switchToTab(0);
        });

        setupSideIndex();

        new androidx.recyclerview.widget.ItemTouchHelper(new SwipeToCallMessageCallback(requireContext(), new SwipeToCallMessageCallback.SwipeActionListener() {
            @Override public void onCallAction(int p) {
                Context context = getContext();
                if (context == null || contactsAdapter == null || contactsAdapter.isSelectionMode()) {
                    if (contactsAdapter != null) contactsAdapter.notifyItemChanged(p);
                    return;
                }
                Utils.makePhoneCall(context, contactsAdapter.getContactAt(p).getNumber());
                contactsAdapter.notifyItemChanged(p);
            }
            @Override public void onMessageAction(int p) {
                Context context = getContext();
                if (context == null || contactsAdapter == null || contactsAdapter.isSelectionMode()) {
                    if (contactsAdapter != null) contactsAdapter.notifyItemChanged(p);
                    return;
                }
                Utils.sendSMS(context, contactsAdapter.getContactAt(p).getNumber(), "");
                contactsAdapter.notifyItemChanged(p);
            }
        })).attachToRecyclerView(rvContacts);

        return view;
    }

    private void loadDefaultContacts() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> all = database.contactDao().getAllContactsSync();
            if (getActivity() != null) getActivity().runOnUiThread(() -> contactsAdapter.setContacts(all));
        });
    }

    private void loadRecentContactsForSearch() {
        // Show recently called contacts in the search list
        AppDatabase.databaseWriteExecutor.execute(() -> {
            // Join contacts with recents to get most recent ones
            List<ContactModel> recents = database.contactDao().searchContactsWithRanking("%%", "%%");
            if (getActivity() != null) getActivity().runOnUiThread(() -> contactsAdapter.setContacts(recents));
        });
    }

    private void deleteFromSystemContacts(String number) {
        if (number == null || number.isEmpty()) return;
        try {
            android.net.Uri contactUri = android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number));
            Cursor cursor = requireContext().getContentResolver().query(contactUri, new String[]{ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.LOOKUP_KEY}, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String lookupKey = cursor.getString(1);
                    android.net.Uri uri = android.net.Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey);
                    requireContext().getContentResolver().delete(uri, null, null);
                }
                cursor.close();
            }
        } catch (Exception e) { Log.e("ContactsFragment", "System delete failed", e); }
    }

    private void setupSideIndex() {
        layoutSideIndex.removeAllViews();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";
        for (int i = 0; i < chars.length(); i++) {
            final String letter = String.valueOf(chars.charAt(i));
            TextView tv = new TextView(requireContext());
            tv.setText(letter);
            tv.setTextColor(Color.GRAY);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            tv.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            tv.setLayoutParams(lp);
            layoutSideIndex.addView(tv);
        }

        layoutSideIndex.setOnClickListener(v -> {}); // Satisfy accessibility
        layoutSideIndex.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float y = event.getY();
                int itemHeight = layoutSideIndex.getHeight() / chars.length();
                if (itemHeight <= 0) return true;
                int index = (int) (y / itemHeight);
                if (index >= 0 && index < chars.length()) {
                    scrollToLetter(String.valueOf(chars.charAt(index)));
                }
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
                return true;
            }
            return false;
        });
    }

    private void scrollToLetter(String letter) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> list = database.contactDao().getAllContactsSync();
            int foundIndex = -1;
            for (int i = 0; i < list.size(); i++) {
                String name = list.get(i).getName();
                if (name != null && !name.isEmpty()) {
                    if (letter.equals("#")) {
                        if (!Character.isLetter(name.charAt(0))) { foundIndex = i; break; }
                    } else if (name.toUpperCase().startsWith(letter)) {
                        foundIndex = i;
                        break;
                    }
                }
            }
            if (foundIndex != -1) {
                final int finalIndex = foundIndex;
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        LinearLayoutManager lm = (LinearLayoutManager) rvContacts.getLayoutManager();
                        if (lm != null) lm.scrollToPositionWithOffset(finalIndex, 0);
                    });
                }
            }
        });
    }

    private void updateOnBackPressedCallbackState() {
        boolean shouldHandle = contactsAdapter.isSelectionMode() 
                || (etSearch != null && (etSearch.hasFocus() || !etSearch.getText().toString().isEmpty()));
        onBackPressedCallback.setEnabled(shouldHandle);
    }

    private void setupOnBackPressedCallback() {
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { 
                if (contactsAdapter.isSelectionMode()) {
                    contactsAdapter.setSelectionMode(false);
                } else if (etSearch != null && (!etSearch.getText().toString().isEmpty() || etSearch.hasFocus())) {
                    etSearch.setText("");
                    etSearch.clearFocus();
                    android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                }
                updateOnBackPressedCallbackState();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    private void syncDeviceContacts() {
        if (sHasSyncedThisSession || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        triggerSyncDirectly(requireContext());
    }

    public static void triggerSyncDirectly(Context context) {
        sHasSyncedThisSession = false;
        syncExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getDatabase(context);
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
                try (Cursor cursor = context.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                        new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, // index 0
                            ContactsContract.CommonDataKinds.Phone.NUMBER,       // index 1
                            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,    // index 2
                            ContactsContract.CommonDataKinds.Phone.STARRED       // index 3
                        }, 
                        null, null, null)) {
                    if (cursor != null) {
                        ContactDao dao = db.contactDao();
                        List<ContactModel> existing = dao.getAllContactsSync();
                        
                        java.util.Map<String, ContactModel> existingMap = new java.util.HashMap<>();
                        for (ContactModel c : existing) {
                            if (c.getNormalizedNumber() != null && !c.getNormalizedNumber().isEmpty()) {
                                existingMap.put(c.getNormalizedNumber(), c);
                            }
                        }
                        
                        List<ContactModel> toInsert = new ArrayList<>();
                        List<ContactModel> toUpdate = new ArrayList<>();
                        
                        while (cursor.moveToNext()) {
                            String name = cursor.getString(0);
                            String number = cursor.getString(1);
                            String photoUri = cursor.getString(2);
                            boolean isStarred = cursor.getInt(3) == 1;
                            
                            if (number == null) continue;
                            String normalized = Utils.normalizePhoneNumber(number);
                            if (normalized.isEmpty()) continue;
                            
                            if (existingMap.containsKey(normalized)) {
                                ContactModel existingContact = existingMap.get(normalized);
                                boolean changed = false;
                                
                                if (name != null && !name.equals(existingContact.getName())) {
                                    existingContact.setName(name);
                                    changed = true;
                                }
                                
                                if (photoUri != null && !photoUri.equals(existingContact.getPhotoUri())) {
                                    existingContact.setPhotoUri(photoUri);
                                    changed = true;
                                } else if (photoUri == null && existingContact.getPhotoUri() != null && "Synced".equals(existingContact.getNotes())) {
                                    existingContact.setPhotoUri(null);
                                    changed = true;
                                }
                                
                                if (isStarred != existingContact.isFavorite()) {
                                    existingContact.setFavorite(isStarred);
                                    changed = true;
                                }
                                
                                if (changed) {
                                    toUpdate.add(existingContact);
                                }
                            } else {
                                toInsert.add(new ContactModel(name, number, photoUri, isStarred, false, "Synced"));
                            }
                        }
                        
                        if (!toInsert.isEmpty()) dao.insertAll(toInsert);
                        if (!toUpdate.isEmpty()) dao.updateAll(toUpdate);
                        sHasSyncedThisSession = true;
                    }
                }
            } catch (Exception e) { Log.e("ContactsFragment", "Sync error", e); }
        });
    }
}

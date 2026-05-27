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
    private View layoutSelectionBar;
    private LinearLayout layoutSelectAll, layoutDeleteSelected, layoutSideIndex;
    private TextView tvSelectedCount, tvSelectAllText;
    private OnBackPressedCallback onBackPressedCallback;

    private static boolean sHasSyncedThisSession = false;
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
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar);
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
            onBackPressedCallback.setEnabled(isSelectionMode);
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
                r = () -> contactsAdapter.filter(s.toString());
                h.postDelayed(r, 250);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

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

    private void setupOnBackPressedCallback() {
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { if (contactsAdapter.isSelectionMode()) contactsAdapter.setSelectionMode(false); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    private void syncDeviceContacts() {
        if (sHasSyncedThisSession || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
        syncExecutor.execute(() -> {
            try (Cursor cursor = requireContext().getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.PHOTO_URI, ContactsContract.CommonDataKinds.Phone.STARRED}, 
                    null, null, null)) {
                if (cursor != null) {
                    ContactDao dao = database.contactDao();
                    List<ContactModel> existing = dao.getAllContactsSync();
                    java.util.Set<String> numbers = new java.util.HashSet<>();
                    for (ContactModel c : existing) numbers.add(c.getNumber());
                    List<ContactModel> toInsert = new ArrayList<>();
                    while (cursor.moveToNext()) {
                        String n = cursor.getString(1);
                        if (n != null && !numbers.contains(n)) {
                            toInsert.add(new ContactModel(cursor.getString(0), n, cursor.getString(2), cursor.getInt(3) == 1, false, "Synced"));
                            if (toInsert.size() >= 50) {
                                dao.insertAll(new ArrayList<>(toInsert));
                                toInsert.clear();
                            }
                        }
                    }
                    if (!toInsert.isEmpty()) dao.insertAll(toInsert);
                    sHasSyncedThisSession = true;
                }
            } catch (Exception e) { Log.e("ContactsFragment", "Sync error", e); }
        });
    }
}

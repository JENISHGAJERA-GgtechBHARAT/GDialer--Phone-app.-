package com.gg_tech_bharat.gdialer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CallLog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
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
import java.util.HashSet;
import java.util.List;

public class RecentsFragment extends Fragment {

    private RecentAdapter adapter;
    private AppDatabase database;
    private View layoutSelectionBar;
    private LinearLayout layoutSelectAll, layoutDeleteSelected;
    private TextView tvSelectedCount, tvSelectAllText;
    private OnBackPressedCallback onBackPressedCallback;
    private EditText etSearch;
    private android.widget.ImageButton btnClearSearch;
    private View layoutRecentSearchHeader;
    private TextView btnCloseAllSearch;

    private List<RecentModel> lastEmittedRecents = new ArrayList<>();

    private static final java.util.concurrent.ExecutorService syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_recents, container, false);
        database = AppDatabase.getDatabase(requireContext());

        RecyclerView rvRecents = view.findViewById(R.id.rvRecents);
        ImageButton btnMenuSettings = view.findViewById(R.id.btnMenuSettings);
        etSearch = view.findViewById(R.id.etSearchRecents);
        btnClearSearch = view.findViewById(R.id.btnClearSearch);
        layoutRecentSearchHeader = view.findViewById(R.id.layoutRecentSearchHeader);
        btnCloseAllSearch = view.findViewById(R.id.btnCloseAllSearch);

        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBar);
        layoutSelectAll = view.findViewById(R.id.layoutSelectAll);
        layoutDeleteSelected = view.findViewById(R.id.layoutDeleteSelected);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCount);
        tvSelectAllText = view.findViewById(R.id.tvSelectAllText);

        rvRecents.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecentAdapter(requireContext());
        rvRecents.setAdapter(adapter);

        setupOnBackPressedCallback();

        adapter.setOnSelectionModeListener((isSelectionMode, selectedCount) -> {
            layoutSelectionBar.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            tvSelectedCount.setText(String.format(java.util.Locale.getDefault(), "%d selected", selectedCount));
            updateOnBackPressedCallbackState();
            tvSelectAllText.setText(selectedCount == adapter.getItemCount() && selectedCount > 0 ? "Deselect All" : "Select All");
        });

        layoutSelectAll.setOnClickListener(v -> adapter.selectAll(adapter.getSelectedRecentIds().size() != adapter.getItemCount()));

        layoutDeleteSelected.setOnClickListener(v -> {
            int count = adapter.getSelectedRecentIds().size();
            if (count == 0) return;
            new AlertDialog.Builder(requireContext()).setTitle("Delete History").setMessage("Delete " + count + " items?").setPositiveButton("Delete", (dialog, which) -> {
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    java.util.Set<Integer> ids = adapter.getSelectedRecentIds();
                    List<RecentModel> all = database.recentDao().getAllRecentsSync();
                    for (RecentModel r : all) if (ids.contains(r.getId())) database.recentDao().delete(r);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> adapter.setSelectionMode(false));
                });
            }).setNegativeButton("Cancel", null).show();
        });

        syncDeviceCallLogs();

        btnMenuSettings.setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            startActivity(new Intent(requireContext(), SettingsActivity.class));
        });

        view.findViewById(R.id.fabDialpad).setOnClickListener(v -> {
            Utils.triggerHaptic(v);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).switchToTab(0);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            private final android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            private Runnable r;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (r != null) h.removeCallbacks(r);
                String query = s.toString();
                if (btnClearSearch != null) btnClearSearch.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                
                if (layoutRecentSearchHeader != null) {
                    layoutRecentSearchHeader.setVisibility(query.isEmpty() && etSearch.hasFocus() ? View.VISIBLE : View.GONE);
                }

                r = () -> {
                    if (!query.isEmpty()) {
                        saveRecentSearch(query);
                    }
                    searchContactsAndRecents(query);
                };
                h.postDelayed(r, 400);
                updateOnBackPressedCallbackState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        etSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (layoutRecentSearchHeader != null) {
                layoutRecentSearchHeader.setVisibility(hasFocus && etSearch.getText().toString().isEmpty() ? View.VISIBLE : View.GONE);
            }
            if (hasFocus && etSearch.getText().toString().isEmpty()) {
                loadRecentSearches();
            }
            updateOnBackPressedCallbackState();
        });

        if (btnCloseAllSearch != null) {
            btnCloseAllSearch.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                AppDatabase.databaseWriteExecutor.execute(() -> {
                    database.recentSearchDao().clearAll();
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            view.findViewById(R.id.rvRecents).animate().alpha(0f).setDuration(300).withEndAction(() -> {
                                adapter.setRecents(new ArrayList<>());
                                view.findViewById(R.id.rvRecents).setAlpha(1f);
                                if (layoutRecentSearchHeader != null) layoutRecentSearchHeader.setVisibility(View.GONE);
                                com.google.android.material.snackbar.Snackbar.make(view, "Recent searches cleared", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
                            }).start();
                        });
                    }
                });
            });
        }

        if (btnClearSearch != null) {
            btnClearSearch.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                etSearch.setText("");
            });
        }

        // 1. Observe Recents table
        database.recentDao().getAllRecents().observe(getViewLifecycleOwner(), recents -> {
            if (recents != null) {
                lastEmittedRecents = recents;
                processAndDisplayRecents();
            }
        });

        // 2. Observe Contacts table (to refresh names when deleted/edited)
        database.contactDao().getAllContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (contacts != null) {
                // Update cache immediately to ensure next processing is accurate
                ContactCache.setCachedContacts(contacts);
                processAndDisplayRecents();
            }
        });

        new androidx.recyclerview.widget.ItemTouchHelper(new SwipeToCallMessageCallback(requireContext(), new SwipeToCallMessageCallback.SwipeActionListener() {
            @Override public void onCallAction(int position) {
                Context context = getContext();
                if (context == null || adapter == null || adapter.isSelectionMode()) {
                    if (adapter != null) adapter.notifyItemChanged(position);
                    return;
                }
                RecentModel r = adapter.getRecentAt(position);
                if (r != null) Utils.makePhoneCall(context, r.getNumber());
                adapter.notifyItemChanged(position);
            }
            @Override public void onMessageAction(int position) {
                Context context = getContext();
                if (context == null || adapter == null || adapter.isSelectionMode()) {
                    if (adapter != null) adapter.notifyItemChanged(position);
                    return;
                }
                RecentModel r = adapter.getRecentAt(position);
                if (r != null) Utils.sendSMS(context, r.getNumber(), "");
                adapter.notifyItemChanged(position);
            }
        })).attachToRecyclerView(rvRecents);

        return view;
    }

    private void saveRecentSearch(String query) {
        if (query.length() < 2) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.recentSearchDao().insert(new RecentSearch(query, System.currentTimeMillis()));
        });
    }

    private void loadRecentSearches() {
        database.recentSearchDao().getRecentSearches().observe(getViewLifecycleOwner(), searches -> {
            if (searches == null || !etSearch.getText().toString().isEmpty() || !etSearch.hasFocus()) return;
            List<RecentModel> results = new ArrayList<>();
            for (RecentSearch s : searches) {
                String query = s.getQuery();
                
                // Try to resolve the contact name for the search query
                ContactModel contact = ContactCache.getContactByNumber(query);
                String displayName = (contact != null) ? contact.getName() : query;

                RecentModel r = new RecentModel(query, displayName, s.getTimestamp(), 0, 0, false, "");
                r.setId(-Math.abs(query.hashCode()));
                results.add(r);
            }
            adapter.setRecents(results);
        });
    }

    private void processAndDisplayRecents() {
        if (!etSearch.getText().toString().isEmpty()) return;
        final List<RecentModel> dataToProcess = new ArrayList<>(lastEmittedRecents);
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<RecentModel> processed = groupRecents(dataToProcess);
            
            // PRE-RESOLVE NAMES FROM CACHE FOR SPEED
            for (RecentModel r : processed) {
                if ("Conference".equals(r.getNumber())) {
                    r.setName("Conference call");
                } else {
                    ContactModel contact = ContactCache.getContactByNumber(r.getNumber());
                    if (contact != null) r.setName(contact.getName());
                    else r.setName(null); // Force number display (means contact was deleted)
                }
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (adapter != null) adapter.setRecents(processed);
                });
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            // High-reliability sync on return
            syncDeviceCallLogs();
        }
    }

    private void searchContactsAndRecents(String query) {
        if (query.isEmpty()) {
            processAndDisplayRecents();
            return;
        }
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> contacts = database.contactDao().searchContactsWithRanking("%" + query + "%", query + "%");
            List<RecentModel> results = new ArrayList<>();
            for (ContactModel c : contacts) {
                RecentModel r = new RecentModel(c.getNumber(), c.getName(), 0, 0, 0, false, "");
                r.setId(-Math.abs(c.getNumber().hashCode()));
                results.add(r);
            }
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> adapter.setRecents(results));
            }
        });
    }

    private void updateOnBackPressedCallbackState() {
        boolean shouldHandle = adapter.isSelectionMode() 
                || (etSearch != null && (etSearch.hasFocus() || !etSearch.getText().toString().isEmpty()));
        onBackPressedCallback.setEnabled(shouldHandle);
    }

    private void setupOnBackPressedCallback() {
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { 
                if (adapter.isSelectionMode()) {
                    adapter.setSelectionMode(false);
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

    private List<RecentModel> groupRecents(List<RecentModel> recents) {
        if (recents.isEmpty()) return recents;
        List<RecentModel> grouped = new ArrayList<>();
        RecentModel currentGroup = null;
        int count = 0;
        long groupStartTime = 0;

        for (RecentModel r : recents) {
            if (currentGroup == null) {
                currentGroup = copyRecent(r);
                count = 1;
                groupStartTime = r.getTimestamp();
            } else {
                // Group if same number and within 30 minutes of the latest call in the group
                boolean sameNumber = r.getNumber().equals(currentGroup.getNumber());
                boolean within30Min = (groupStartTime - r.getTimestamp()) <= (30 * 60 * 1000L);
                
                if (sameNumber && within30Min) {
                    count++;
                } else {
                    currentGroup.setCallCount(count);
                    grouped.add(currentGroup);
                    currentGroup = copyRecent(r);
                    count = 1;
                    groupStartTime = r.getTimestamp();
                }
            }
        }
        if (currentGroup != null) {
            currentGroup.setCallCount(count);
            grouped.add(currentGroup);
        }
        return grouped;
    }

    private RecentModel copyRecent(RecentModel r) {
        RecentModel copy = new RecentModel(r.getNumber(), r.getName(), r.getTimestamp(), r.getDuration(), r.getCallType(), r.isRecorded(), r.getRecordingPath());
        copy.setId(r.getId());
        return copy;
    }

    private static long lastSyncTime = 0;

    private void syncDeviceCallLogs() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return;
        
        long now = System.currentTimeMillis();
        if (now - lastSyncTime < 10000) return;
        lastSyncTime = now;

        syncExecutor.execute(() -> {
            try (Cursor cursor = requireContext().getContentResolver().query(CallLog.Calls.CONTENT_URI, 
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE, CallLog.Calls.DURATION, CallLog.Calls.TYPE}, 
                    null, null, CallLog.Calls.DATE + " DESC LIMIT 500")) {
                if (cursor != null) {
                    RecentDao dao = database.recentDao();
                    List<RecentModel> localRecents = dao.getAllRecentsSync();
                    
                    HashSet<Long> systemTimestamps = new HashSet<>();
                    List<RecentModel> toInsert = new ArrayList<>();
                    
                    while (cursor.moveToNext()) {
                        long date = cursor.getLong(2);
                        systemTimestamps.add(date);
                        
                        boolean existsLocally = false;
                        for (RecentModel local : localRecents) {
                            if (local.getTimestamp() == date) {
                                existsLocally = true;
                                break;
                            }
                        }
                        
                        if (!existsLocally) {
                            int type = cursor.getInt(4);
                            int mappedType = type == CallLog.Calls.OUTGOING_TYPE ? 2 : (type == CallLog.Calls.MISSED_TYPE || type == CallLog.Calls.REJECTED_TYPE ? 3 : 1);
                            String cachedName = cursor.getString(1);
                            String number = cursor.getString(0) != null ? cursor.getString(0) : "";
                            toInsert.add(new RecentModel(number, cachedName != null ? cachedName : "", date, cursor.getLong(3), mappedType, false, ""));
                        }
                    }
                    
                    if (!toInsert.isEmpty()) dao.insertAll(toInsert);

                    for (RecentModel local : localRecents) {
                        if (local.getTimestamp() > 0 && !systemTimestamps.contains(local.getTimestamp())) {
                            dao.delete(local);
                        }
                    }
                }
            } catch (Exception e) { Log.e("RecentsFragment", "Sync error", e); }
        });
    }
}

package com.gg_tech_bharat.gdialer;

import android.Manifest;
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
            onBackPressedCallback.setEnabled(isSelectionMode);
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
                r = () -> searchContactsAndRecents(query);
                h.postDelayed(r, 250);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadCallHistory();

        // REFRESH RECENT NAMES WHEN CONTACTS CHANGE
        database.contactDao().getAllContacts().observe(getViewLifecycleOwner(), contacts -> {
            if (adapter != null) adapter.notifyDataSetChanged();
        });

        new androidx.recyclerview.widget.ItemTouchHelper(new SwipeToCallMessageCallback(requireContext(), new SwipeToCallMessageCallback.SwipeActionListener() {
            @Override public void onCallAction(int position) {
                if (adapter.isSelectionMode()) { adapter.notifyItemChanged(position); return; }
                RecentModel r = adapter.getRecentAt(position);
                if (r != null) Utils.makePhoneCall(requireContext(), r.getNumber());
                adapter.notifyItemChanged(position);
            }
            @Override public void onMessageAction(int position) {
                if (adapter.isSelectionMode()) { adapter.notifyItemChanged(position); return; }
                RecentModel r = adapter.getRecentAt(position);
                if (r != null) Utils.sendSMS(requireContext(), r.getNumber(), "");
                adapter.notifyItemChanged(position);
            }
        })).attachToRecyclerView(rvRecents);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (adapter != null) {
            // Force a full refresh of names and photos to catch edits/deletions
            AppDatabase.databaseWriteExecutor.execute(() -> {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> adapter.notifyDataSetChanged());
                }
            });
        }
    }

    private void searchContactsAndRecents(String query) {
        if (query.isEmpty()) {
            loadCallHistory();
            return;
        }
        
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<ContactModel> contacts = database.contactDao().searchContactsSync("%" + query + "%");
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

    private void setupOnBackPressedCallback() {
        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() { if (adapter.isSelectionMode()) adapter.setSelectionMode(false); }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), onBackPressedCallback);
    }

    private void loadCallHistory() {
        database.recentDao().getAllRecents().observe(getViewLifecycleOwner(), recents -> {
            if (recents != null && etSearch.getText().toString().isEmpty()) {
                adapter.setRecents(groupRecents(recents));
            }
        });
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
                // We use Math.abs because timestamps are descending, so currentGroup.ts > r.ts
                boolean within30Min = (groupStartTime - r.getTimestamp()) <= (30 * 60 * 1000L);
                
                if (sameNumber && within30Min) {
                    count++;
                } else {
                    if (count > 1) {
                        String baseName = (currentGroup.getName() != null && !currentGroup.getName().isEmpty()) ? currentGroup.getName() : currentGroup.getNumber();
                        currentGroup.setName(baseName + " (" + count + ")");
                    }
                    grouped.add(currentGroup);
                    currentGroup = copyRecent(r);
                    count = 1;
                    groupStartTime = r.getTimestamp();
                }
            }
        }
        if (currentGroup != null) {
            if (count > 1) {
                String baseName = (currentGroup.getName() != null && !currentGroup.getName().isEmpty()) ? currentGroup.getName() : currentGroup.getNumber();
                currentGroup.setName(baseName + " (" + count + ")");
            }
            grouped.add(currentGroup);
        }
        return grouped;
    }

    private RecentModel copyRecent(RecentModel r) {
        RecentModel copy = new RecentModel(r.getNumber(), r.getName(), r.getTimestamp(), r.getDuration(), r.getCallType(), r.isRecorded(), r.getRecordingPath());
        copy.setId(r.getId());
        return copy;
    }

    private void syncDeviceCallLogs() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return;
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

                    // DELETE SYNC: Remove local items that were deleted from system logs
                    // We only do this for the last 500 to avoid accidental mass deletion of older logs
                    for (RecentModel local : localRecents) {
                        // Only consider calls synced from system (duration > 0 or has a valid timestamp within range)
                        if (local.getTimestamp() > 0 && !systemTimestamps.contains(local.getTimestamp())) {
                            // Check if this log is "recent" enough to be in the 500 we queried
                            // If it's not in the system's latest 500 but it is in our local DB, 
                            // and the system still has older calls than this one, then it was definitely deleted.
                            dao.delete(local);
                        }
                    }
                }
            } catch (Exception e) { Log.e("RecentsFragment", "Sync error", e); }
        });
    }
}

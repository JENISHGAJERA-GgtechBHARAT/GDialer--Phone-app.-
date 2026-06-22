package com.gg_tech_bharat.gdialer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VoicemailFragment extends Fragment {

    private RecyclerView rvVoicemails;
    private TextView tvEmpty, btnSelectAll;
    private VoicemailAdapter adapter;
    private final List<VoicemailModel> voicemailList = new ArrayList<>();
    
    private View layoutSelectionBar;
    private TextView tvSelectedCount;
    private LinearLayout layoutDelete, layoutShare;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voicemail, container, false);
        
        rvVoicemails = view.findViewById(R.id.rvVoicemails);
        tvEmpty = view.findViewById(R.id.tvEmptyVoicemails);
        layoutSelectionBar = view.findViewById(R.id.layoutSelectionBarVoicemail);
        tvSelectedCount = view.findViewById(R.id.tvSelectedCountVoicemail);
        layoutDelete = view.findViewById(R.id.layoutDeleteVoicemail);
        layoutShare = view.findViewById(R.id.layoutShareVoicemail);
        btnSelectAll = view.findViewById(R.id.btnSelectAllVoicemail);

        rvVoicemails.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        adapter = new VoicemailAdapter(requireContext(), voicemailList);
        adapter.setOnSelectionModeListener((isSelectionMode, selectedCount) -> {
            layoutSelectionBar.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            if (btnSelectAll != null) btnSelectAll.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            tvSelectedCount.setText(selectedCount + " selected");
            
            if (isSelectionMode && btnSelectAll != null) {
                btnSelectAll.setText(selectedCount == voicemailList.size() ? "Deselect all" : "Select all");
            }
        });
        
        rvVoicemails.setAdapter(adapter);

        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(v -> {
                boolean shouldSelectAll = adapter.getSelectedIds().size() != voicemailList.size();
                adapter.selectAll(shouldSelectAll);
            });
        }

        layoutDelete.setOnClickListener(v -> {
            int count = adapter.getSelectedIds().size();
            if (count == 0) return;
            new AlertDialog.Builder(requireContext())
                .setTitle("Delete Voicemails")
                .setMessage("Delete " + count + " selected voicemails?")
                .setPositiveButton("Delete", (dialog, which) -> deleteSelectedVoicemails())
                .setNegativeButton("Cancel", null)
                .show();
        });

        layoutShare.setOnClickListener(v -> shareSelectedVoicemails());

        loadVoicemails();
        
        return view;
    }

    private void loadVoicemails() {
        voicemailList.clear();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String selection = MediaStore.Audio.Media.RELATIVE_PATH + " LIKE ?";
        String[] selectionArgs = new String[]{"Music/Voicemails%"};
        String sortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATE_ADDED
        };

        try (Cursor cursor = requireContext().getContentResolver().query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    String fileName = cursor.getString(1);
                    long dateAdded = cursor.getLong(2) * 1000;

                    String name = fileName;
                    if (fileName.startsWith("Voicemail_")) {
                        String[] parts = fileName.split("_");
                        if (parts.length >= 2) {
                            String num = parts[1];
                            String contactName = Utils.queryContactName(requireContext(), num);
                            name = (contactName != null) ? contactName : num;
                        }
                    }

                    Uri contentUri = ContentUris.withAppendedId(uri, id);
                    voicemailList.add(new VoicemailModel(id, name, fileName, dateAdded, contentUri));
                }
            }
        } catch (Exception e) {
            Log.e("VoicemailFragment", "Error loading", e);
        }
        
        if (tvEmpty != null) {
            tvEmpty.setVisibility(voicemailList.isEmpty() ? View.VISIBLE : View.GONE);
        }

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void deleteSelectedVoicemails() {
        Set<Long> selectedIds = adapter.getSelectedIds();
        ContentResolver cr = requireContext().getContentResolver();
        for (long id : selectedIds) {
            Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
            try {
                cr.delete(uri, null, null);
            } catch (Exception e) {
                Log.e("VoicemailFragment", "Delete failed", e);
            }
        }
        adapter.setSelectionMode(false);
        loadVoicemails();
        Toast.makeText(requireContext(), "Voicemails deleted", Toast.LENGTH_SHORT).show();
    }

    private void shareSelectedVoicemails() {
        Set<Long> selectedIds = adapter.getSelectedIds();
        if (selectedIds.isEmpty()) return;

        ArrayList<Uri> uris = new ArrayList<>();
        for (long id : selectedIds) {
            uris.add(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id));
        }

        Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setType("audio/*");
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Share Voicemails"));
    }

    @Override
    public void onResume() {
        super.onResume();
        loadVoicemails();
    }

    @Override
    public void onDestroy() {
        if (adapter != null) adapter.release();
        super.onDestroy();
    }
}

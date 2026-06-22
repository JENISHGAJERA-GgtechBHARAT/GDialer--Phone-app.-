package com.gg_tech_bharat.gdialer;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.List;

public class VoicemailFragment extends Fragment {

    private RecyclerView rvVoicemails;
    private TextView tvEmpty;
    private VoicemailLocalAdapter adapter;
    private VoicemailDao voicemailDao;
    private LiveData<List<VoicemailEntity>> currentLiveData;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voicemail, container, false);
        
        voicemailDao = AppDatabase.getDatabase(requireContext()).voicemailDao();
        
        rvVoicemails = view.findViewById(R.id.rvVoicemails);
        tvEmpty = view.findViewById(R.id.tvEmptyVoicemails);
        EditText etSearch = view.findViewById(R.id.etSearchVoicemails);
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupFilters);
        View btnSettings = view.findViewById(R.id.btnGreetingSettings);

        rvVoicemails.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new VoicemailLocalAdapter(requireContext());
        rvVoicemails.setAdapter(adapter);

        setupSwipeActions();

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), GreetingActivity.class);
            startActivity(intent);
        });

        setupFilters(chipGroup);
        setupSearch(etSearch);
        
        loadVoicemails(voicemailDao.getAllVoicemails());

        return view;
    }

    private void setupSwipeActions() {
        ItemTouchHelper.SimpleCallback simpleCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getBindingAdapterPosition();
                VoicemailEntity voicemail = adapter.getVoicemailAt(position);

                if (direction == ItemTouchHelper.LEFT) {
                    // Delete
                    AppDatabase.databaseWriteExecutor.execute(() -> voicemailDao.delete(voicemail));
                    Toast.makeText(requireContext(), "Voicemail deleted", Toast.LENGTH_SHORT).show();
                } else if (direction == ItemTouchHelper.RIGHT) {
                    // Mark as read/unread
                    voicemail.setRead(!voicemail.isRead());
                    AppDatabase.databaseWriteExecutor.execute(() -> voicemailDao.update(voicemail));
                    adapter.notifyItemChanged(position);
                }
            }
        };

        new ItemTouchHelper(simpleCallback).attachToRecyclerView(rvVoicemails);
    }

    private void setupFilters(ChipGroup group) {
        group.setOnCheckedStateChangeListener((group1, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipAll) loadVoicemails(voicemailDao.getAllVoicemails());
            else if (id == R.id.chipUnread) loadVoicemails(voicemailDao.getUnreadVoicemails());
            else if (id == R.id.chipStarred) loadVoicemails(voicemailDao.getStarredVoicemails());
        });
    }

    private void setupSearch(EditText et) {
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = "%" + s.toString() + "%";
                loadVoicemails(voicemailDao.searchVoicemails(query));
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void loadVoicemails(LiveData<List<VoicemailEntity>> liveData) {
        if (currentLiveData != null) {
            currentLiveData.removeObservers(getViewLifecycleOwner());
        }
        currentLiveData = liveData;
        currentLiveData.observe(getViewLifecycleOwner(), voicemails -> {
            adapter.setVoicemails(voicemails);
            tvEmpty.setVisibility(voicemails == null || voicemails.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroy() {
        if (adapter != null) adapter.release();
        super.onDestroy();
    }
}

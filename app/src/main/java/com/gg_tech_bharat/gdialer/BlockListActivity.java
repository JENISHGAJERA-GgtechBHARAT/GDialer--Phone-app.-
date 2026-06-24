package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class BlockListActivity extends AppCompatActivity {

    private EditText etBlockNumber;
    private ImageButton btnBack;
    private androidx.appcompat.widget.AppCompatImageView btnAddBlock;
    private RecyclerView rvBlockedList;
    private BlockedAdapter adapter;
    private AppDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_list);

        database = AppDatabase.getDatabase(this);

        btnBack = findViewById(R.id.btnBack);
        etBlockNumber = findViewById(R.id.etBlockNumber);
        btnAddBlock = findViewById(R.id.btnAddBlock);
        rvBlockedList = findViewById(R.id.rvBlockedList);

        rvBlockedList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new BlockedAdapter();
        rvBlockedList.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());

        btnAddBlock.setOnClickListener(v -> addNumberToBlockList());

        // Observe Blocked Numbers list
        database.blockedNumberDao().getAllBlockedNumbers().observe(this, blockedNumbers -> {
            if (blockedNumbers != null) {
                adapter.setBlockedNumbers(blockedNumbers);
            }
        });
    }

    private void addNumberToBlockList() {
        Utils.triggerHaptic(btnAddBlock);
        String number = etBlockNumber.getText().toString().trim();
        if (number.isEmpty()) {
            Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            BlockedNumber existing = database.blockedNumberDao().getBlockedByNumber(number);
            if (existing != null) {
                runOnUiThread(() -> Toast.makeText(this, "Number already blocked", Toast.LENGTH_SHORT).show());
                return;
            }

            // Insert Blocked Number
            database.blockedNumberDao().insert(new BlockedNumber(number));

            // Sync with Contacts: mark matching contact as spam
            ContactModel contact = database.contactDao().getContactByNumber(number);
            if (contact != null) {
                contact.setSpam(true);
                database.contactDao().update(contact);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Number blocked successfully", Toast.LENGTH_SHORT).show();
                etBlockNumber.setText("");
            });
        });
    }

    private void removeNumberFromBlockList(BlockedNumber blockedNumber) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            database.blockedNumberDao().delete(blockedNumber);

            // Sync with Contacts: mark matching contact as not spam
            ContactModel contact = database.contactDao().getContactByNumber(blockedNumber.getNumber());
            if (contact != null) {
                contact.setSpam(false);
                database.contactDao().update(contact);
            }

            runOnUiThread(() -> Toast.makeText(this, "Number unblocked", Toast.LENGTH_SHORT).show());
        });
    }

    // Local adapter for blocked numbers
    private class BlockedAdapter extends RecyclerView.Adapter<BlockedAdapter.BlockedViewHolder> {

        private List<BlockedNumber> blockedList = new ArrayList<>();

        public void setBlockedNumbers(List<BlockedNumber> list) {
            this.blockedList = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BlockedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(BlockListActivity.this).inflate(R.layout.blocked_item, parent, false);
            return new BlockedViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull BlockedViewHolder holder, int position) {
            BlockedNumber blockedNumber = blockedList.get(position);
            holder.tvBlockedNumber.setText(blockedNumber.getNumber());

            holder.btnUnblock.setOnClickListener(v -> {
                Utils.triggerHaptic(v);
                removeNumberFromBlockList(blockedNumber);
            });
        }

        @Override
        public int getItemCount() {
            return blockedList.size();
        }

        class BlockedViewHolder extends RecyclerView.ViewHolder {
            TextView tvBlockedNumber;
            ImageButton btnUnblock;

            public BlockedViewHolder(@NonNull View itemView) {
                super(itemView);
                tvBlockedNumber = itemView.findViewById(R.id.tvBlockedNumber);
                btnUnblock = itemView.findViewById(R.id.btnUnblock);
            }
        }
    }
}

package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class QuickRepliesActivity extends AppCompatActivity {

    private RecyclerView rvQuickReplies;
    private QuickReplyAdapter adapter;
    private AppDatabase database;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quick_replies);

        database = AppDatabase.getDatabase(this);
        rvQuickReplies = findViewById(R.id.rvQuickReplies);
        ImageButton btnBack = findViewById(R.id.btnBack);
        ImageButton btnAddReply = findViewById(R.id.btnAddReply);

        rvQuickReplies.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuickReplyAdapter(new QuickReplyAdapter.OnItemClickListener() {
            @Override public void onEdit(QuickReplyModel item) { showEditDialog(item); }
            @Override public void onDelete(QuickReplyModel item) { deleteReply(item); }
        });
        rvQuickReplies.setAdapter(adapter);

        btnBack.setOnClickListener(v -> finish());
        btnAddReply.setOnClickListener(v -> showEditDialog(null));

        loadQuickReplies();
    }

    private void loadQuickReplies() {
        database.quickReplyDao().getAllQuickReplies().observe(this, replies -> {
            if (replies != null) adapter.setItems(replies);
        });
    }

    private void showEditDialog(@Nullable QuickReplyModel item) {
        EditText editText = new EditText(this);
        editText.setHint("Enter response message");
        if (item != null) editText.setText(item.getMessage());
        editText.setTextColor(android.graphics.Color.WHITE);
        editText.setPadding(64, 64, 64, 64);

        AlertDialog dialog = new AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                .setTitle(item == null ? "Add Quick Response" : "Edit Response")
                .setView(editText)
                .setPositiveButton("Save", (d, which) -> {
                    String msg = editText.getText().toString().trim();
                    if (msg.isEmpty()) return;
                    
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        if (item == null) {
                            database.quickReplyDao().insert(new QuickReplyModel(msg));
                        } else {
                            item.setMessage(msg);
                            database.quickReplyDao().update(item);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .create();
        
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE);
        });
        dialog.show();
    }

    private void deleteReply(QuickReplyModel item) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.SamsungCustomDialog)
                .setTitle("Delete Response")
                .setMessage("Are you sure you want to delete this message?")
                .setPositiveButton("Delete", (d, which) -> {
                    AppDatabase.databaseWriteExecutor.execute(() -> database.quickReplyDao().delete(item));
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.WHITE);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.WHITE);
        });
        dialog.show();
    }
}

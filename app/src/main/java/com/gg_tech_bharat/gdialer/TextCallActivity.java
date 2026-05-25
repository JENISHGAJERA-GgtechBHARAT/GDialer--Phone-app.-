package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class TextCallActivity extends AppCompatActivity {

    private TextView tvChat;
    private EditText etInput;
    private ImageButton btnSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_call);

        String name = getIntent().getStringExtra("EXTRA_NAME");
        tvChat = findViewById(R.id.tvChatHistory);
        etInput = findViewById(R.id.etTextCallInput);
        btnSend = findViewById(R.id.btnSendTextCall);

        tvChat.setText("Bixby: Hello! I'm an automated assistant. How can I help you?\n\nCaller (" + name + "): I'm looking for the office location.");

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                tvChat.append("\n\nYou: " + text);
                tvChat.append("\n\nBixby (Reading out): " + text);
                etInput.setText("");
            }
        });

        findViewById(R.id.btnEndTextCall).setOnClickListener(v -> finish());
    }
}

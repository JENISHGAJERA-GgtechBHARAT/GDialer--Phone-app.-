package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class PostCallActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_call);

        String name = getIntent().getStringExtra("EXTRA_NAME");
        String number = getIntent().getStringExtra("EXTRA_NUMBER");
        long duration = getIntent().getLongExtra("EXTRA_DURATION", 0);

        TextView tvHeader = findViewById(R.id.tvPostCallHeader);
        TextView tvSummary = findViewById(R.id.tvAiSummary);
        TextView tvReminders = findViewById(R.id.tvAiReminders);

        tvHeader.setText("Call Summary");
        
        String contactLabel = (name != null && !name.isEmpty()) ? name : number;
        tvSummary.setText(String.format("Call with %s\nDuration: %s", contactLabel, Utils.formatDuration(duration)));
        
        tvReminders.setText("Call ended successfully.");

        findViewById(R.id.btnDone).setOnClickListener(v -> finish());
    }
}

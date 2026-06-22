package com.gg_tech_bharat.gdialer;

import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class VoicemailInboxActivity extends AppCompatActivity {

    private static final String SERVER_URL = "https://your-free-backend.com/voicemails";

    /**
     * Performs a background GET request to fetch cloud-hosted voicemail metadata.
     */
    private void fetchCloudVoicemails() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                if (conn.getResponseCode() == 200) {
                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) response.append(line);
                    in.close();

                    JSONArray array = new JSONArray(response.toString());
                    List<VoicemailModel> cloudList = new ArrayList<>();

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        // Parsing Twilio-backed free cloud data
                        String number = obj.getString("from");
                        long time = obj.getLong("timestamp");
                        String mp3Url = obj.getString("url");
                        
                        // Map to internal model for UI rendering
                        cloudList.add(new VoicemailModel(i, number, mp3Url, time, null));
                    }
                    
                    runOnUiThread(() -> updateInboxUI(cloudList));
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e("VoicemailInbox", "Fetch failed: " + e.getMessage());
            }
        }).start();
    }

    private void updateInboxUI(List<VoicemailModel> list) {
        // Implementation for updating your RecyclerView Adapter goes here
    }
}

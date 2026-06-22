package com.gg_tech_bharat.gdialer;

import android.net.Uri;

public class VoicemailModel {
    private long id;
    private String name;
    private String number;
    private long timestamp;
    private Uri uri;

    public VoicemailModel(long id, String name, String number, long timestamp, Uri uri) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.timestamp = timestamp;
        this.uri = uri;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getNumber() { return number; }
    public long getTimestamp() { return timestamp; }
    public Uri getUri() { return uri; }
}

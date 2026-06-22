package com.gg_tech_bharat.gdialer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "voicemails")
public class VoicemailEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String phoneNumber;
    private String contactName;
    private String filePath;
    private long timestamp;
    private long duration;
    private boolean isRead;
    private boolean isStarred;

    public VoicemailEntity(String phoneNumber, String contactName, String filePath, long timestamp, long duration) {
        this.phoneNumber = phoneNumber;
        this.contactName = contactName;
        this.filePath = filePath;
        this.timestamp = timestamp;
        this.duration = duration;
        this.isRead = false;
        this.isStarred = false;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getContactName() { return contactName; }
    public String getFilePath() { return filePath; }
    public long getTimestamp() { return timestamp; }
    public long getDuration() { return duration; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public boolean isStarred() { return isStarred; }
    public void setStarred(boolean starred) { isStarred = starred; }
}

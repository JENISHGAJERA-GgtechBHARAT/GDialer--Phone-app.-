package com.gg_tech_bharat.gdialer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "quick_replies")
public class QuickReplyModel {
    @PrimaryKey(autoGenerate = true)
    private int id;
    private String message;

    public QuickReplyModel(String message) {
        this.message = message;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

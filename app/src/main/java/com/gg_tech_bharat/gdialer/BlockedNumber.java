package com.gg_tech_bharat.gdialer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "blocked_numbers")
public class BlockedNumber {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String number;

    public BlockedNumber(String number) {
        this.number = number;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }
}

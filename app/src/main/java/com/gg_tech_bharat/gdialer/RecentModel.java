package com.gg_tech_bharat.gdialer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "recents")
public class RecentModel {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String number;
    private String name;
    private long timestamp;
    private long duration; // in seconds
    private int callType;  // 1: Incoming, 2: Outgoing, 3: Missed
    private boolean isRecorded;
    private String recordingPath;

    @androidx.room.Ignore
    private int callCount = 1;

    public RecentModel(String number, String name, long timestamp, long duration, int callType, boolean isRecorded, String recordingPath) {
        this.number = number;
        this.name = name;
        this.timestamp = timestamp;
        this.duration = duration;
        this.callType = callType;
        this.isRecorded = isRecorded;
        this.recordingPath = recordingPath;
    }

    // Getters and Setters
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public int getCallType() {
        return callType;
    }

    public void setCallType(int callType) {
        this.callType = callType;
    }

    public boolean isRecorded() {
        return isRecorded;
    }

    public void setRecorded(boolean recorded) {
        isRecorded = recorded;
    }

    public String getRecordingPath() {
        return recordingPath;
    }

    public void setRecordingPath(String recordingPath) {
        this.recordingPath = recordingPath;
    }

    public int getCallCount() {
        return callCount;
    }

    public void setCallCount(int callCount) {
        this.callCount = callCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RecentModel that = (RecentModel) o;
        return id == that.id &&
                timestamp == that.timestamp &&
                duration == that.duration &&
                callType == that.callType &&
                isRecorded == that.isRecorded &&
                java.util.Objects.equals(number, that.number) &&
                java.util.Objects.equals(name, that.name) &&
                java.util.Objects.equals(recordingPath, that.recordingPath);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, number, name, timestamp, duration, callType, isRecorded, recordingPath);
    }
}

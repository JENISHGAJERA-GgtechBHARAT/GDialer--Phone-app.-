package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface RecentDao {

    @Insert
    long insert(RecentModel recent);

    @Update
    void update(RecentModel recent);

    @Delete
    void delete(RecentModel recent);

    @Insert
    void insertAll(List<RecentModel> recents);

    @Query("SELECT * FROM recents ORDER BY timestamp DESC LIMIT 500")
    LiveData<List<RecentModel>> getAllRecents();

    @Query("SELECT * FROM recents ORDER BY timestamp DESC LIMIT 200")
    List<RecentModel> getAllRecentsSync();

    @Query("SELECT * FROM recents WHERE callType = 3 ORDER BY timestamp DESC")
    LiveData<List<RecentModel>> getMissedRecents();

    @Query("SELECT * FROM recents WHERE number = :number ORDER BY timestamp DESC")
    LiveData<List<RecentModel>> getCallHistoryForNumber(String number);

    @Query("SELECT * FROM recents WHERE isRecorded = 1 ORDER BY timestamp DESC")
    LiveData<List<RecentModel>> getRecordedCalls();

    @Query("SELECT * FROM recents WHERE timestamp = :timestamp LIMIT 1")
    RecentModel getRecentByTimestamp(long timestamp);

    @Query("DELETE FROM recents WHERE number = :number")
    void deleteByNumber(String number);
}

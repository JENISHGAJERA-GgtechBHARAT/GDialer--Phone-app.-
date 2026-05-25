package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BlockedNumberDao {

    @Insert
    long insert(BlockedNumber blockedNumber);

    @Delete
    void delete(BlockedNumber blockedNumber);

    @Query("SELECT * FROM blocked_numbers ORDER BY id DESC")
    LiveData<List<BlockedNumber>> getAllBlockedNumbers();

    @Query("SELECT * FROM blocked_numbers WHERE number = :number LIMIT 1")
    BlockedNumber getBlockedByNumber(String number);
}

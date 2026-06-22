package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecentSearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecentSearch search);

    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC LIMIT 10")
    LiveData<List<RecentSearch>> getRecentSearches();

    @Query("DELETE FROM recent_searches")
    void clearAll();
}

package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface QuickReplyDao {
    @Insert
    long insert(QuickReplyModel reply);

    @Update
    void update(QuickReplyModel reply);

    @Delete
    void delete(QuickReplyModel reply);

    @Query("SELECT * FROM quick_replies")
    LiveData<List<QuickReplyModel>> getAllQuickReplies();

    @Query("SELECT * FROM quick_replies")
    List<QuickReplyModel> getAllQuickRepliesSync();
    
    @Query("DELETE FROM quick_replies")
    void deleteAll();
}

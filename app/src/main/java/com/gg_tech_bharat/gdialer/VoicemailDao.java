package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface VoicemailDao {
    @Insert
    long insert(VoicemailEntity voicemail);

    @Update
    void update(VoicemailEntity voicemail);

    @Delete
    void delete(VoicemailEntity voicemail);

    @Query("SELECT * FROM voicemails ORDER BY timestamp DESC")
    LiveData<List<VoicemailEntity>> getAllVoicemails();

    @Query("SELECT * FROM voicemails WHERE isStarred = 1 ORDER BY timestamp DESC")
    LiveData<List<VoicemailEntity>> getStarredVoicemails();

    @Query("SELECT * FROM voicemails WHERE isRead = 0 ORDER BY timestamp DESC")
    LiveData<List<VoicemailEntity>> getUnreadVoicemails();

    @Query("SELECT * FROM voicemails WHERE phoneNumber LIKE :query OR contactName LIKE :query ORDER BY timestamp DESC")
    LiveData<List<VoicemailEntity>> searchVoicemails(String query);

    @Query("SELECT COUNT(*) FROM voicemails WHERE isRead = 0")
    LiveData<Integer> getUnreadCount();

    @Query("DELETE FROM voicemails WHERE id = :id")
    void deleteById(long id);
}

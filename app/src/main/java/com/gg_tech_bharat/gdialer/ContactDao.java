package com.gg_tech_bharat.gdialer;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ContactDao {

    @Insert
    long insert(ContactModel contact);

    @Update
    void update(ContactModel contact);

    @Delete
    void delete(ContactModel contact);

    @Insert
    void insertAll(List<ContactModel> contacts);

    @Update
    void updateAll(List<ContactModel> contacts);

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    LiveData<List<ContactModel>> getAllContacts();

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    List<ContactModel> getAllContactsSync();

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY name ASC")
    LiveData<List<ContactModel>> getFavoriteContacts();

    @Query("SELECT * FROM contacts WHERE name LIKE :searchQuery OR number LIKE :searchQuery ORDER BY name ASC")
    List<ContactModel> searchContactsSync(String searchQuery);

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    ContactModel getContactById(int id);

    @Query("SELECT * FROM contacts WHERE number = :number LIMIT 1")
    ContactModel getContactByNumber(String number);

    @Query("SELECT * FROM contacts WHERE normalizedNumber = :normalizedNumber LIMIT 1")
    ContactModel getContactByNormalizedNumber(String normalizedNumber);

    @Query("SELECT * FROM contacts WHERE normalizedNumber LIKE '%' || :lastDigits LIMIT 1")
    ContactModel getContactByLastDigits(String lastDigits);

    @Query("DELETE FROM contacts WHERE id NOT IN (SELECT MIN(id) FROM contacts GROUP BY normalizedNumber)")
    void deleteDuplicates();

    @Query("DELETE FROM contacts WHERE number = :number")
    void deleteByNumber(String number);
}

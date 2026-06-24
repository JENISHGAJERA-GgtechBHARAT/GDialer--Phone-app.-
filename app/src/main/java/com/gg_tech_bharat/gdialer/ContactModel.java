package com.gg_tech_bharat.gdialer;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "contacts")
public class ContactModel {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String name;
    private String number;
    private String photoUri;
    private boolean isFavorite;
    private boolean isSpam;
    private String notes;
    private boolean needUnlock;

    private String normalizedName;
    private String normalizedNumber;

    public ContactModel(String name, String number, String photoUri, boolean isFavorite, boolean isSpam, String notes) {
        this.name = name;
        this.number = number;
        this.photoUri = photoUri;
        this.isFavorite = isFavorite;
        this.isSpam = isSpam;
        this.notes = notes;
        updateNormalizedFields();
    }

    private void updateNormalizedFields() {
        this.normalizedName = name != null ? name.toLowerCase().replaceAll("[^a-z]", "") : "";
        this.normalizedNumber = number != null ? number.replaceAll("[^0-9]", "") : "";
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; updateNormalizedFields(); }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; updateNormalizedFields(); }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getNormalizedNumber() { return normalizedNumber; }
    public void setNormalizedNumber(String normalizedNumber) { this.normalizedNumber = normalizedNumber; }
    public String getPhotoUri() { return photoUri; }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
    public boolean isFavorite() { return isFavorite; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public boolean isSpam() { return isSpam; }
    public void setSpam(boolean spam) { isSpam = spam; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public boolean isNeedUnlock() { return needUnlock; }
    public void setNeedUnlock(boolean needUnlock) { this.needUnlock = needUnlock; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactModel that = (ContactModel) o;
        return id == that.id && isFavorite == that.isFavorite && isSpam == that.isSpam &&
                java.util.Objects.equals(name, that.name) &&
                java.util.Objects.equals(number, that.number) &&
                java.util.Objects.equals(photoUri, that.photoUri);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id, name, number, photoUri, isFavorite, isSpam, notes);
    }
}

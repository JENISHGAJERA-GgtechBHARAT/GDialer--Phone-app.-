package com.gg_tech_bharat.gdialer;

import java.util.ArrayList;
import java.util.List;

public class ContactCache {

    private static volatile List<ContactModel> sCachedContacts = new ArrayList<>();

    public static synchronized void setCachedContacts(List<ContactModel> contacts) {
        sCachedContacts = contacts != null ? java.util.Collections.unmodifiableList(new ArrayList<>(contacts)) : new ArrayList<>();
    }

    public static List<ContactModel> getCachedContacts() {
        return sCachedContacts;
    }
}

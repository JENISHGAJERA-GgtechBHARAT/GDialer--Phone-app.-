package com.gg_tech_bharat.gdialer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactCache {

    private static volatile Map<String, ContactModel> sCachedContactsMap = new HashMap<>();
    private static volatile List<ContactModel> sCachedList = new ArrayList<>();

    public static synchronized void setCachedContacts(List<ContactModel> contacts) {
        Map<String, ContactModel> newMap = new HashMap<>();
        if (contacts != null) {
            for (ContactModel c : contacts) {
                if (c.getNumber() != null) {
                    newMap.put(Utils.normalizePhoneNumber(c.getNumber()), c);
                }
            }
            sCachedList = java.util.Collections.unmodifiableList(new ArrayList<>(contacts));
        } else {
            sCachedList = new ArrayList<>();
        }
        sCachedContactsMap = newMap;
    }

    public static ContactModel getContactByNumber(String number) {
        if (number == null) return null;
        String norm = Utils.normalizePhoneNumber(number);
        ContactModel c = sCachedContactsMap.get(norm);
        if (c == null && norm.length() >= 10) {
            // Check last 10 digits if full normalized doesn't match
            String last10 = norm.substring(norm.length() - 10);
            for (Map.Entry<String, ContactModel> entry : sCachedContactsMap.entrySet()) {
                if (entry.getKey().endsWith(last10)) return entry.getValue();
            }
        }
        return c;
    }

    public static List<ContactModel> getCachedContacts() {
        return sCachedList;
    }
}

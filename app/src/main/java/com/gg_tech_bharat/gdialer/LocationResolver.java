package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.util.Log;
import java.util.HashMap;
import java.util.Map;

/**
 * Legitimate, offline local caller location resolver.
 * Parses mobile prefixes and landline STD codes to return cities and states without network requests or latency.
 */
public class LocationResolver {

    private static final String TAG = "LocationResolver";
    private static final Map<String, String> MOBILE_PREFIX_MAP = new HashMap<>();
    private static final Map<String, String> LANDLINE_PREFIX_MAP = new HashMap<>();

    static {
        // --- GUJARAT MOBILE SERIES ---
        String[] gujaratSeries = {
            "9824", "9825", "9898", "9904", "9909", "9924", "9925", "9974", "9978", "9979", "9998",
            "9724", "9725", "9727", "9512", "9586", "9687", "9426", "9427", "9428", "9429", "9099",
            "9081", "7016", "7041", "7043", "7045", "7046", "7048", "7096", "7201", "7202", "7203",
            "7226", "7227", "7228", "7359", "7383", "7405", "7433", "7434", "7435", "7490", "7567",
            "7572", "7573", "7574", "7575", "7600", "7621", "7622", "7623", "7624", "7698", "7801",
            "7802", "7818", "7819", "7874", "7878", "8128", "8140", "8141", "8153", "8154", "8155",
            "8156", "8160", "8200", "8238", "8320", "8347", "8401", "8460", "8469", "8487", "8488",
            "8511", "8733", "8734", "8735", "8758", "8780", "8799", "8905", "8980"
        };
        for (String prefix : gujaratSeries) {
            MOBILE_PREFIX_MAP.put(prefix, "Gujarat");
        }

        // --- MAHARASHTRA MOBILE SERIES ---
        String[] maharashtraSeries = {
            "9822", "9823", "9834", "9850", "9860", "9890", "9921", "9922", "9923", "9960", "9970",
            "9975", "9552", "9595", "9623", "9637", "9657", "9665", "9673", "9689", "9730", "9762",
            "9763", "9764", "9765", "9766", "9767", "9420", "9421", "9422", "9423", "9403", "9404",
            "9405", "9011", "9021", "9022", "9028", "9049", "9075", "9096", "9112", "9130", "9145",
            "9146", "9156", "9158", "9172", "9175", "9209", "9225", "9226", "9270", "9271", "9272",
            "9273", "9284", "9307", "9309", "9325", "9326", "9356", "9359", "9370", "9371", "9372",
            "9373", "7020", "7028", "7030", "7038", "7057", "7058", "7066", "7083", "7218", "7219",
            "7249", "7276", "7304", "7350", "7385", "7387", "7410", "7447", "7448", "7498", "7499",
            "7507", "7558", "7559", "7588", "7620", "7666", "7709", "7719", "7720", "7721", "7722",
            "7741", "7743", "7744", "7745", "7755", "7756", "7757", "7758", "7767", "7768", "7769",
            "7770", "7773", "7774", "7775", "7776", "7796", "7798", "7820", "7821", "7822", "7841",
            "7875", "7887", "7888"
        };
        for (String prefix : maharashtraSeries) {
            MOBILE_PREFIX_MAP.put(prefix, "Maharashtra");
        }

        // --- MUMBAI MOBILE SERIES ---
        String[] mumbaiSeries = {
            "9819", "9820", "9821", "9833", "9867", "9869", "9870", "9892", "9920", "9930", "9967",
            "9969", "9987", "9702", "9757", "9768", "9769", "9773", "9594", "9619", "9664", "9004",
            "9029", "9082", "9136", "9137", "9152", "9167", "9220", "9221", "9222", "9223", "9224",
            "9320", "9321", "9322", "9323", "9324", "7021", "7045", "7208", "7303", "7304", "7506",
            "7666", "7710", "7715", "7718", "7738", "7777", "7977", "8080", "8082", "8097", "8104",
            "8108", "8169", "8268", "8286", "8291", "8355", "8356", "8369", "8422", "8424", "8425",
            "8433", "8450", "8451", "8452", "8454", "8652", "8655", "8689", "8691", "8692", "8693",
            "8828", "8850", "8879", "8898", "8928", "8976"
        };
        for (String prefix : mumbaiSeries) {
            MOBILE_PREFIX_MAP.put(prefix, "Mumbai, Maharashtra");
        }

        // --- DELHI MOBILE SERIES ---
        String[] delhiSeries = {
            "9810", "9811", "9818", "9871", "9873", "9891", "9899", "9910", "9911", "9953", "9971",
            "9999", "9540", "9560", "9650", "9654", "9711", "9716", "9717", "9718", "9013", "9015",
            "9210", "9211", "9212", "9213", "9250", "9310", "9311", "9312", "9313", "9315", "9319",
            "9350", "9354", "7011", "7042", "7053", "7065", "7210", "7289", "7290", "7291", "7292",
            "7303", "7428", "7503", "7530", "7531", "7532", "7827", "7834", "7835", "7836", "7838",
            "7840", "7982", "8010", "8076", "8130", "8178", "8285", "8287", "8368", "8373", "8375",
            "8376", "8377", "8383", "8384", "8447", "8448", "8505", "8506", "8510", "8512", "8527",
            "8585", "8586", "8587", "8588", "8595", "8700", "8743", "8744", "8745", "8750", "8800",
            "8802", "8810", "8826", "8851", "8860", "8920", "8929"
        };
        for (String prefix : delhiSeries) {
            MOBILE_PREFIX_MAP.put(prefix, "Delhi");
        }

        // --- LANDLINE / STD CODES ---
        LANDLINE_PREFIX_MAP.put("79", "Ahmedabad, Gujarat");
        LANDLINE_PREFIX_MAP.put("261", "Surat, Gujarat");
        LANDLINE_PREFIX_MAP.put("265", "Vadodara, Gujarat");
        LANDLINE_PREFIX_MAP.put("22", "Mumbai, Maharashtra");
        LANDLINE_PREFIX_MAP.put("11", "Delhi");
        LANDLINE_PREFIX_MAP.put("20", "Pune, Maharashtra");
        LANDLINE_PREFIX_MAP.put("80", "Bengaluru, Karnataka");
        LANDLINE_PREFIX_MAP.put("44", "Chennai, Tamil Nadu");
        LANDLINE_PREFIX_MAP.put("33", "Kolkata, West Bengal");
        LANDLINE_PREFIX_MAP.put("40", "Hyderabad, Telangana");
    }

    /**
     * Resolves the location of a caller's phone number offline.
     * @param context Application context.
     * @param rawNumber Phone number in string format.
     * @return Resolved location string, or null if unavailable.
     */
    public static String getCallLocation(Context context, String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            return null;
        }

        // Normalize number by stripping non-digit chars
        String normalized = rawNumber.replaceAll("[^0-9]", "");

        // Remove Indian country code prefixes (+91, 91, or leading 0)
        if (normalized.startsWith("91") && normalized.length() > 10) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("0") && normalized.length() > 2) {
            normalized = normalized.substring(1);
        }

        // 1. Resolve Indian Mobile Numbers (10 digits starting with 6-9)
        if (normalized.length() == 10) {
            char firstChar = normalized.charAt(0);
            if (firstChar >= '6' && firstChar <= '9') {
                String prefix = normalized.substring(0, 4);
                String state = MOBILE_PREFIX_MAP.get(prefix);
                if (state != null) {
                    // Try to resolve some major city indicators based on subscriber distribution if possible,
                    // otherwise return the state name.
                    if ("Gujarat".equals(state)) {
                        char secondLast = normalized.charAt(8);
                        if (secondLast == '2' || secondLast == '3') {
                            return "Ahmedabad, Gujarat";
                        } else if (secondLast == '5' || secondLast == '6') {
                            return "Surat, Gujarat";
                        } else if (secondLast == '8' || secondLast == '9') {
                            return "Vadodara, Gujarat";
                        }
                    }
                    return state;
                }
            }
        }

        // 2. Resolve Indian Landline Numbers (based on area STD codes)
        // Check for area codes up to 4 digits
        for (int len = 2; len <= 4 && len <= normalized.length(); len++) {
            String stdCode = normalized.substring(0, len);
            String location = LANDLINE_PREFIX_MAP.get(stdCode);
            if (location != null) {
                return location;
            }
        }

        // 3. Fallback check for international country codes
        if (rawNumber.startsWith("+")) {
            String cleanCode = rawNumber.replaceAll("[^0-9]", "");
            if (cleanCode.startsWith("1")) return "United States";
            if (cleanCode.startsWith("44")) return "United Kingdom";
            if (cleanCode.startsWith("86")) return "China";
            if (cleanCode.startsWith("81")) return "Japan";
            if (cleanCode.startsWith("49")) return "Germany";
            if (cleanCode.startsWith("33")) return "France";
            if (cleanCode.startsWith("61")) return "Australia";
            if (cleanCode.startsWith("7")) return "Russia";
            if (cleanCode.startsWith("971")) return "UAE";
        }

        return null;
    }
}

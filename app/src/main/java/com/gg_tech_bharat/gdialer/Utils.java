package com.gg_tech_bharat.gdialer;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Utils {

    private static final android.view.ViewOutlineProvider CIRCULAR_OUTLINE_PROVIDER = 
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP ? 
            new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(android.view.View view, android.graphics.Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            } : null;

    public static void triggerHaptic(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void vibrateDevice(Context context, long durationMs) {
        Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        }
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        long m = seconds / 60;
        long s = seconds % 60;
        return (m > 0) ? m + "m " + s + "s" : s + "s";
    }

    public static String formatTimestamp(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault());
        return sdf.format(new Date(epochMs));
    }

    public static void openWhatsApp(Context context, String phoneNumber) {
        try {
            String cleanNumber = phoneNumber.replaceAll("[^0-9+]", "");
            if (!cleanNumber.startsWith("+") && cleanNumber.length() == 10) cleanNumber = "+91" + cleanNumber;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=" + cleanNumber));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show();
        }
    }

    public static void sendSMS(Context context, String number, String msg) {
        if (number == null || number.isEmpty()) return;
        try {
            String cleanNumber = number.replaceAll("[^0-9+]", "");
            
            if (msg == null || msg.trim().isEmpty()) {
                Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + cleanNumber));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }

            android.telephony.SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = context.getSystemService(android.telephony.SmsManager.class);
            } else {
                smsManager = android.telephony.SmsManager.getDefault();
            }
            
            if (smsManager != null) {
                // Ensure high-priority sending
                smsManager.sendTextMessage(cleanNumber, null, msg, null, null);
                Log.d("Utils", "SMS sent successfully to " + cleanNumber);
            }
        } catch (Exception e) {
            Log.e("Utils", "SMS action failed", e);
        }
    }

    public static void makePhoneCall(Context context, String number) {
        makePhoneCall(context, number, null);
    }

    public static void makePhoneCall(Context context, String number, android.telecom.PhoneAccountHandle handle) {
        makePhoneCall(context, number, handle, VideoProfile.STATE_AUDIO_ONLY);
    }

    public static void makePhoneCall(Context context, String number, android.telecom.PhoneAccountHandle handle, int videoState) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        try {
            TelecomManager tm = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (tm != null && context.getPackageName().equals(tm.getDefaultDialerPackage())) {
                android.os.Bundle extras = new android.os.Bundle();
                if (handle != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
                extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
                tm.placeCall(Uri.parse("tel:" + number), extras);
            } else {
                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (handle != null) intent.putExtra(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, handle);
                intent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
                context.startActivity(intent);
            }
        } catch (Exception e) {
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    public static void loadContactPhoto(Context context, String uri, android.widget.ImageView iv) {
        if (iv == null) return;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            iv.setOutlineProvider(CIRCULAR_OUTLINE_PROVIDER);
            iv.setClipToOutline(true);
        }
        if (uri == null || uri.isEmpty()) {
            iv.setTag(R.id.view_tag_photo_uri, null);
            iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            iv.setBackgroundResource(R.drawable.gray_circle);
            iv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.divider_color)));
            iv.setImageResource(R.drawable.ic_contacts);
            iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
            int paddingPx = (int) (8 * context.getResources().getDisplayMetrics().density);
            iv.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        } else {
            iv.setTag(R.id.view_tag_photo_uri, uri);
            iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            iv.setPadding(0, 0, 0, 0);
            iv.setBackground(null);
            iv.setImageTintList(null);
            com.bumptech.glide.Glide.with(context)
                    .load(uri)
                    .override(480, 480) // High-fidelity size for "clear" images
                    .circleCrop()
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override
                        public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e, 
                                                    Object model, 
                                                    com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                    boolean isFirstResource) {
                            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                                if (uri.equals(iv.getTag(R.id.view_tag_photo_uri))) {
                                    iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                                    iv.setBackgroundResource(R.drawable.gray_circle);
                                    iv.setBackgroundTintList(android.content.res.ColorStateList.valueOf(context.getResources().getColor(R.color.divider_color)));
                                    iv.setImageResource(R.drawable.ic_contacts);
                                    iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                                    int paddingPx = (int) (8 * context.getResources().getDisplayMetrics().density);
                                    iv.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
                                }
                            });
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(android.graphics.drawable.Drawable resource, 
                                                       Object model, 
                                                       com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, 
                                                       com.bumptech.glide.load.DataSource dataSource, 
                                                       boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(iv);
        }
    }

    public static String queryContactName(Context context, String number) {
        if (number == null || number.isEmpty()) return null;
        try {
            android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number));
            String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME};
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String queryContactPhotoUri(Context context, String number) {
        if (number == null || number.isEmpty()) return null;
        try {
            android.net.Uri uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number));
            String[] projection = new String[]{android.provider.ContactsContract.PhoneLookup.PHOTO_URI};
            try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String normalizePhoneNumber(String number) {
        if (number == null) return "";
        return number.replaceAll("[^0-9]", "");
    }
}

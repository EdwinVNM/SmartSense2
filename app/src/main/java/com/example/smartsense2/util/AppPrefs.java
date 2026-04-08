package com.example.smartsense2.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

public final class AppPrefs {

    private static final String PREFS_NAME = "smartsense_prefs";

    private static final String KEY_LIVE_SOURCE_FIREBASE = "live_source_firebase";
    private static final String KEY_AUTO_UPLOAD_ENABLED = "auto_upload_enabled";
    private static final String KEY_MOTOR_MANUAL_MODE = "motor_manual_mode";
    private static final String KEY_LAST_CSV_URI = "last_csv_uri";
    private static final String KEY_LAST_CSV_NAME = "last_csv_name";

    private AppPrefs() {}

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isFirebaseLiveEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_LIVE_SOURCE_FIREBASE, false);
    }

    public static void setFirebaseLiveEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_LIVE_SOURCE_FIREBASE, enabled).apply();
    }

    public static boolean isAutoUploadEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_AUTO_UPLOAD_ENABLED, false);
    }

    public static void setAutoUploadEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_UPLOAD_ENABLED, enabled).apply();
    }

    public static boolean isMotorManualMode(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_MOTOR_MANUAL_MODE, false);
    }

    public static void setMotorManualMode(@NonNull Context context, boolean manualMode) {
        prefs(context).edit().putBoolean(KEY_MOTOR_MANUAL_MODE, manualMode).apply();
    }

    public static void setLastCsv(@NonNull Context context, String uri, String name) {
        prefs(context).edit()
                .putString(KEY_LAST_CSV_URI, uri)
                .putString(KEY_LAST_CSV_NAME, name)
                .apply();
    }

    public static String getLastCsvUri(@NonNull Context context) {
        return prefs(context).getString(KEY_LAST_CSV_URI, null);
    }

    public static String getLastCsvName(@NonNull Context context) {
        return prefs(context).getString(KEY_LAST_CSV_NAME, null);
    }

    public static void clearLastCsv(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_LAST_CSV_URI)
                .remove(KEY_LAST_CSV_NAME)
                .apply();
    }
}

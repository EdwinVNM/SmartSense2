//package com.example.smartsense2.ws;
//
//import android.app.Service;
//import android.os.Build;
//
//public final class ServiceInfoCompat {
//    private ServiceInfoCompat() {}
//
//    public static int FOREGROUND_SERVICE_TYPE_DATA_SYNC() {
//        if (Build.VERSION.SDK_INT >= 34) {
//            return Service.FOREGROUND_SERVICE_TYPE_DATA_SYNC; // required for Android 14+ types [web:329]
//        }
//        return 0;
//    }
//}

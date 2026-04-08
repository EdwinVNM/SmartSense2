package com.example.smartsense2.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class UploadScheduler {

    public static final String UNIQUE_WORK_NAME = "csv_auto_upload_work";

    private UploadScheduler() {}

    public static void startAutoUpload(@NonNull Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(CsvUploadWorker.class, 1, TimeUnit.HOURS)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(
                        UNIQUE_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }

    public static void stopAutoUpload(@NonNull Context context) {
        WorkManager.getInstance(context.getApplicationContext())
                .cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    public static void syncFromPrefs(@NonNull Context context) {
        if (AppPrefs.isAutoUploadEnabled(context)) {
            startAutoUpload(context);
        } else {
            stopAutoUpload(context);
        }
    }
}
//package com.example.smartsense2.util;
//
//import android.content.Context;
//
//import androidx.annotation.NonNull;
//import androidx.work.Constraints;
//import androidx.work.ExistingPeriodicWorkPolicy;
//import androidx.work.NetworkType;
//import androidx.work.PeriodicWorkRequest;
//import androidx.work.WorkManager;
//
//import java.util.concurrent.TimeUnit;
//
//public final class UploadScheduler {
//
//    public static final String UNIQUE_WORK_NAME = "csv_auto_upload_work";
//
//    private UploadScheduler() {}
//
//    public static void startAutoUpload(@NonNull Context context) {
//        Constraints constraints = new Constraints.Builder()
//                .setRequiredNetworkType(NetworkType.CONNECTED)
//                .build();
//
//        PeriodicWorkRequest request =
//                new PeriodicWorkRequest.Builder(CsvUploadWorker.class, 1, TimeUnit.HOURS)
//                        .setConstraints(constraints)
//                        .build();
//
//        WorkManager.getInstance(context.getApplicationContext())
//                .enqueueUniquePeriodicWork(
//                        UNIQUE_WORK_NAME,
//                        ExistingPeriodicWorkPolicy.UPDATE,
//                        request
//                );
//    }
//
//    public static void stopAutoUpload(@NonNull Context context) {
//        WorkManager.getInstance(context.getApplicationContext())
//                .cancelUniqueWork(UNIQUE_WORK_NAME);
//    }
//}

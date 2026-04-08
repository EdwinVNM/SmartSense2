package com.example.smartsense2.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.smartsense2.data.SensorReading;
import com.example.smartsense2.data.SensorRepository;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CsvUploadWorker extends Worker {
    public CsvUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        File temp = null;
        try {
            List<SensorReading> history = SensorRepository.get().historySnapshot();
            if (history == null || history.isEmpty()) return Result.success();

            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String filename = "smartsense_auto_" + ts + ".csv";
            temp = new File(getApplicationContext().getCacheDir(), filename);
            writeCsv(temp, history);

            StorageReference ref = FirebaseStorage.getInstance().getReference().child("csv/auto/" + filename);
            Tasks.await(ref.putFile(android.net.Uri.fromFile(temp)));
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            if (temp != null) temp.delete();
        }
    }

    private void writeCsv(File file, List<SensorReading> readings) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("timestampMs,seq,tempC,humPct\n");
        for (SensorReading r : readings) {
            sb.append(r.timestampMs).append(',').append(r.seq).append(',')
                    .append(String.format(Locale.US, "%.2f", r.tempC)).append(',')
                    .append(String.format(Locale.US, "%.2f", r.humPct)).append('\n');
        }
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
//package com.example.smartsense2.util;
//
//import android.content.Context;
//import android.net.Uri;
//import android.text.TextUtils;
//
//import androidx.annotation.NonNull;
//import androidx.work.Worker;
//import androidx.work.WorkerParameters;
//
//import com.google.android.gms.tasks.Tasks;
//import com.google.firebase.storage.FirebaseStorage;
//import com.google.firebase.storage.StorageReference;
//
//public class CsvUploadWorker extends Worker {
//
//    public CsvUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
//        super(context, workerParams);
//    }
//
//    @NonNull
//    @Override
//    public Result doWork() {
//        try {
//            Context ctx = getApplicationContext();
//
//            String uriString = AppPrefs.getLastCsvUri(ctx);
//            String fileName = AppPrefs.getLastCsvName(ctx);
//
//            if (TextUtils.isEmpty(uriString) || TextUtils.isEmpty(fileName)) {
//                return Result.success();
//            }
//
//            Uri fileUri = Uri.parse(uriString);
//
//            StorageReference root = FirebaseStorage.getInstance().getReference();
//            StorageReference ref = root.child("csv/auto/" + fileName);
//
//            Tasks.await(ref.putFile(fileUri));
//
//            return Result.success();
//        } catch (Exception e) {
//            return Result.retry();
//        }
//    }
//}
////package com.example.smartsense2.util;
////
////import android.content.Context;
////import android.net.Uri;
////
////import androidx.annotation.NonNull;
////import androidx.work.Worker;
////import androidx.work.WorkerParameters;
////
////import com.example.smartsense2.data.SensorReading;
////import com.example.smartsense2.data.SensorRepository;
////import com.google.android.gms.tasks.Tasks;
////import com.google.firebase.storage.FirebaseStorage;
////import com.google.firebase.storage.StorageReference;
////
////import java.io.File;
////import java.io.FileOutputStream;
////import java.nio.charset.StandardCharsets;
////import java.text.SimpleDateFormat;
////import java.util.Date;
////import java.util.List;
////import java.util.Locale;
////
////public class CsvUploadWorker extends Worker {
////
////    public CsvUploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
////        super(context, workerParams);
////    }
////
////    @NonNull
////    @Override
////    public Result doWork() {
////        try {
////            List<SensorReading> history = SensorRepository.get().historySnapshot();
////            if (history == null || history.isEmpty()) {
////                return Result.success();
////            }
////
////            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
////            String filename = "smartsense_auto_" + ts + ".csv";
////
////            File temp = new File(getApplicationContext().getCacheDir(), filename);
////            writeCsv(temp, history);
////
////            Uri fileUri = Uri.fromFile(temp);
////
////            StorageReference root = FirebaseStorage.getInstance().getReference();
////            StorageReference ref = root.child("csv/auto/" + filename);
////
////            Tasks.await(ref.putFile(fileUri));
////
////            // optional: remove temp file after upload
////            //noinspection ResultOfMethodCallIgnored
////            temp.delete();
////
////            return Result.success();
////        } catch (Exception e) {
////            return Result.retry();
////        }
////    }
////
////    private void writeCsv(File file, List<SensorReading> readings) throws Exception {
////        StringBuilder sb = new StringBuilder();
////        sb.append("timestampMs,seq,tempC,humPct\n");
////
////        for (SensorReading r : readings) {
////            sb.append(r.timestampMs).append(",");
////            sb.append(r.seq).append(",");
////            sb.append(String.format(Locale.US, "%.2f", r.tempC)).append(",");
////            sb.append(String.format(Locale.US, "%.2f", r.humPct)).append("\n");
////        }
////
////        try (FileOutputStream fos = new FileOutputStream(file)) {
////            fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
////            fos.flush();
////        }
////    }
////}

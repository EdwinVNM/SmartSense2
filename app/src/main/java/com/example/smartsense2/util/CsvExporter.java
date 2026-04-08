package com.example.smartsense2.util; // change if your package differs

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.example.smartsense2.data.SensorReading;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Exports CSV to the user's Downloads folder using MediaStore (shareable) [web:546][web:550]
public final class CsvExporter {
    private CsvExporter() {}

    /**
     * Exports readings to a CSV in Downloads and returns a content Uri (shareable).
     */
    public static Uri exportToDownloads(Context ctx, List<SensorReading> readings) throws Exception {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String fileName = "smartsense_" + ts + ".csv";

        String csv = buildCsv(readings);

        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/csv");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS); // [web:546]

        ContentResolver resolver = ctx.getContentResolver();
        Uri uri = null; // [web:550]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        }
        if (uri == null) throw new IllegalStateException("MediaStore insert returned null");

        try (OutputStream os = resolver.openOutputStream(uri)) { // [web:546]
            if (os == null) throw new IllegalStateException("openOutputStream returned null");
            os.write(csv.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        return uri;
    }

    private static String buildCsv(List<SensorReading> readings) {
        StringBuilder sb = new StringBuilder();
        sb.append("timestampMs,seq,tempC,humPct\n");

        for (SensorReading r : readings) {
            sb.append(r.timestampMs).append(",");
            sb.append(r.seq).append(",");
            sb.append(String.format(Locale.US, "%.2f", r.tempC)).append(",");
            sb.append(String.format(Locale.US, "%.2f", r.humPct)).append("\n");
        }

        return sb.toString();
    }
}

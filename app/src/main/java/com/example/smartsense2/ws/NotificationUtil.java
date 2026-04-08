package com.example.smartsense2.ws;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.smartsense2.R;

public final class NotificationUtil {

    private NotificationUtil() {}

    public static final String CHANNEL_ID = "ws_server_channel";

    public static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Server",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps the WebSocket server running in the background");

            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    public static Notification buildNotification(Context ctx, String text) {
        return new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("SmartSense server")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

//    public static void updateNotification(Context ctx, int id, Notification notif) {
//        NotificationManagerCompat.from(ctx).notify(id, notif);
//    }
    public static void updateNotification(Context ctx, int id, Notification notif) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return; // no permission -> skip updating notification
            }
        }
        NotificationManagerCompat.from(ctx).notify(id, notif);
    }

}

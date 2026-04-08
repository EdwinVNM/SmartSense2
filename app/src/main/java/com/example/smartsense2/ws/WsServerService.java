package com.example.smartsense2.ws;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import java.net.InetSocketAddress;

public class WsServerService extends Service implements MyWebSocketServer.Listener {

    private static final String TAG = "WsServerService";
    public static final int NOTIF_ID = 1001;
    public static final int WS_PORT = 8080;

    private MyWebSocketServer server;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationUtil.createChannel(this);

        // Always use the simple overload: avoids API-34 typed startForeground compile issues. [web:392]
        startForeground(NOTIF_ID, NotificationUtil.buildNotification(this, "Starting server…"));

        startServer();
    }

    private void startServer() {
        try {
            server = new MyWebSocketServer(new InetSocketAddress(WS_PORT), this);
            server.start();
            Log.d(TAG, "WebSocket server start() called on port " + WS_PORT);

            NotificationUtil.updateNotification(
                    this,
                    NOTIF_ID,
                    NotificationUtil.buildNotification(this, "Server running on port " + WS_PORT)
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WS server", e);
            NotificationUtil.updateNotification(
                    this,
                    NOTIF_ID,
                    NotificationUtil.buildNotification(this, "Server failed: " + e.getClass().getSimpleName())
            );
        }
    }

    private void stopServer() {
        try {
            if (server != null) {
                server.stop();
                server = null;
                Log.d(TAG, "WebSocket server stopped");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping server", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopServer();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ===== callbacks from MyWebSocketServer =====
    @Override
    public void onClientConnected(String address) {
        NotificationUtil.updateNotification(
                this,
                NOTIF_ID,
                NotificationUtil.buildNotification(this, "Client connected: " + address)
        );
    }

    @Override
    public void onClientDisconnected(String address) {
        NotificationUtil.updateNotification(
                this,
                NOTIF_ID,
                NotificationUtil.buildNotification(this, "Client disconnected: " + address)
        );
    }

    @Override
    public void onTextMessage(String msg) {
        // Keep short to avoid notification spam
        NotificationUtil.updateNotification(
                this,
                NOTIF_ID,
                NotificationUtil.buildNotification(this, "Last msg received")
        );
    }
}

package com.example.smartsense2.ws;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.java_websocket.WebSocket;

public final class WsServerHub {

    private static WsServerHub INSTANCE;

    public static synchronized WsServerHub get() {
        if (INSTANCE == null) INSTANCE = new WsServerHub();
        return INSTANCE;
    }

    private final MutableLiveData<Boolean> hasClient = new MutableLiveData<>(false);

    private WebSocket activeClient; // CYD connection (last connected)

    private WsServerHub() {}

    public LiveData<Boolean> hasClient() {
        return hasClient;
    }

    public synchronized void setActiveClient(WebSocket conn) {
        activeClient = conn;
        hasClient.postValue(conn != null && conn.isOpen());
    }

    public synchronized void clearClient(WebSocket conn) {
        if (activeClient == conn) {
            activeClient = null;
            hasClient.postValue(false);
        }
    }

    public synchronized boolean sendToCyd(String text) {
        if (activeClient == null) return false;
        if (!activeClient.isOpen()) return false;
        activeClient.send(text);
        return true;
    }
}

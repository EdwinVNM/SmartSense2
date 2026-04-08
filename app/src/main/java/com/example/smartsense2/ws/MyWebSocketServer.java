package com.example.smartsense2.ws;

import android.util.Log;

import com.example.smartsense2.data.SensorReading;
import com.example.smartsense2.data.SensorRepository;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class MyWebSocketServer extends WebSocketServer {
    private static final String TAG = "MyWSServer";

    public interface Listener {
        void onClientConnected(String address);
        void onClientDisconnected(String address);
        void onTextMessage(String msg);
    }

    private final Listener listener;

    public MyWebSocketServer(InetSocketAddress address, Listener listener) {
        super(address);
        this.listener = listener;
        setConnectionLostTimeout(60);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String addr = conn.getRemoteSocketAddress().getAddress().getHostAddress();
        Log.d(TAG, "Client connected: " + addr);
        Log.d(TAG, "Resource: " + handshake.getResourceDescriptor());

        WsServerHub.get().setActiveClient(conn);

        if (listener != null) listener.onClientConnected(addr);
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String addr = (conn != null && conn.getRemoteSocketAddress() != null)
                ? conn.getRemoteSocketAddress().getAddress().getHostAddress()
                : "unknown";
        Log.d(TAG, "Client disconnected: " + addr + " code=" + code + " reason=" + reason);

        WsServerHub.get().clearClient(conn);

        if (listener != null) listener.onClientDisconnected(addr);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.d(TAG, "Message: " + message);

        if (listener != null) listener.onTextMessage(message);

        try {
            SensorReading reading = WsMessageParser.parseSensorMessage(message);
            if (reading != null) {
                SensorRepository.get().addFromWebSocket(reading);
            }
        } catch (Exception e) {
            Log.e(TAG, "Parse error", e);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e(TAG, "Error", ex);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "Server started on port " + getPort());
    }
}
//package com.example.smartsense2.ws;
//
//import android.util.Log;
//
//import com.example.smartsense2.data.SensorReading;
//import com.example.smartsense2.data.SensorRepository;
//import com.example.smartsense2.ws.WsServerHub;
//
//import org.java_websocket.WebSocket;
//import org.java_websocket.handshake.ClientHandshake;
//import org.java_websocket.server.WebSocketServer;
//
//import java.net.InetSocketAddress;
//
//public class MyWebSocketServer extends WebSocketServer {
//    private static final String TAG = "MyWSServer";
//
//    public interface Listener {
//        void onClientConnected(String address);
//        void onClientDisconnected(String address);
//        void onTextMessage(String msg);
//    }
//
//    private final Listener listener;
//
//    public MyWebSocketServer(InetSocketAddress address, Listener listener) {
//        super(address);
//        this.listener = listener;
//
//        // Important: Java-WebSocket expects SECONDS, not ms.
//        setConnectionLostTimeout(60); // 60 seconds
//    }
//
////    @Override
////    public void onOpen(WebSocket conn, ClientHandshake handshake) {
////        String addr = conn.getRemoteSocketAddress().getAddress().getHostAddress();
////        Log.d(TAG, "Client connected: " + addr);
////        Log.d(TAG, "Resource: " + handshake.getResourceDescriptor());
////        if (listener != null) listener.onClientConnected(addr);
////    }
////
////    @Override
////    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
////        String addr = (conn != null && conn.getRemoteSocketAddress() != null)
////                ? conn.getRemoteSocketAddress().getAddress().getHostAddress()
////                : "unknown";
////        Log.d(TAG, "Client disconnected: " + addr + " code=" + code + " reason=" + reason);
////        if (listener != null) listener.onClientDisconnected(addr);
////    }
//
//    @Override
//    public void onOpen(WebSocket conn, ClientHandshake handshake) {
//        String addr = conn.getRemoteSocketAddress().getAddress().getHostAddress();
//        Log.d(TAG, "Client connected: " + addr);
//        Log.d(TAG, "Resource: " + handshake.getResourceDescriptor());
//
//        WsServerHub.get().setActiveClient(conn);
//
//        if (listener != null) listener.onClientConnected(addr);
//    }
//
//    @Override
//    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
//        String addr = (conn != null && conn.getRemoteSocketAddress() != null)
//                ? conn.getRemoteSocketAddress().getAddress().getHostAddress()
//                : "unknown";
//        Log.d(TAG, "Client disconnected: " + addr + " code=" + code + " reason=" + reason);
//
//        WsServerHub.get().clearClient(conn);
//
//        if (listener != null) listener.onClientDisconnected(addr);
//    }
//
//
//    //    @Override
////    public void onMessage(WebSocket conn, String message) {
////        Log.d(TAG, "Message: " + message);
////        if (listener != null) listener.onTextMessage(message);
////    }
//    @Override
//    public void onMessage(WebSocket conn, String message) {
//        Log.d(TAG, "Message: " + message);
//
//        // Existing UI callback
//        if (listener != null) listener.onTextMessage(message);
//
//        // NEW: parse + store
//        try {
//            SensorReading reading = WsMessageParser.parseSensorMessage(message);
//            if (reading != null) {
////                SensorRepository.get().add(reading);
//                SensorRepository.get().addFromWebSocket(reading);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "Parse error", e);
//        }
//    }
//
//
//    @Override
//    public void onError(WebSocket conn, Exception ex) {
//        Log.e(TAG, "Error", ex);
//    }
//
//    @Override
//    public void onStart() {
//        Log.d(TAG, "Server started on port " + getPort());
//    }
//}

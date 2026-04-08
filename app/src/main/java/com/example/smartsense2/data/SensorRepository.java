package com.example.smartsense2.data;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SensorRepository {

    public enum LiveSource {
        WEBSOCKET,
        FIREBASE
    }

    private static SensorRepository INSTANCE;

    public static synchronized SensorRepository get() {
        if (INSTANCE == null) INSTANCE = new SensorRepository();
        return INSTANCE;
    }

    private final MutableLiveData<SensorReading> latest = new MutableLiveData<>();
    private final MutableLiveData<LiveSource> liveSource = new MutableLiveData<>(LiveSource.WEBSOCKET);
    private final MutableLiveData<Long> resetVersion = new MutableLiveData<>(0L);

    private final ArrayList<SensorReading> history = new ArrayList<>();
    private static final int MAX_POINTS = 600;

    private final DatabaseReference liveRef;
    private ValueEventListener firebaseListener;

    private long lastFirebaseSeq = -1;
    private long lastAcceptedSeq = -1;

    private SensorRepository() {
        liveRef = FirebaseDatabase.getInstance().getReference("live");
    }

    public LiveData<SensorReading> latest() {
        return latest;
    }

    public LiveData<LiveSource> liveSource() {
        return liveSource;
    }

    public LiveData<Long> resetVersion() {
        return resetVersion;
    }

    public LiveSource getCurrentSource() {
        LiveSource value = liveSource.getValue();
        return value != null ? value : LiveSource.WEBSOCKET;
    }

    public synchronized List<SensorReading> historySnapshot() {
        return Collections.unmodifiableList(new ArrayList<>(history));
    }

    public synchronized void add(SensorReading r) {
        if (r == null) return;

        if (r.seq > 0 && r.seq == lastAcceptedSeq) {
            return;
        }
        if (r.seq > 0) {
            lastAcceptedSeq = r.seq;
        }

        history.add(r);
        if (history.size() > MAX_POINTS) {
            history.remove(0);
        }
        latest.postValue(r);
    }

    public synchronized void clearHistory() {
        history.clear();
        lastAcceptedSeq = -1;
        resetVersion.postValue(System.currentTimeMillis());
    }

    public void setLiveSource(LiveSource source) {
        LiveSource current = getCurrentSource();
        if (current == source) return;

        liveSource.postValue(source);
        clearHistory();

        if (source == LiveSource.FIREBASE) {
            startFirebaseListening();
        } else {
            stopFirebaseListening();
            lastFirebaseSeq = -1;
        }
    }

    public void addFromWebSocket(SensorReading r) {
        if (getCurrentSource() != LiveSource.WEBSOCKET) return;
        add(r);
    }

    private void startFirebaseListening() {
        if (firebaseListener != null) return;

        firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (getCurrentSource() != LiveSource.FIREBASE) return;
                if (!snapshot.exists()) return;

                Float temp = getFloat(snapshot, "temperatureC");
                Float hum = getFloat(snapshot, "humidity");
                Long seq = getLong(snapshot, "seq");
                Long updatedMs = getLong(snapshot, "updatedMs");

                if (temp == null || hum == null || seq == null || updatedMs == null) return;
                if (seq <= lastFirebaseSeq) return;

                lastFirebaseSeq = seq;

                SensorReading reading = new SensorReading(
                        updatedMs,
                        temp,
                        hum,
                        seq
                );

                add(reading);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };

        liveRef.addValueEventListener(firebaseListener);
    }

    private void stopFirebaseListening() {
        if (firebaseListener != null) {
            liveRef.removeEventListener(firebaseListener);
            firebaseListener = null;
        }
    }

    private Float getFloat(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value instanceof Long) return ((Long) value).floatValue();
        if (value instanceof Integer) return ((Integer) value).floatValue();
        if (value instanceof Double) return ((Double) value).floatValue();
        if (value instanceof Float) return (Float) value;
        return null;
    }

    private Long getLong(DataSnapshot snapshot, String key) {
        Object value = snapshot.child(key).getValue();
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        return null;
    }
}
//package com.example.smartsense2.data;
//
//import androidx.annotation.NonNull;
//import androidx.lifecycle.LiveData;
//import androidx.lifecycle.MutableLiveData;
//
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.ValueEventListener;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//public class SensorRepository {
//
//    public enum LiveSource {
//        WEBSOCKET,
//        FIREBASE
//    }
//
//    private static SensorRepository INSTANCE;
//
//    public static synchronized SensorRepository get() {
//        if (INSTANCE == null) INSTANCE = new SensorRepository();
//        return INSTANCE;
//    }
//
//    private final MutableLiveData<SensorReading> latest = new MutableLiveData<>();
//    private final MutableLiveData<LiveSource> liveSource = new MutableLiveData<>(LiveSource.WEBSOCKET);
//    private final ArrayList<SensorReading> history = new ArrayList<>();
//
//    private static final int MAX_POINTS = 600;
//
//    private final DatabaseReference liveRef;
//    private ValueEventListener firebaseListener;
//
//    private long lastFirebaseSeq = -1;
//
//    private SensorRepository() {
//        liveRef = FirebaseDatabase.getInstance().getReference("live");
//    }
//
//    public LiveData<SensorReading> latest() {
//        return latest;
//    }
//
//    public LiveData<LiveSource> liveSource() {
//        return liveSource;
//    }
//
//    public synchronized List<SensorReading> historySnapshot() {
//        return Collections.unmodifiableList(new ArrayList<>(history));
//    }
//
//    public synchronized void add(SensorReading r) {
//        history.add(r);
//        if (history.size() > MAX_POINTS) {
//            history.remove(0);
//        }
//        latest.postValue(r);
//    }
//
//    public synchronized void clearHistory() {
//        history.clear();
//    }
//
//    public void setLiveSource(LiveSource source) {
//        LiveSource current = getCurrentSource();
//        if (current == source) return;
//
//        liveSource.postValue(source);
//
//        if (source == LiveSource.FIREBASE) {
//            startFirebaseListening();
//        } else {
//            stopFirebaseListening();
//        }
//    }
//
//    public LiveSource getCurrentSource() {
//        LiveSource value = liveSource.getValue();
//        return value != null ? value : LiveSource.WEBSOCKET;
//    }
//
//    public void addFromWebSocket(SensorReading r) {
//        if (getCurrentSource() != LiveSource.WEBSOCKET) return;
//        add(r);
//    }
//
//    private void startFirebaseListening() {
//        if (firebaseListener != null) return;
//
//        firebaseListener = new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot snapshot) {
//                if (getCurrentSource() != LiveSource.FIREBASE) return;
//                if (!snapshot.exists()) return;
//
////                Float temp = snapshot.child("temperatureC").getValue(Float.class);
////                Float hum = snapshot.child("humidity").getValue(Float.class);
////                Long seq = snapshot.child("seq").getValue(Long.class);
////                Long updatedMs = snapshot.child("updatedMs").getValue(Long.class);
//                Float temp = getFloat(snapshot, "temperatureC");
//                Float hum = getFloat(snapshot, "humidity");
//                Long seq = getLong(snapshot, "seq");
//                Long updatedMs = getLong(snapshot, "updatedMs");
//
//                if (temp == null || hum == null || seq == null || updatedMs == null) return;
//                if (seq <= lastFirebaseSeq) return;
//
//                lastFirebaseSeq = seq;
//
//                SensorReading reading = new SensorReading(
//                        updatedMs,
//                        temp,
//                        hum,
//                        seq
//                );
//
//                add(reading);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//            }
//        };
//
//        liveRef.addValueEventListener(firebaseListener);
//    }
//
//    private void stopFirebaseListening() {
//        if (firebaseListener != null) {
//            liveRef.removeEventListener(firebaseListener);
//            firebaseListener = null;
//        }
//    }
//    private Float getFloat(DataSnapshot snapshot, String key) {
//        Object value = snapshot.child(key).getValue();
//        if (value instanceof Long) return ((Long) value).floatValue();
//        if (value instanceof Integer) return ((Integer) value).floatValue();
//        if (value instanceof Double) return ((Double) value).floatValue();
//        if (value instanceof Float) return (Float) value;
//        return null;
//    }
//
//    private Long getLong(DataSnapshot snapshot, String key) {
//        Object value = snapshot.child(key).getValue();
//        if (value instanceof Long) return (Long) value;
//        if (value instanceof Integer) return ((Integer) value).longValue();
//        if (value instanceof Double) return ((Double) value).longValue();
//        return null;
//    }
//}
////package com.example.smartsense2.data;
////
////import androidx.lifecycle.LiveData;
////import androidx.lifecycle.MutableLiveData;
////
////import java.util.ArrayList;
////import java.util.Collections;
////import java.util.List;
////
////public class SensorRepository {
////
////    private static SensorRepository INSTANCE;
////
////    public static synchronized SensorRepository get() {
////        if (INSTANCE == null) INSTANCE = new SensorRepository();
////        return INSTANCE;
////    }
////
////    private final MutableLiveData<SensorReading> latest = new MutableLiveData<>();
////    private final ArrayList<SensorReading> history = new ArrayList<>();
////
////    // keep memory bounded (chart will use this)
////    private static final int MAX_POINTS = 600; // e.g., 600 points = 50 min at 5s
////
////    private SensorRepository() {}
////
////    public LiveData<SensorReading> latest() {
////        return latest;
////    }
////
////    public synchronized List<SensorReading> historySnapshot() {
////        return Collections.unmodifiableList(new ArrayList<>(history));
////    }
////
////    public synchronized void add(SensorReading r) {
////        history.add(r);
////        if (history.size() > MAX_POINTS) {
////            history.remove(0);
////        }
////        latest.postValue(r);
////    }
////
////    public synchronized void clearHistory() {
////        history.clear();
////    }
////}

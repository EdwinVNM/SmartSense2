package com.example.smartsense2.data;

public class SensorReading {
    public final long timestampMs;
    public final float tempC;
    public final float humPct;
    public final long seq;

    public SensorReading(long timestampMs, float tempC, float humPct, long seq) {
        this.timestampMs = timestampMs;
        this.tempC = tempC;
        this.humPct = humPct;
        this.seq = seq;
    }
}

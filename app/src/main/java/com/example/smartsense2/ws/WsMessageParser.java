package com.example.smartsense2.ws;

import com.example.smartsense2.data.SensorReading;

import org.json.JSONObject;

public final class WsMessageParser {
    private WsMessageParser() {}

    // Expects: {"event":"sensor","temp":22.7,"hum":53.0,"seq":12}
    public static SensorReading parseSensorMessage(String msg) throws Exception {
        JSONObject o = new JSONObject(msg);

        String event = o.optString("event", "");
        if (!"sensor".equals(event)) return null;

        float t = (float) o.getDouble("temp");
        float h = (float) o.getDouble("hum");
        long seq = o.optLong("seq", -1);

        return new SensorReading(System.currentTimeMillis(), t, h, seq);
    }
}

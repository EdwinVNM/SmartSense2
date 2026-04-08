package com.example.smartsense2.ui.control;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartsense2.R;
import com.example.smartsense2.util.AppPrefs;
import com.example.smartsense2.ws.WsServerHub;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.util.Locale;

public class ControlFragment extends Fragment {

    private TextView txtConn;

    private SwitchMaterial swMode;
    private TextView txtModeStatus;

    private SeekBar seekThreshold;
    private TextView txtThreshold;

    private EditText edtCydMessage;
    private Button btnSendMsg;

    private RadioButton rbStop, rbFwd, rbRev;
    private SeekBar seekSpeed;
    private TextView txtSpeed;
    private Button btnSendMotor;

    private boolean isManualMode = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_control, container, false);

        txtConn = v.findViewById(R.id.txtConn);

        swMode = v.findViewById(R.id.swMode);
        txtModeStatus = v.findViewById(R.id.txtModeStatus);

        seekThreshold = v.findViewById(R.id.seekThreshold);
        txtThreshold = v.findViewById(R.id.txtThreshold);

        edtCydMessage = v.findViewById(R.id.edtCydMessage);
        btnSendMsg = v.findViewById(R.id.btnSendMsg);

        rbStop = v.findViewById(R.id.rbStop);
        rbFwd  = v.findViewById(R.id.rbFwd);
        rbRev  = v.findViewById(R.id.rbRev);

        seekSpeed = v.findViewById(R.id.seekSpeed);
        txtSpeed = v.findViewById(R.id.txtSpeed);
        btnSendMotor = v.findViewById(R.id.btnSendMotor);

        WsServerHub.get().hasClient().observe(getViewLifecycleOwner(), connected -> {
            boolean ok = connected != null && connected;
            txtConn.setText(ok ? "CYD: connected" : "CYD: disconnected");
        });

        boolean savedManualMode = AppPrefs.isMotorManualMode(requireContext());
        isManualMode = savedManualMode;
        swMode.setChecked(savedManualMode);
        applyModeUi(savedManualMode);

        swMode.setOnCheckedChangeListener((buttonView, checked) -> {
            isManualMode = checked;
            AppPrefs.setMotorManualMode(requireContext(), checked);
            applyModeUi(checked);
            sendModeCommand(checked);
        });

        seekThreshold.setMax(100);
        seekThreshold.setProgress(40);
        updateThresholdLabel(seekThreshold.getProgress());

        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateThresholdLabel(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                float thr = seekBar.getProgress() / 2.0f;
                sendThresholdCommand(thr);
            }
        });

        txtSpeed.setText("Speed: " + seekSpeed.getProgress());
        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                txtSpeed.setText("Speed: " + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSendMsg.setOnClickListener(view -> sendCydMessage());
        btnSendMotor.setOnClickListener(view -> sendMotorCommand());

        return v;
    }

    private void applyModeUi(boolean manual) {
        swMode.setText(manual ? "MANUAL" : "AUTO");
        txtModeStatus.setText(manual ? "Mode: MANUAL (override)" : "Mode: AUTO");

        rbStop.setEnabled(manual);
        rbFwd.setEnabled(manual);
        rbRev.setEnabled(manual);
        seekSpeed.setEnabled(manual);
        btnSendMotor.setEnabled(manual);

        seekThreshold.setEnabled(true);
    }

    private void updateThresholdLabel(int progress) {
        float thr = progress / 2.0f;
        txtThreshold.setText(String.format(Locale.US, "Auto threshold: %.1f°C", thr));
    }

    private void sendModeCommand(boolean manual) {
        try {
            JSONObject o = new JSONObject();
            o.put("cmd", "motor_mode");
            o.put("mode", manual ? "MANUAL" : "AUTO");

            boolean ok = WsServerHub.get().sendToCyd(o.toString());
            Toast.makeText(requireContext(), ok ? "Mode sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Mode error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendThresholdCommand(float thresholdC) {
        try {
            JSONObject o = new JSONObject();
            o.put("cmd", "auto_threshold");
            o.put("threshold", thresholdC);

            boolean ok = WsServerHub.get().sendToCyd(o.toString());
            Toast.makeText(requireContext(), ok ? "Threshold sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Threshold error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendCydMessage() {
        String msg = edtCydMessage.getText().toString().trim();
        if (msg.isEmpty()) {
            Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject o = new JSONObject();
            o.put("cmd", "cyd_text");
            o.put("text", msg);

            boolean ok = WsServerHub.get().sendToCyd(o.toString());
            Toast.makeText(requireContext(), ok ? "Sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Msg error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendMotorCommand() {
        if (!isManualMode) {
            Toast.makeText(requireContext(), "Switch to MANUAL to send motor commands", Toast.LENGTH_SHORT).show();
            return;
        }

        String dir = "STOP";
        if (rbFwd.isChecked()) dir = "FWD";
        else if (rbRev.isChecked()) dir = "REV";

        int speed = seekSpeed.getProgress();

        try {
            JSONObject o = new JSONObject();
            o.put("cmd", "motor");
            o.put("dir", dir);
            o.put("speed", speed);

            boolean ok = WsServerHub.get().sendToCyd(o.toString());
            Toast.makeText(requireContext(), ok ? "Sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Motor error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
//package com.example.smartsense2.ui.control;
//
//import android.os.Bundle;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.RadioButton;
//import android.widget.SeekBar;
//import android.widget.TextView;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//
//import com.example.smartsense2.R;
//import com.example.smartsense2.ws.WsServerHub;
//import com.google.android.material.switchmaterial.SwitchMaterial; // correct [web:700]
//
//import org.json.JSONObject;
//
//import java.util.Locale;
//
//public class ControlFragment extends Fragment {
//
//    private TextView txtConn;
//
//    private SwitchMaterial swMode;
//    private TextView txtModeStatus;
//
//    private SeekBar seekThreshold;
//    private TextView txtThreshold;
//
//    private EditText edtCydMessage;
//    private Button btnSendMsg;
//
//    private RadioButton rbStop, rbFwd, rbRev;
//    private SeekBar seekSpeed;
//    private TextView txtSpeed;
//    private Button btnSendMotor;
//
//    private boolean isManualMode = false;
//
//    @Nullable
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             @Nullable ViewGroup container,
//                             @Nullable Bundle savedInstanceState) {
//        View v = inflater.inflate(R.layout.fragment_control, container, false);
//
//        txtConn = v.findViewById(R.id.txtConn);
//
//        swMode = v.findViewById(R.id.swMode);
//        txtModeStatus = v.findViewById(R.id.txtModeStatus);
//
//        seekThreshold = v.findViewById(R.id.seekThreshold);
//        txtThreshold = v.findViewById(R.id.txtThreshold);
//
//        edtCydMessage = v.findViewById(R.id.edtCydMessage);
//        btnSendMsg = v.findViewById(R.id.btnSendMsg);
//
//        rbStop = v.findViewById(R.id.rbStop);
//        rbFwd  = v.findViewById(R.id.rbFwd);
//        rbRev  = v.findViewById(R.id.rbRev);
//
//        seekSpeed = v.findViewById(R.id.seekSpeed);
//        txtSpeed = v.findViewById(R.id.txtSpeed);
//        btnSendMotor = v.findViewById(R.id.btnSendMotor);
//
//        WsServerHub.get().hasClient().observe(getViewLifecycleOwner(), connected -> {
//            boolean ok = connected != null && connected;
//            txtConn.setText(ok ? "CYD: connected" : "CYD: disconnected");
//        });
//
//        // ===== Mode switch: OFF=AUTO, ON=MANUAL =====
//        swMode.setChecked(false);
//        applyModeUi(false);
//
//        swMode.setOnCheckedChangeListener((buttonView, checked) -> {
//            isManualMode = checked;
//            applyModeUi(checked);
//            sendModeCommand(checked);
//        });
//
//        // ===== Threshold: 0..100 => 0.0..50.0C in 0.5 steps =====
//        seekThreshold.setMax(100);
//        seekThreshold.setProgress(40); // 20.0C default
//        updateThresholdLabel(seekThreshold.getProgress());
//
//        seekThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                updateThresholdLabel(progress);
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
//
//            // send only when released [web:653]
//            @Override public void onStopTrackingTouch(SeekBar seekBar) {
//                float thr = seekBar.getProgress() / 2.0f;
//                sendThresholdCommand(thr);
//            }
//        });
//
//        // ===== Motor speed slider =====
//        txtSpeed.setText("Speed: " + seekSpeed.getProgress());
//        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                txtSpeed.setText("Speed: " + progress);
//            }
//            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
//            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
//
//        btnSendMsg.setOnClickListener(view -> sendCydMessage());
//        btnSendMotor.setOnClickListener(view -> sendMotorCommand());
//
//        return v;
//    }
//
//    private void applyModeUi(boolean manual) {
//        swMode.setText(manual ? "MANUAL" : "AUTO");
//        txtModeStatus.setText(manual ? "Mode: MANUAL (override)" : "Mode: AUTO");
//
//        // Disable motor controls in AUTO
//        rbStop.setEnabled(manual);
//        rbFwd.setEnabled(manual);
//        rbRev.setEnabled(manual);
//        seekSpeed.setEnabled(manual);
//        btnSendMotor.setEnabled(manual);
//
//        // (Optional) allow threshold changes in both modes; you can change this if you want
//        seekThreshold.setEnabled(true);
//    }
//
//    private void updateThresholdLabel(int progress) {
//        float thr = progress / 2.0f;
//        txtThreshold.setText(String.format(Locale.US, "Auto threshold: %.1f°C", thr));
//    }
//
//    private void sendModeCommand(boolean manual) {
//        try {
//            JSONObject o = new JSONObject();
//            o.put("cmd", "motor_mode");
//            o.put("mode", manual ? "MANUAL" : "AUTO");
//
//            boolean ok = WsServerHub.get().sendToCyd(o.toString());
//            Toast.makeText(requireContext(), ok ? "Mode sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(requireContext(), "Mode error: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//    }
//
//    private void sendThresholdCommand(float thresholdC) {
//        try {
//            JSONObject o = new JSONObject();
//            o.put("cmd", "auto_threshold");
//            o.put("threshold", thresholdC); // JSONObject supports put(key, float/double) [web:699]
//
//            boolean ok = WsServerHub.get().sendToCyd(o.toString());
//            Toast.makeText(requireContext(), ok ? "Threshold sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(requireContext(), "Threshold error: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//    }
//
//    private void sendCydMessage() {
//        String msg = edtCydMessage.getText().toString().trim();
//        if (msg.isEmpty()) {
//            Toast.makeText(requireContext(), "Enter a message", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        try {
//            JSONObject o = new JSONObject();
//            o.put("cmd", "cyd_text");
//            o.put("text", msg);
//
//            boolean ok = WsServerHub.get().sendToCyd(o.toString());
//            Toast.makeText(requireContext(), ok ? "Sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(requireContext(), "Msg error: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//    }
//
//    private void sendMotorCommand() {
//        if (!isManualMode) {
//            Toast.makeText(requireContext(), "Switch to MANUAL to send motor commands", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        String dir = "STOP";
//        if (rbFwd.isChecked()) dir = "FWD";
//        else if (rbRev.isChecked()) dir = "REV";
//
//        int speed = seekSpeed.getProgress();
//
//        try {
//            JSONObject o = new JSONObject();
//            o.put("cmd", "motor");
//            o.put("dir", dir);
//            o.put("speed", speed);
//
//            boolean ok = WsServerHub.get().sendToCyd(o.toString());
//            Toast.makeText(requireContext(), ok ? "Sent" : "No CYD connected", Toast.LENGTH_SHORT).show();
//        } catch (Exception e) {
//            Toast.makeText(requireContext(), "Motor error: " + e.getMessage(), Toast.LENGTH_LONG).show();
//        }
//    }
//}

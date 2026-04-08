package com.example.smartsense2.ui.hum;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ekn.gruzer.gaugelibrary.HalfGauge;
import com.ekn.gruzer.gaugelibrary.Range;
import com.example.smartsense2.R;
import com.example.smartsense2.data.SensorRepository;

import java.util.Locale;
import java.util.Random;

public class HumGaugeFragment extends Fragment {

    private static final double MIN_HUM = 0.0;
    private static final double MAX_HUM = 100.0;

    private HalfGauge humGauge;
    private TextView humValueText;
    private Button btnResetHum;

    private final Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_hum_gauge, container, false);

        humGauge = v.findViewById(R.id.humGauge);
        humValueText = v.findViewById(R.id.humValueText);
        btnResetHum = v.findViewById(R.id.btnResetHum);

        setupGauge();

        btnResetHum.setOnClickListener(view -> {
            double fake = MIN_HUM + (MAX_HUM - MIN_HUM) * random.nextDouble();
            setHumUi((float) fake, true);
        });

        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
            if (reading == null) return;
            setHumUi(reading.humPct, false);
        });

        return v;
    }

    private void setupGauge() {
        // humidity “comfort-ish” coloring (dry -> ok -> humid)
        Range dry = new Range();
        dry.setColor(Color.parseColor("#F44336")); // red
        dry.setFrom(MIN_HUM);
        dry.setTo(30.0);

        Range ok = new Range();
        ok.setColor(Color.parseColor("#4CAF50"));  // green
        ok.setFrom(30.0);
        ok.setTo(60.0);

        Range humid = new Range();
        humid.setColor(Color.parseColor("#2196F3")); // blue
        humid.setFrom(60.0);
        humid.setTo(MAX_HUM);

        humGauge.addRange(dry);
        humGauge.addRange(ok);
        humGauge.addRange(humid);

        humGauge.setMinValue(MIN_HUM);
        humGauge.setMaxValue(MAX_HUM);
        humGauge.setValue(0.0);

        setHumUi(Float.NaN, false);
    }

    private void setHumUi(float humPct, boolean isFake) {
        if (Float.isNaN(humPct)) {
            humValueText.setText("Hum: -- %");
            return;
        }

        if (humPct < MIN_HUM) humPct = (float) MIN_HUM;
        if (humPct > MAX_HUM) humPct = (float) MAX_HUM;

        humGauge.setValue(humPct);

        String label = String.format(Locale.US, "Hum: %.2f %%%s",
                humPct,
                isFake ? " (reset)" : "");
        humValueText.setText(label);
    }
}

package com.example.smartsense2.ui.temp;

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
import com.example.smartsense2.data.SensorReading;
import com.example.smartsense2.data.SensorRepository;

import java.util.Locale;
import java.util.Random;

public class TempGaugeFragment extends Fragment {

    private static final double MIN_TEMP = 0.0;
    private static final double MAX_TEMP = 50.0;

    private HalfGauge tempGauge;
    private TextView tempValueText;
    private Button btnResetTemp;

    private final Random random = new Random();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View v = inflater.inflate(R.layout.fragment_temp_gauge, container, false);

        tempGauge = v.findViewById(R.id.tempGauge);
        tempValueText = v.findViewById(R.id.tempValueText);
        btnResetTemp = v.findViewById(R.id.btnResetTemp);

        setupGauge();

        // Reset Value = set a random placeholder; real value will overwrite on next WS message
        btnResetTemp.setOnClickListener(view -> {
            double fake = MIN_TEMP + (MAX_TEMP - MIN_TEMP) * random.nextDouble();
            setTempUi((float) fake, true);
        });

        // Live updates from repository
        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
            if (reading == null) return;
            setTempUi(reading.tempC, false);
        });

        return v;
    }

    private void setupGauge() {
        // Color ranges (cold -> warm -> hot)
        Range cold = new Range();
        cold.setColor(Color.parseColor("#2196F3"));
        cold.setFrom(MIN_TEMP);
        cold.setTo(16.0);

        Range warm = new Range();
        warm.setColor(Color.parseColor("#4CAF50"));
        warm.setFrom(16.0);
        warm.setTo(30.0);

        Range hot = new Range();
        hot.setColor(Color.parseColor("#F44336"));
        hot.setFrom(30.0);
        hot.setTo(MAX_TEMP);

        tempGauge.addRange(cold);
        tempGauge.addRange(warm);
        tempGauge.addRange(hot);

        // Min/max/current
        tempGauge.setMinValue(MIN_TEMP);
        tempGauge.setMaxValue(MAX_TEMP);
        tempGauge.setValue(0.0);

        setTempUi(Float.NaN, false);
    }

    private void setTempUi(float tempC, boolean isFake) {
        if (Float.isNaN(tempC)) {
            tempValueText.setText("Temp: -- °C");
            return;
        }

        // Clamp to gauge range
        if (tempC < MIN_TEMP) tempC = (float) MIN_TEMP;
        if (tempC > MAX_TEMP) tempC = (float) MAX_TEMP;

        tempGauge.setValue(tempC);

        String label = String.format(Locale.US, "Temp: %.2f °C%s",
                tempC,
                isFake ? " (reset)" : "");
        tempValueText.setText(label);
    }
}

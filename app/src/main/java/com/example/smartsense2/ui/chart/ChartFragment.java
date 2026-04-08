package com.example.smartsense2.ui.chart;

import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.smartsense2.R;
import com.example.smartsense2.data.SensorReading;
import com.example.smartsense2.data.SensorRepository;
import com.example.smartsense2.util.AppPrefs;
import com.example.smartsense2.util.CsvExporter;
import com.example.smartsense2.util.FirebaseUploader;
import com.example.smartsense2.util.UploadScheduler;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartFragment extends Fragment {

    private LineChart chart;
    private LineDataSet humSet;
    private LineDataSet tempSet;

    private Uri lastCsvUri = null;
    private String lastCsvName = null;
    private long baseTimestampMs = -1;
    private long lastPlottedSeq = -1;

    private SwitchMaterial swAutoUpload;
    private TextView txtAutoUploadStatus;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable refreshStatusRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_chart, container, false);

        chart = v.findViewById(R.id.lineChart);
        Button btnExport = v.findViewById(R.id.btnExportCsv);
        Button btnUpload = v.findViewById(R.id.btnUploadCsv);

        swAutoUpload = v.findViewById(R.id.swAutoUpload);
        txtAutoUploadStatus = v.findViewById(R.id.txtAutoUploadStatus);

        restoreLastCsv();
        setupChart();
        rebuildChartFromHistory();

        SensorRepository.get().resetVersion().observe(getViewLifecycleOwner(), version -> {
            resetChart();
            rebuildChartFromHistory();
        });

        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
            if (reading == null) return;
            appendReading(reading);
        });

        boolean autoEnabled = AppPrefs.isAutoUploadEnabled(requireContext());
        swAutoUpload.setChecked(autoEnabled);
        UploadScheduler.syncFromPrefs(requireContext());
        updateStatusText();

        btnExport.setOnClickListener(view -> {
            try {
                List<SensorReading> history = SensorRepository.get().historySnapshot();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                lastCsvName = "smartsense_" + ts + ".csv";
                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);

                AppPrefs.setLastCsv(requireContext(), lastCsvUri.toString(), lastCsvName);

                Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_SHORT).show();
                updateStatusText();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        btnUpload.setOnClickListener(view -> manualUpload());

        swAutoUpload.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setAutoUploadEnabled(requireContext(), isChecked);

            if (isChecked) {
                UploadScheduler.startAutoUpload(requireContext());
                startRefreshingStatus();
                Toast.makeText(requireContext(), "Auto upload enabled", Toast.LENGTH_SHORT).show();
            } else {
                UploadScheduler.stopAutoUpload(requireContext());
                stopRefreshingStatus();
                txtAutoUploadStatus.setText("Auto: OFF");
                Toast.makeText(requireContext(), "Auto upload disabled", Toast.LENGTH_SHORT).show();
            }
        });

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        startRefreshingStatus();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopRefreshingStatus();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopRefreshingStatus();
    }

    private void restoreLastCsv() {
        String uriString = AppPrefs.getLastCsvUri(requireContext());
        String fileName = AppPrefs.getLastCsvName(requireContext());

        if (!TextUtils.isEmpty(uriString)) {
            lastCsvUri = Uri.parse(uriString);
        }
        lastCsvName = fileName;
    }

    private void manualUpload() {
        if (lastCsvUri == null) {
            try {
                List<SensorReading> history = SensorRepository.get().historySnapshot();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                lastCsvName = "smartsense_" + ts + ".csv";
                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
                AppPrefs.setLastCsv(requireContext(), lastCsvUri.toString(), lastCsvName);
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return;
            }
        }

        String uploadName = (lastCsvName != null) ? lastCsvName : "smartsense.csv";

        FirebaseUploader.uploadCsv(
                lastCsvUri,
                uploadName,
                () -> Toast.makeText(requireContext(), "Upload OK", Toast.LENGTH_SHORT).show(),
                err -> Toast.makeText(requireContext(), "Upload failed: " + err, Toast.LENGTH_LONG).show()
        );
    }

    private void setupChart() {
        chart.setNoDataText("Waiting for sensor data...");
        chart.setDescription(null);
        chart.setTouchEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setPinchZoom(true);

        Legend legend = chart.getLegend();
        legend.setEnabled(true);
        legend.setForm(Legend.LegendForm.LINE);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);

        YAxis left = chart.getAxisLeft();
        left.setAxisMinimum(0f);
        left.setAxisMaximum(100f);
        left.setGranularity(1f);

        YAxis right = chart.getAxisRight();
        right.setAxisMinimum(0f);
        right.setAxisMaximum(50f);
        right.setGranularity(1f);
        right.setEnabled(true);
        right.setLabelCount(6, true);

        resetChart();
    }

    private void resetChart() {
        baseTimestampMs = -1;
        lastPlottedSeq = -1;

        humSet = new LineDataSet(new ArrayList<>(), "Hum (%)");
        humSet.setAxisDependency(YAxis.AxisDependency.LEFT);
        humSet.setColor(ColorTemplate.getHoloBlue());
        humSet.setLineWidth(2f);
        humSet.setDrawCircles(false);
        humSet.setDrawValues(false);

        tempSet = new LineDataSet(new ArrayList<>(), "Temp (°C)");
        tempSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        tempSet.setColor(Color.RED);
        tempSet.setLineWidth(2f);
        tempSet.setDrawCircles(false);
        tempSet.setDrawValues(false);

        chart.clear();
        chart.setData(new LineData(humSet, tempSet));
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private void rebuildChartFromHistory() {
        List<SensorReading> history = SensorRepository.get().historySnapshot();
        for (SensorReading r : history) {
            appendReading(r);
        }
    }

    private void appendReading(SensorReading r) {
        if (r == null || r.timestampMs <= 0) return;
        if (r.seq > 0 && r.seq == lastPlottedSeq) return;

        if (baseTimestampMs < 0) {
            baseTimestampMs = r.timestampMs;
        }

        float x = (r.timestampMs - baseTimestampMs) / 1000f;

        humSet.addEntry(new Entry(x, r.humPct));
        tempSet.addEntry(new Entry(x, r.tempC));

        if (r.seq > 0) {
            lastPlottedSeq = r.seq;
        }

        LineData data = chart.getData();
        if (data != null) {
            data.notifyDataChanged();
        }

        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(60f);
        chart.moveViewToX(x);
        chart.invalidate();
    }

    private void startRefreshingStatus() {
        if (refreshStatusRunnable != null) {
            uiHandler.removeCallbacks(refreshStatusRunnable);
        }

        refreshStatusRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    updateStatusText();
                    uiHandler.postDelayed(this, 10000);
                }
            }
        };

        uiHandler.post(refreshStatusRunnable);
    }

    private void stopRefreshingStatus() {
        if (refreshStatusRunnable != null) {
            uiHandler.removeCallbacks(refreshStatusRunnable);
            refreshStatusRunnable = null;
        }
    }

    private void updateStatusText() {
        if (swAutoUpload == null || txtAutoUploadStatus == null) return;

        boolean autoOn = swAutoUpload.isChecked();
        if (!autoOn) {
            txtAutoUploadStatus.setText("Auto: OFF");
            return;
        }

        if (lastCsvUri == null) {
            txtAutoUploadStatus.setText("Auto: ON, export CSV first");
            return;
        }

        txtAutoUploadStatus.setText("Auto: ON, hourly");
    }
}
//package com.example.smartsense2.ui.chart;
//
//import android.graphics.Paint;
//import android.graphics.Color;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.Handler;
//import android.os.Looper;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//import androidx.fragment.app.Fragment;
//
//import com.example.smartsense2.R;
//import com.example.smartsense2.data.SensorReading;
//import com.example.smartsense2.data.SensorRepository;
//import com.example.smartsense2.util.CsvExporter;
//import com.example.smartsense2.util.FirebaseUploader;
//import com.example.smartsense2.util.UploadScheduler;
//import com.github.mikephil.charting.charts.LineChart;
//import com.github.mikephil.charting.components.Legend;
//import com.github.mikephil.charting.components.XAxis;
//import com.github.mikephil.charting.components.YAxis;
//import com.github.mikephil.charting.data.Entry;
//import com.github.mikephil.charting.data.LineData;
//import com.github.mikephil.charting.data.LineDataSet;
//import com.github.mikephil.charting.utils.ColorTemplate;
//import com.google.android.material.switchmaterial.SwitchMaterial;
//
//import android.widget.TextView;
//
//import java.text.SimpleDateFormat;
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//import java.util.Locale;
//
//public class ChartFragment extends Fragment {
//
//    private LineChart chart;
//    private LineDataSet humSet;
//    private LineDataSet tempSet;
//
//    private Uri lastCsvUri = null;
//    private String lastCsvName = null;
//    private long baseTimestampMs = -1;
//
//    private SwitchMaterial swAutoUpload;
//    private TextView txtAutoUploadStatus;
//
//    private long lastAutoSyncStart = 0;
//    private Handler uiHandler = new Handler(Looper.getMainLooper());
//    private Runnable refreshStatusRunnable;
//
//    @Nullable
//    @Override
//    public View onCreateView(@NonNull LayoutInflater inflater,
//                             @Nullable ViewGroup container,
//                             @Nullable Bundle savedInstanceState) {
//        View v = inflater.inflate(R.layout.fragment_chart, container, false);
//
//        chart = v.findViewById(R.id.lineChart);
//        Button btnExport = v.findViewById(R.id.btnExportCsv);
//        Button btnUpload = v.findViewById(R.id.btnUploadCsv);
//
//        swAutoUpload = v.findViewById(R.id.swAutoUpload);
//        txtAutoUploadStatus = v.findViewById(R.id.txtAutoUploadStatus);
//
//        setupChart();
//        loadInitialHistory();
//
//        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
//            if (reading == null) return;
//            appendReading(reading);
//        });
//
//        btnExport.setOnClickListener(view -> {
//            try {
//                List<SensorReading> history = SensorRepository.get().historySnapshot();
//                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
//                lastCsvName = "smartsense_" + ts + ".csv";
//                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
//                Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_SHORT).show();
//            } catch (Exception e) {
//                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
//            }
//        });
//
//        btnUpload.setOnClickListener(view -> manualUpload());
//
//        swAutoUpload.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            if (isChecked) {
//                UploadScheduler.startAutoUpload(requireContext());
//                startRefreshingStatus();
//                Toast.makeText(requireContext(), "Auto upload enabled", Toast.LENGTH_SHORT).show();
//            } else {
//                UploadScheduler.stopAutoUpload(requireContext());
//                stopRefreshingStatus();
//                txtAutoUploadStatus.setText("Auto: OFF");
//                Toast.makeText(requireContext(), "Auto upload disabled", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//        return v;
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        startRefreshingStatus();
//    }
//
//    @Override
//    public void onPause() {
//        super.onPause();
//        stopRefreshingStatus();
//    }
//
//    private void manualUpload() {
//        if (lastCsvUri == null) {
//            try {
//                List<SensorReading> history = SensorRepository.get().historySnapshot();
//                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
//                lastCsvName = "smartsense_" + ts + ".csv";
//                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
//            } catch (Exception e) {
//                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
//                return;
//            }
//        }
//
//        String uploadName = (lastCsvName != null) ? lastCsvName : "smartsense.csv";
//
//        FirebaseUploader.uploadCsv(
//                lastCsvUri,
//                uploadName,
//                () -> Toast.makeText(requireContext(), "Upload OK", Toast.LENGTH_SHORT).show(),
//                err -> Toast.makeText(requireContext(), "Upload failed: " + err, Toast.LENGTH_LONG).show()
//        );
//    }
//
//    private void setupChart() {
//        // Initial “no data” placeholder
//        chart.setNoDataText("Waiting for sensor data...");
//
//        // Style the no‑data text (no setNoDataTextSize; use PAINT_INFO)
////        Paint info = chart.getPaint(com.github.mikephil.charting.utils.Utils.PAINT_INFO);
////        if (info != null) {
////            info.setTextSize(16f);
////        }
//
//        chart.setDescription(null); // or disable it if you don’t want description
//        chart.setTouchEnabled(true);
//        chart.setDragEnabled(true);
//        chart.setScaleEnabled(true);
//        chart.setPinchZoom(true);
//
//        Legend legend = chart.getLegend();
//        legend.setEnabled(true);
//        legend.setForm(Legend.LegendForm.LINE);
//
//        XAxis xAxis = chart.getXAxis();
//        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
//        xAxis.setDrawGridLines(false);
//        xAxis.setGranularity(1f);
//
//        YAxis left = chart.getAxisLeft();
//        left.setAxisMinimum(0f);
//        left.setAxisMaximum(100f);
//        left.setGranularity(1f);
//
//        YAxis right = chart.getAxisRight();
//        right.setAxisMinimum(0f);
//        right.setAxisMaximum(50f);
//        right.setGranularity(1f);
//        right.setEnabled(true);
//        right.setLabelCount(6, true); // 0, 10, 20, 30, 40, 50
//
//        humSet = new LineDataSet(new ArrayList<>(), "Hum (%)");
//        humSet.setAxisDependency(YAxis.AxisDependency.LEFT);
//        humSet.setColor(ColorTemplate.getHoloBlue());
//        humSet.setLineWidth(2f);
//        humSet.setDrawCircles(false);
//        humSet.setDrawValues(false);
//
//        tempSet = new LineDataSet(new ArrayList<>(), "Temp (°C)");
//        tempSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
//        tempSet.setColor(Color.RED);
//        tempSet.setLineWidth(2f);
//        tempSet.setDrawCircles(false);
//        tempSet.setDrawValues(false);
//
//        LineData data = new LineData(humSet, tempSet);
//        chart.setData(data);
//        chart.invalidate();
//    }
//
//    private void loadInitialHistory() {
//        baseTimestampMs = -1;
//        List<SensorReading> history = SensorRepository.get().historySnapshot();
//        for (SensorReading r : history) {
//            appendReading(r);
//        }
//    }
//
//    private void appendReading(SensorReading r) {
//        if (r.timestampMs <= 0) return;
//
//        if (baseTimestampMs < 0) {
//            baseTimestampMs = r.timestampMs;
//        }
//
//        float x = (r.timestampMs - baseTimestampMs) / 1000f; // seconds
//
//        humSet.addEntry(new Entry(x, r.humPct));
//        tempSet.addEntry(new Entry(x, r.tempC));
//
//        LineData data = chart.getData();
//        if (data != null) {
//            data.notifyDataChanged(); // notify data
//        }
//        chart.notifyDataSetChanged();  // notify chart
//        chart.invalidate();            // redraw
//
//        chart.setVisibleXRangeMaximum(60f); // last 60 seconds
//        chart.moveViewToX(x);
//    }
//
//    // ---- Auto‑upload status UI ----
//
//    private void startRefreshingStatus() {
//        if (refreshStatusRunnable != null) {
//            uiHandler.removeCallbacks(refreshStatusRunnable);
//        }
//
//        refreshStatusRunnable = new Runnable() {
//            @Override
//            public void run() {
//                if (isAdded()) {
//                    updateStatusText();
//                }
//                uiHandler.postDelayed(this, 10000); // every 10s
//            }
//        };
//
//        uiHandler.post(refreshStatusRunnable);
//    }
//
//    private void stopRefreshingStatus() {
//        if (refreshStatusRunnable != null) {
//            uiHandler.removeCallbacks(refreshStatusRunnable);
//            refreshStatusRunnable = null;
//        }
//    }
//
//    private void updateStatusText() {
//        if (swAutoUpload == null || txtAutoUploadStatus == null) return;
//
//        boolean autoOn = swAutoUpload.isChecked();
//        long now = System.currentTimeMillis();
//
//        if (!autoOn) {
//            txtAutoUploadStatus.setText("Auto: OFF");
//            return;
//        }
//
//        String next = null;
//        if (lastAutoSyncStart == 0) {
//            lastAutoSyncStart = now;
//            next = "~5 min";
//        } else {
//            long elapsed = (now - lastAutoSyncStart) / 60000; // minutes
//            if (elapsed >= 60) {
//                lastAutoSyncStart = now;
//                next = "~5 min";
//            } else {
//                int remaining = 60 - (int) elapsed;
//                next = remaining + " min";
//            }
//        }
//
//        txtAutoUploadStatus.setText("Auto: ON, next: " + next);
//    }
//}
////package com.example.smartsense2.ui.chart;
////
////import android.net.Uri;
////import android.os.Bundle;
////import android.view.LayoutInflater;
////import android.view.View;
////import android.view.ViewGroup;
////import android.widget.Button;
////import android.widget.Toast;
////
////import androidx.annotation.NonNull;
////import androidx.annotation.Nullable;
////import androidx.fragment.app.Fragment;
////
////import com.example.smartsense2.R;
////import com.example.smartsense2.data.SensorReading;
////import com.example.smartsense2.data.SensorRepository;
////import com.example.smartsense2.util.CsvExporter;
////import com.example.smartsense2.util.FirebaseUploader;
////import com.example.smartsense2.util.UploadScheduler;
////import com.github.mikephil.charting.charts.LineChart;
////import com.github.mikephil.charting.components.Legend;
////import com.github.mikephil.charting.components.XAxis;
////import com.github.mikephil.charting.components.YAxis;
////import com.github.mikephil.charting.data.Entry;
////import com.github.mikephil.charting.data.LineData;
////import com.github.mikephil.charting.data.LineDataSet;
////import com.github.mikephil.charting.utils.ColorTemplate;
////import com.google.android.material.switchmaterial.SwitchMaterial;
////
////import android.graphics.Color;
////import android.widget.TextView;
////
////import java.text.SimpleDateFormat;
////import java.util.ArrayList;
////import java.util.Date;
////import java.util.List;
////import java.util.Locale;
////
////public class ChartFragment extends Fragment {
////
////    private LineChart chart;
////    private LineDataSet humSet;
////    private LineDataSet tempSet;
////
////    private Uri lastCsvUri = null;
////    private String lastCsvName = null;
////    private long baseTimestampMs = -1;
////
////    private SwitchMaterial swAutoUpload;
////    private TextView txtAutoUploadStatus;
////
////    @Nullable
////    @Override
////    public View onCreateView(@NonNull LayoutInflater inflater,
////                             @Nullable ViewGroup container,
////                             @Nullable Bundle savedInstanceState) {
////        View v = inflater.inflate(R.layout.fragment_chart, container, false);
////
////        chart = v.findViewById(R.id.lineChart);
////        Button btnExport = v.findViewById(R.id.btnExportCsv);
////        Button btnUpload = v.findViewById(R.id.btnUploadCsv);
////
////        swAutoUpload = v.findViewById(R.id.swAutoUpload);
////        txtAutoUploadStatus = v.findViewById(R.id.txtAutoUploadStatus);
////
////        setupChart();
////        loadInitialHistory();
////
////        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
////            if (reading == null) return;
////            appendReading(reading);
////        });
////
////        btnExport.setOnClickListener(view -> {
////            try {
////                List<SensorReading> history = SensorRepository.get().historySnapshot();
////                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
////                lastCsvName = "smartsense_" + ts + ".csv";
////                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
////                Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_SHORT).show();
////            } catch (Exception e) {
////                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
////            }
////        });
////
////        btnUpload.setOnClickListener(view -> manualUpload());
////
////        swAutoUpload.setOnCheckedChangeListener((buttonView, isChecked) -> {
////            if (isChecked) {
////                UploadScheduler.startAutoUpload(requireContext());
////                txtAutoUploadStatus.setText("Auto Upload: ON");
////                Toast.makeText(requireContext(), "Auto upload enabled", Toast.LENGTH_SHORT).show();
////            } else {
////                UploadScheduler.stopAutoUpload(requireContext());
////                txtAutoUploadStatus.setText("Auto Upload: OFF");
////                Toast.makeText(requireContext(), "Auto upload disabled", Toast.LENGTH_SHORT).show();
////            }
////        });
////
////        return v;
////    }
////
////    private void manualUpload() {
////        if (lastCsvUri == null) {
////            try {
////                List<SensorReading> history = SensorRepository.get().historySnapshot();
////                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
////                lastCsvName = "smartsense_" + ts + ".csv";
////                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
////            } catch (Exception e) {
////                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
////                return;
////            }
////        }
////
////        String uploadName = (lastCsvName != null) ? lastCsvName : "smartsense.csv";
////
////        FirebaseUploader.uploadCsv(
////                lastCsvUri,
////                uploadName,
////                () -> Toast.makeText(requireContext(), "Upload OK", Toast.LENGTH_SHORT).show(),
////                err -> Toast.makeText(requireContext(), "Upload failed: " + err, Toast.LENGTH_LONG).show()
////        );
////    }
////
////    private void setupChart() {
////        chart.getDescription().setEnabled(false);
////        chart.setTouchEnabled(true);
////        chart.setDragEnabled(true);
////        chart.setScaleEnabled(true);
////        chart.setPinchZoom(true);
////
////        Legend legend = chart.getLegend();
////        legend.setEnabled(true);
////        legend.setForm(Legend.LegendForm.LINE);
////
////        XAxis xAxis = chart.getXAxis();
////        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
////        xAxis.setDrawGridLines(false);
////        xAxis.setGranularity(1f);
////
////        YAxis left = chart.getAxisLeft();
////        left.setAxisMinimum(0f);
////        left.setAxisMaximum(100f);
////        left.setGranularity(1f);
////
////        YAxis right = chart.getAxisRight();
////        right.setAxisMinimum(0f);
////        right.setAxisMaximum(50f);
////        right.setGranularity(1f);
////        right.setEnabled(true);
////        right.setLabelCount(6, true);
////
////        humSet = new LineDataSet(new ArrayList<>(), "Hum (%)");
////        humSet.setAxisDependency(YAxis.AxisDependency.LEFT);
////        humSet.setColor(ColorTemplate.getHoloBlue());
////        humSet.setLineWidth(2f);
////        humSet.setDrawCircles(false);
////        humSet.setDrawValues(false);
////
////        tempSet = new LineDataSet(new ArrayList<>(), "Temp (°C)");
////        tempSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
////        tempSet.setColor(Color.RED);
////        tempSet.setLineWidth(2f);
////        tempSet.setDrawCircles(false);
////        tempSet.setDrawValues(false);
////
////        chart.setData(new LineData(humSet, tempSet));
////        chart.invalidate();
////    }
////
////    private void loadInitialHistory() {
////        baseTimestampMs = -1;
////        List<SensorReading> history = SensorRepository.get().historySnapshot();
////        for (SensorReading r : history) appendReading(r);
////    }
////
////    private void appendReading(SensorReading r) {
////        if (r.timestampMs <= 0) return;
////        if (baseTimestampMs < 0) baseTimestampMs = r.timestampMs;
////
////        LineData data = chart.getData();
////        if (data == null) return;
////
////        float x = (r.timestampMs - baseTimestampMs) / 1000f;
////
//////        data.addEntry(new Entry(x, r.humPct), 0);
//////        data.addEntry(new Entry(x, r.tempC), 1);
////        humSet.addEntry(new Entry(x, r.humPct));
////        tempSet.addEntry(new Entry(x, r.tempC));
////
////        data.notifyDataChanged();
////        chart.notifyDataSetChanged();
////        chart.invalidate();
////
////        chart.setVisibleXRangeMaximum(60f);
////        chart.moveViewToX(x);
////    }
////}
////package com.example.smartsense2.ui.chart;
////
////import android.graphics.Color;
////import android.net.Uri;
////import android.os.Bundle;
////import android.view.LayoutInflater;
////import android.view.View;
////import android.view.ViewGroup;
////import android.widget.Button;
////import android.widget.Toast;
////
////import androidx.annotation.NonNull;
////import androidx.annotation.Nullable;
////import androidx.fragment.app.Fragment;
////
////import com.example.smartsense2.R;
////import com.example.smartsense2.data.SensorReading;
////import com.example.smartsense2.data.SensorRepository;
////import com.example.smartsense2.util.CsvExporter;
////import com.example.smartsense2.util.FirebaseUploader;
////import com.github.mikephil.charting.charts.LineChart;
////import com.github.mikephil.charting.components.Legend;
////import com.github.mikephil.charting.components.XAxis;
////import com.github.mikephil.charting.components.YAxis;
////import com.github.mikephil.charting.data.Entry;
////import com.github.mikephil.charting.data.LineData;
////import com.github.mikephil.charting.data.LineDataSet;
////import com.github.mikephil.charting.utils.ColorTemplate;
////
////import java.text.SimpleDateFormat;
////import java.util.ArrayList;
////import java.util.Date;
////import java.util.List;
////import java.util.Locale;
////
////public class ChartFragment extends Fragment {
////
////    private LineChart chart;
////
////    private LineDataSet humSet;   // LEFT axis
////    private LineDataSet tempSet;  // RIGHT axis
////
////    private Uri lastCsvUri = null;
////    private String lastCsvName = null;
////
////    // For stable, increasing X values even if seq is broken
////    private long baseTimestampMs = -1;
////
////    @Nullable
////    @Override
////    public View onCreateView(@NonNull LayoutInflater inflater,
////                             @Nullable ViewGroup container,
////                             @Nullable Bundle savedInstanceState) {
////        View v = inflater.inflate(R.layout.fragment_chart, container, false);
////
////        chart = v.findViewById(R.id.lineChart);
////        Button btnExport = v.findViewById(R.id.btnExportCsv);
////        Button btnUpload = v.findViewById(R.id.btnUploadCsv);
////
////        setupChart();
////        loadInitialHistory();
////
////        SensorRepository.get().latest().observe(getViewLifecycleOwner(), reading -> {
////            if (reading == null) return;
////            appendReading(reading);
////        });
////
////        btnExport.setOnClickListener(view -> {
////            try {
////                List<SensorReading> history = SensorRepository.get().historySnapshot();
////                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
////                lastCsvName = "smartsense_" + ts + ".csv";
////                lastCsvUri = CsvExporter.exportToDownloads(requireContext(), history);
////                Toast.makeText(requireContext(), "Saved to Downloads", Toast.LENGTH_SHORT).show();
////            } catch (Exception e) {
////                Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
////            }
////        });
////
////        btnUpload.setOnClickListener(view -> {
////            if (lastCsvUri == null) {
////                Toast.makeText(requireContext(), "Export CSV first", Toast.LENGTH_SHORT).show();
////                return;
////            }
////
////            String uploadName = (lastCsvName != null) ? lastCsvName : "smartsense.csv";
////            FirebaseUploader.uploadCsv(
////                    lastCsvUri,
////                    uploadName,
////                    () -> Toast.makeText(requireContext(), "Upload OK", Toast.LENGTH_SHORT).show(),
////                    err -> Toast.makeText(requireContext(), "Upload failed: " + err, Toast.LENGTH_LONG).show()
////            );
////        });
////
////        return v;
////    }
////
////    private void setupChart() {
////        chart.getDescription().setEnabled(false);
////        chart.setTouchEnabled(true);
////        chart.setDragEnabled(true);
////        chart.setScaleEnabled(true);
////        chart.setPinchZoom(true);
////
////        Legend legend = chart.getLegend();
////        legend.setEnabled(true);
////        legend.setForm(Legend.LegendForm.LINE);
////
////        XAxis xAxis = chart.getXAxis();
////        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
////        xAxis.setDrawGridLines(false);
////        xAxis.setGranularity(1f);
////
////        YAxis left = chart.getAxisLeft();
////        left.setAxisMinimum(0f);
////        left.setAxisMaximum(100f);
////        left.setGranularity(1f);
////
//////        YAxis right = chart.getAxisRight();
//////        right.setGranularity(1f);
////
////        YAxis right = chart.getAxisRight();
////        right.setAxisMinimum(0f);
////        right.setAxisMaximum(50f);
////        right.setGranularity(1f);
////        right.setEnabled(true);
////
////        right.setLabelCount(6, true); // 0,10,20,30,40,50
////
////        humSet = new LineDataSet(new ArrayList<>(), "Hum (%)");
////        humSet.setAxisDependency(YAxis.AxisDependency.LEFT);
////        humSet.setColor(ColorTemplate.getHoloBlue());
////        humSet.setLineWidth(2f);
////        humSet.setDrawCircles(false);
////        humSet.setDrawValues(false);
////
////        tempSet = new LineDataSet(new ArrayList<>(), "Temp (°C)");
////        tempSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
////        tempSet.setColor(Color.RED);
////        tempSet.setLineWidth(2f);
////        tempSet.setDrawCircles(false);
////        tempSet.setDrawValues(false);
////
////        chart.setData(new LineData(humSet, tempSet));
////        chart.invalidate(); // redraw [web:511]
////    }
////
////    private void loadInitialHistory() {
////        baseTimestampMs = -1;
////
////        List<SensorReading> history = SensorRepository.get().historySnapshot();
////        for (SensorReading r : history) appendReading(r);
////    }
////
////    private void appendReading(SensorReading r) {
////        if (r.timestampMs <= 0) return;
////
////        if (baseTimestampMs < 0) baseTimestampMs = r.timestampMs;
////
////        LineData data = chart.getData();
////        if (data == null) return;
////
////        // X = seconds since first reading (keeps values small & increasing)
////        float x = (r.timestampMs - baseTimestampMs) / 1000f;
////
////        data.addEntry(new Entry(x, r.humPct), 0);
////        data.addEntry(new Entry(x, r.tempC), 1);
////
////        data.notifyDataChanged();
////        chart.notifyDataSetChanged();
////        chart.invalidate(); // required for dynamic updates [web:511]
////
////        chart.setVisibleXRangeMaximum(60f); // last 60 seconds
////        chart.moveViewToX(x);
////    }
////}

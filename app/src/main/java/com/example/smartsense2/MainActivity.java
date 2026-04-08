package com.example.smartsense2;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.smartsense2.data.SensorRepository;
import com.example.smartsense2.ui.MainPagerAdapter;
import com.example.smartsense2.util.AppPrefs;
import com.example.smartsense2.ws.WsServerService;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private MaterialSwitch sourceSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        200
                );
            }
        }

        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        sourceSwitch = findViewById(R.id.sourceSwitch);

        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText("Temp");
                    break;
                case 1:
                    tab.setText("Hum");
                    break;
                case 2:
                    tab.setText("Chart");
                    break;
                case 3:
                    tab.setText("Control");
                    break;
            }
        }).attach();

        boolean firebaseMode = AppPrefs.isFirebaseLiveEnabled(this);
        sourceSwitch.setChecked(firebaseMode);
        SensorRepository.get().setLiveSource(
                firebaseMode
                        ? SensorRepository.LiveSource.FIREBASE
                        : SensorRepository.LiveSource.WEBSOCKET
        );

        sourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setFirebaseLiveEnabled(this, isChecked);

            SensorRepository.get().setLiveSource(
                    isChecked
                            ? SensorRepository.LiveSource.FIREBASE
                            : SensorRepository.LiveSource.WEBSOCKET
            );
        });

        startWsService();
    }

    private void startWsService() {
        Intent i = new Intent(this, WsServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
    }
}
//package com.example.smartsense2;
//
//import android.Manifest;
//import android.content.Intent;
//import android.content.pm.PackageManager;
//import android.os.Build;
//import android.os.Bundle;
//import android.widget.Switch;
//
//import androidx.activity.EdgeToEdge;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.app.ActivityCompat;
//import androidx.core.content.ContextCompat;
//import androidx.viewpager2.widget.ViewPager2;
//
//import com.example.smartsense2.data.SensorRepository;
//import com.example.smartsense2.ui.MainPagerAdapter;
//import com.example.smartsense2.ws.WsServerService;
//import com.google.android.material.materialswitch.MaterialSwitch;
//import com.google.android.material.tabs.TabLayout;
//import com.google.android.material.tabs.TabLayoutMediator;
//
//public class MainActivity extends AppCompatActivity {
//    private TabLayout tabLayout;
//    private ViewPager2 viewPager;
//    private MaterialSwitch sourceSwitch;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        setContentView(R.layout.activity_main);
//
//        if (Build.VERSION.SDK_INT >= 33) {
//            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
//                    != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(this,
//                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
//            }
//        }
//
//        tabLayout = findViewById(R.id.tabLayout);
//        viewPager = findViewById(R.id.viewPager);
//        sourceSwitch = findViewById(R.id.sourceSwitch);
//
//        MainPagerAdapter adapter = new MainPagerAdapter(this);
//        viewPager.setAdapter(adapter);
//
//        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
//            switch (position) {
//                case 0: tab.setText("Temp"); break;
//                case 1: tab.setText("Hum"); break;
//                case 2: tab.setText("Chart"); break;
//                case 3: tab.setText("Control"); break;
//            }
//        }).attach();
//
//        sourceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
//            SensorRepository.LiveSource source = isChecked
//                    ? SensorRepository.LiveSource.FIREBASE
//                    : SensorRepository.LiveSource.WEBSOCKET;
//
//            SensorRepository.get().setLiveSource(source);
//        });
//
//        SensorRepository.get().setLiveSource(SensorRepository.LiveSource.WEBSOCKET);
//        sourceSwitch.setChecked(false);
//
//        startWsService();
//    }
//
//    private void startWsService() {
//        Intent i = new Intent(this, WsServerService.class);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            startForegroundService(i);
//        } else {
//            startService(i);
//        }
//    }
//}
////package com.example.smartsense2;
////
////import android.os.Bundle;
////
////import androidx.activity.EdgeToEdge;
////import androidx.appcompat.app.AppCompatActivity;
////
////import android.content.Intent;
////import android.os.Build;
////
////import androidx.viewpager2.widget.ViewPager2;
////import android.Manifest;
////import android.content.pm.PackageManager;
////import android.os.Build;
////
////import androidx.core.app.ActivityCompat;
////import androidx.core.content.ContextCompat;
////
////import com.example.smartsense2.ui.MainPagerAdapter;
////import com.example.smartsense2.ws.WsServerService;
////import com.google.android.material.tabs.TabLayout;
////import com.google.android.material.tabs.TabLayoutMediator;
////public class MainActivity extends AppCompatActivity {
////    private TabLayout tabLayout;
////    private ViewPager2 viewPager;
////    @Override
////    protected void onCreate(Bundle savedInstanceState) {
////        super.onCreate(savedInstanceState);
////        EdgeToEdge.enable(this);
////        setContentView(R.layout.activity_main);
//////        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
//////            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//////            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//////            return insets;
//////        });
////        if (Build.VERSION.SDK_INT >= 33) {
////            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
////                    != PackageManager.PERMISSION_GRANTED) {
////                ActivityCompat.requestPermissions(this,
////                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 200);
////            }
////        }
////
////        tabLayout = findViewById(R.id.tabLayout);
////        viewPager = findViewById(R.id.viewPager);
////
////        MainPagerAdapter adapter = new MainPagerAdapter(this);
////        viewPager.setAdapter(adapter);
////
////        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
////            switch (position) {
////                case 0: tab.setText("Temp"); break;
////                case 1: tab.setText("Hum"); break;
////                case 2: tab.setText("Chart"); break;
////                case 3: tab.setText("Control"); break;
////            }
////        }).attach();
////
////        startWsService();
////    }
////    private void startWsService() {
////        Intent i = new Intent(this, WsServerService.class);
////        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
////            startForegroundService(i);
////        } else {
////            startService(i);
////        }
////    }
////}
package com.example.smartsense2.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.smartsense2.ui.chart.ChartFragment;
import com.example.smartsense2.ui.control.ControlFragment;
import com.example.smartsense2.ui.hum.HumGaugeFragment;
import com.example.smartsense2.ui.temp.TempGaugeFragment;

public class MainPagerAdapter extends FragmentStateAdapter {

    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new TempGaugeFragment();
            case 1: return new HumGaugeFragment();
            case 2: return new ChartFragment();
            case 3: return new ControlFragment();
            default: return new TempGaugeFragment();
        }
    }

    @Override
    public int getItemCount() {
        return 4;
    }
}

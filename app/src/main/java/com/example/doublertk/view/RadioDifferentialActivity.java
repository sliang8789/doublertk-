package com.example.doublertk.view;

import com.example.doublertk.R;
import com.example.doublertk.base.BaseActivity;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class RadioDifferentialActivity extends BaseActivity {

    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_radio_differential;
    }

    @Override
    protected void initView() {
        setTopBarTitle("电台差分");

        tabLayout = findViewById(R.id.tab_layout);
        viewPager = findViewById(R.id.view_pager);

        if (tabLayout != null && viewPager != null) {
            viewPager.setAdapter(new RadioDiffPagerAdapter(this));
            new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
                if (position == 0) tab.setText("监控");
                else if (position == 1) tab.setText("日志");
                else if (position == 2) tab.setText("工具");
            }).attach();
        }
    }

    private static class RadioDiffPagerAdapter extends FragmentStateAdapter {

        RadioDiffPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position == 0) return new RadioDiffMonitorFragment();
            if (position == 1) return new RadioDiffLogFragment();
            return new RadioDiffToolsFragment();
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}


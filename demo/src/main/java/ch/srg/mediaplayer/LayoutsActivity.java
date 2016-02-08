package ch.srg.mediaplayer;

import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;

import ch.srg.mediaplayer.demo.R;

public class LayoutsActivity extends AppCompatActivity {
    private final int LAYOUT_IDS[];
    private final String[] LAYOUT_NAMES;

    private ViewPager pager;
    private LayoutsPagerAdapter pagerAdapter;
    private TabLayout tabLayout;

    public LayoutsActivity() {
        LAYOUT_IDS = new int[]{
                R.layout.fragment_layout_one,
                R.layout.fragment_layout_two,
                R.layout.fragment_layout_three,
                R.layout.fragment_layout_three_bis,
                R.layout.fragment_layout_four
        };
        LAYOUT_NAMES = new String[]{
                "width constraint",
                "height constraint",
                "vert. scroll w/o adjust",
                "vert. scroll w/ adjust",
                "hor. scrol w/ adjust"
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layouts);

        pager = (ViewPager) findViewById(R.id.pager);

        pagerAdapter = new LayoutsPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(pagerAdapter);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(pager);
    }

    private class LayoutsPagerAdapter extends FragmentStatePagerAdapter {
        public LayoutsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            LayoutFragment layoutFragment = new LayoutFragment();
            Bundle args = new Bundle();
            args.putInt(LayoutFragment.ARG_LAYOUT_ID, LAYOUT_IDS[position]);
            layoutFragment.setArguments(args);
            return layoutFragment;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return LAYOUT_NAMES[position];
        }

        @Override
        public int getCount() {
            return LAYOUT_IDS.length;
        }
    }
}

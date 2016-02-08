package ch.srg.mediaplayer;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import ch.srg.mediaplayer.demo.R;

public class LayoutsActivity extends AppCompatActivity {
    private final int LAYOUT_LIST[];

    private ViewPager pager;
    private LayoutsPagerAdapter pagerAdapter;

    public LayoutsActivity() {
        LAYOUT_LIST = new int[]{
                R.layout.fragment_layout_one,
                R.layout.fragment_layout_two,
                R.layout.fragment_layout_three,
                R.layout.fragment_layout_four
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layouts);

        pager = (ViewPager) findViewById(R.id.pager);

        pagerAdapter = new LayoutsPagerAdapter(getSupportFragmentManager());
        pager.setAdapter(pagerAdapter);
        ViewGroup buttons = (ViewGroup) findViewById(R.id.buttons);

        for (int i = 0; i < LAYOUT_LIST.length; i++) {
            Button button = new Button(this);
            button.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            button.setText(String.valueOf(i));
            final int itemPosition = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    pager.setCurrentItem(itemPosition);
                }
            });
            buttons.addView(button);
        }
    }

    private class LayoutsPagerAdapter extends FragmentStatePagerAdapter {
        public LayoutsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            LayoutFragment layoutFragment = new LayoutFragment();
            Bundle args = new Bundle();
            args.putInt(LayoutFragment.ARG_LAYOUT_ID, LAYOUT_LIST[position]);
            layoutFragment.setArguments(args);
            return layoutFragment;
        }

        @Override
        public int getCount() {
            return LAYOUT_LIST.length;
        }
    }
}

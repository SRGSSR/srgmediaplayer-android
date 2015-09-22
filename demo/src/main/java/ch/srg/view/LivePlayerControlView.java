package ch.srg.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.segmentoverlay.R;
import it.moondroid.seekbarhint.library.SeekBarHint;

/**
 * Created by npietri on 20.05.15.
 */
public class LivePlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SRGMediaPlayerController.Listener {
    private final DateFormat liveTimeFormat;
    private SRGMediaPlayerController playerController;

    private SeekBarHint seekBar;

    private Button pauseButton;
    private Button playButton;

    private TextView leftTime;
    private TextView rightTime;

    private long seekBarSeekToMs;
    private long[] liveRangeMs;

    public LivePlayerControlView(Context context) {
        this(context, null);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.dvr_player_control, this, true);

        seekBar = (SeekBarHint) findViewById(R.id.dvr_player_control_seekbar);
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setOnProgressChangeListener(new SeekBarHint.OnSeekBarHintProgressChangeListener() {
            @Override
            public String onHintTextChanged(SeekBarHint seekBarHint, int i) {
                return stringForTimeInMs(liveRangeMs[0] + i);
            }
        });
        seekBar.setPopupStyle(SeekBarHint.POPUP_FOLLOW);
        liveRangeMs = new long[2];

        pauseButton = (Button) findViewById(R.id.segment_player_control_button_pause);
        playButton = (Button) findViewById(R.id.segment_player_control_button_play);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);

        leftTime = (TextView) findViewById(R.id.segment_player_control_time_left);
        rightTime = (TextView) findViewById(R.id.segment_player_control_time_right);

        liveTimeFormat = DateFormat.getTimeInstance();
    }

    public void attachToController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
        playerController.registerEventListener(this);
    }

    @Override
    public void onClick(View v) {
        if (playerController != null) {
            if (v == playButton) {
                playerController.start();
            } else if (v == pauseButton) {
                playerController.pause();
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
	    if (fromUser) {
	        seekBarSeekToMs = progress;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBarSeekToMs >= 0) {
            playerController.seekTo(seekBarSeekToMs);
            seekBarSeekToMs = -1;
        }
    }

    private void update() {
        if (playerController != null) {
            updateTimes(liveRangeMs, playerController.getWallClockPosition());

            playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
            pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
        } else {
            updateTimes(new long[2], 0);
            playButton.setVisibility(VISIBLE);
            pauseButton.setVisibility(GONE);
        }
    }

    private void updateTimes(long[] timeRange, long position) {
        // TODO Buffer indication
//        int bufferPercent = playerController.getBufferPercentage();
//        if (bufferPercent > 0) {
//            seekBar.setSecondaryProgress((int) duration * bufferPercent / 100);
//        } else {
//            seekBar.setSecondaryProgress(0);
//        }
        long startTime = timeRange[0];
        long endTime = timeRange[1];
        long duration = endTime - startTime;
        seekBar.setMax((int) duration);
        seekBar.setProgress((int) (position - startTime));

        leftTime.setText(stringForTimeInMs(position));
        rightTime.setText(stringForTimeInMs(endTime));
    }

    private String stringForTimeInMs(long millis) {
        return liveTimeFormat.format(new Date(millis));
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (mp == playerController) {
            liveRangeMs = playerController.getLiveRangeMs();
            update();
        }
    }
}

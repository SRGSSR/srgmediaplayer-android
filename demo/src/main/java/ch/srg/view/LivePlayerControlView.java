package ch.srg.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.DateFormat;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.demo.R;
import it.moondroid.seekbarhint.library.SeekBarHint;

/**
 * Created by npietri on 20.05.15.
 */
public class LivePlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SRGMediaPlayerController.Listener {
    private static final String TAG = "Live PlayerControl";
    private final DateFormat liveTimeFormat;
    private SRGMediaPlayerController playerController;

    private SeekBarHint seekBar;

    private Button pauseButton;
    private Button playButton;

    private TextView leftTime;
    private TextView rightTime;

    private long seekBarSeekToMs;
    private long duration;
    private long position;
    private long mediaPlaylistOffset;

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
                return stringForTimeInMs(i - duration);
            }
        });
        seekBar.setPopupStyle(SeekBarHint.POPUP_FOLLOW);

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
            Log.v(TAG, "PlayerEvent: " + position + "+" + mediaPlaylistOffset + " / " + duration);

            updateTimes(position - mediaPlaylistOffset, duration);

            playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
            pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
        } else {
            playButton.setVisibility(VISIBLE);
            pauseButton.setVisibility(GONE);
        }
    }

    private void updateTimes(long position, long duration) {
        // TODO Buffer indication
//        int bufferPercent = playerController.getBufferPercentage();
//        if (bufferPercent > 0) {
//            seekBar.setSecondaryProgress((int) duration * bufferPercent / 100);
//        } else {
//            seekBar.setSecondaryProgress(0);
//        }
        seekBar.setMax((int) duration);
        seekBar.setProgress((int) (position));

        leftTime.setText(stringForTimeInMs(position));
        rightTime.setText(stringForTimeInMs(duration));
    }

    private String stringForTimeInMs(long millis) {
        String sign;
        if (millis < 0) {
            sign = "-";
            millis = -millis;
        } else {
            sign = "";
        }
        int totalSeconds = (int) millis / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%s%d:%02d:%02d", sign, hours, minutes, seconds);
        } else {
            return String.format("%s%02d:%02d", sign, minutes, seconds);
        }
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        if (mp == playerController) {
            Log.v(TAG, "PlayerEvent: " + position + "+" + mediaPlaylistOffset + " / " + duration);
            position = event.mediaPosition;
            duration = event.mediaDuration;
            mediaPlaylistOffset = event.mediaPlaylistOffset;

            update();
        }
    }
}

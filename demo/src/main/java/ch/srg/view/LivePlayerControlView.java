package ch.srg.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.segmentoverlay.R;

/**
 * Created by npietri on 20.05.15.
 */
public class LivePlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SRGMediaPlayerController.Listener {
    private SRGMediaPlayerController playerController;

    private SeekBar seekBar;

    private Button pauseButton;
    private Button playButton;

    private TextView leftTime;
    private TextView rightTime;

    private long time;
    private long duration;

    private long seekBarSeekToMs;

	public LivePlayerControlView(Context context) {
        this(context, null);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.segment_player_control, this, true);

        seekBar = (SeekBar) findViewById(R.id.segment_player_control_seekbar);
        seekBar.setOnSeekBarChangeListener(this);

        pauseButton = (Button) findViewById(R.id.segment_player_control_button_pause);
        playButton = (Button) findViewById(R.id.segment_player_control_button_play);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);

        leftTime = (TextView) findViewById(R.id.segment_player_control_time_left);
        rightTime = (TextView) findViewById(R.id.segment_player_control_time_right);
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

    private void update(long time) {
        if (playerController != null) {
	        duration = playerController.getMediaDuration();

            updateTimes(time, duration);

            playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
            pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
        } else {
            updateTimes(-1, -1);
            playButton.setVisibility(VISIBLE);
            pauseButton.setVisibility(GONE);
        }
    }

    private void updateTimes(long position, long duration) {
        int bufferPercent = playerController.getBufferPercentage();
        if (bufferPercent > 0) {
            seekBar.setSecondaryProgress((int) duration * bufferPercent / 100);
        } else {
            seekBar.setSecondaryProgress(0);
        }
        seekBar.setMax((int) duration);
        seekBar.setProgress((int) position);

        leftTime.setText(stringForTimeInMs(position));
        rightTime.setText(stringForTimeInMs(duration));
    }

    private String stringForTimeInMs(long millis) {
        int totalAbsSeconds = Math.abs((int) millis / 1000);
        int seconds = totalAbsSeconds % 60;
        int minutes = (totalAbsSeconds / 60) % 60;
        int hours = (int) (totalAbsSeconds / 3600 * Math.signum(millis));
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
        update(event.mediaPosition);
    }
}

package ch.srg.segmentoverlay.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.segmentoverlay.R;
import ch.srg.segmentoverlay.controller.SegmentController;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 20.05.15.
 */
public class PlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SegmentController.Listener {

    private SRGMediaPlayerController playerController;

    private SegmentController segmentController;

    private SeekBar seekBar;

    private Button pauseButton;
    private Button playButton;
    private Button replayButton;

    private TextView leftTime;
    private TextView rightTime;

    private long duration;

    private long seekBarSeekToMs;

    public PlayerControlView(Context context) {
        this(context, null);
    }

    public PlayerControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PlayerControlView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.segment_player_control, this, true);

        seekBar = (SeekBar) findViewById(R.id.segment_player_control_seekbar);
        seekBar.setOnSeekBarChangeListener(this);

        pauseButton = (Button) findViewById(R.id.segment_player_control_button_pause);
        playButton = (Button) findViewById(R.id.segment_player_control_button_play);
        replayButton = (Button) findViewById(R.id.segment_player_control_button_replay);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        replayButton.setOnClickListener(this);

        leftTime = (TextView) findViewById(R.id.segment_player_control_time_left);
        rightTime = (TextView) findViewById(R.id.segment_player_control_time_right);
    }

    public void attachToController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
    }

    public void setSegmentController(@NonNull SegmentController segmentController) {
        if (this.segmentController != null) {
            this.segmentController.removeListener(this);
        }
        this.segmentController = segmentController;
        segmentController.addListener(this);
    }

    @Override
    public void onClick(View v) {
        if (playerController != null) {
            if (v == playButton) {
                playerController.start();
            } else if (v == pauseButton) {
                playerController.pause();
            } else if (v == replayButton) {
                String mediaIdentifier = playerController.getMediaIdentifier();
                try {
                    playerController.play(mediaIdentifier, (long) 0);
                } catch (SRGMediaPlayerException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && segmentController != null) {
            segmentController.sendUserTrackedProgress(progress);
        }
        if (fromUser) {
            seekBarSeekToMs = progress;
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        segmentController.stopUserTrackingProgress();
        if (seekBarSeekToMs >= 0) {
            segmentController.seekTo(seekBarSeekToMs);
            seekBarSeekToMs = -1;
        }
    }

    private void update(long time) {
        if (playerController != null) {
            duration = playerController.getMediaDuration();
            boolean mediaCompleted = time >= duration;

            updateTimes(time, duration);

            if (!mediaCompleted) {
                playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
                pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
                replayButton.setVisibility(View.GONE);
            } else {
                playButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.GONE);
                replayButton.setVisibility(View.VISIBLE);
            }
        } else {
            updateTimes(-1, -1);
            playButton.setVisibility(VISIBLE);
            pauseButton.setVisibility(GONE);
            replayButton.setVisibility(View.GONE);
        }
    }

    private void updateTimes(long position, long duration) {
        if (!segmentController.isUserChangingProgress()) {
            int bufferPercent = playerController.getBufferPercentage();
            if (bufferPercent > 0) {
                seekBar.setSecondaryProgress((int) duration * bufferPercent / 100);
            } else {
                seekBar.setSecondaryProgress(0);
            }
            seekBar.setMax((int) duration);
            seekBar.setProgress((int) position);
        }
        leftTime.setText(stringForTimeInMs(position));
        rightTime.setText(stringForTimeInMs(duration));
    }

    private String stringForTimeInMs(long millis) {
        if (millis < 0) {
            return "--:--";
        }
        int totalSeconds = (int) millis / 1000;
        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onPositionChange(long position) {
        update(position);
    }

    @Override
    public void onSegmentListChanged(List<Segment> segments) {
    }
}

package ch.srg.segmentoverlay.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

import ch.srg.mediaplayer.PlayerViewDelegate;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.segmentoverlay.R;
import ch.srg.segmentoverlay.controller.SegmentController;
import ch.srg.segmentoverlay.model.Segment;

/**
 * Created by npietri on 20.05.15.
 */
public class PlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SegmentController.Listener, PlayerViewDelegate {
    private static final long COMPLETION_TOLERANCE_MS = 5000;

    public static final int FULLSCREEN_BUTTON_INVISIBLE = 0;
    public static final int FULLSCREEN_BUTTON_ON = 1;
    public static final int FULLSCREEN_BUTTON_OFF = 2;

    @Nullable
    private SRGMediaPlayerController playerController;

    @Nullable
    private SegmentController segmentController;

    private SeekBar seekBar;

    private Button pauseButton;
    private Button playButton;
    private Button replayButton;
    private Button fullscreenButton;

    private TextView leftTime;
    private TextView rightTime;

    private long duration;

    private long seekBarSeekToMs;

    private int fullScreenButtonState;
    private long currentPosition;
    private long currentDuration;
    @Nullable
    private Listener listener;

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
        fullscreenButton = (Button) findViewById(R.id.segment_player_control_button_fullscreen);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        replayButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);

        leftTime = (TextView) findViewById(R.id.segment_player_control_time_left);
        rightTime = (TextView) findViewById(R.id.segment_player_control_time_right);

        updateFullScreenButton();
    }

    private void updateFullScreenButton() {
        if (fullscreenButton != null) {
            if (fullScreenButtonState == FULLSCREEN_BUTTON_INVISIBLE) {
                fullscreenButton.setVisibility(View.GONE);
            } else {
                fullscreenButton.setVisibility(View.VISIBLE);
                if (fullScreenButtonState == FULLSCREEN_BUTTON_OFF) {
                    fullscreenButton.setBackgroundResource(R.drawable.ic_fullscreen_exit);
                } else {
                    fullscreenButton.setBackgroundResource(R.drawable.ic_fullscreen);
                }
            }
        }
    }

    @Override
    public void attachToController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
        update(SRGMediaPlayerController.UNKNOWN_TIME);
    }

    @Override
    public void detachFromController(SRGMediaPlayerController srgMediaPlayerController) {
        this.playerController = null;
    }

    public void setSegmentController(@NonNull SegmentController segmentController) {
        if (this.segmentController != null) {
            this.segmentController.removeListener(this);
        }
        this.segmentController = segmentController;
        segmentController.addListener(this);
    }

    public void update() {
        long time = playerController != null ? playerController.getMediaPosition() : SRGMediaPlayerController.UNKNOWN_TIME;
        update(time);
    }

    @Override
    public void onClick(View v) {
        if (playerController != null) {
            if (v == playButton) {
                playerController.start();
            } else if (v == pauseButton) {
                playerController.pause();
            } else if (v == replayButton) {
                if (listener != null) {
                    listener.onReplayClick();
                }
            } else if (v == fullscreenButton) {
                if (listener != null) {
                    listener.onFullscreenClick(fullScreenButtonState == FULLSCREEN_BUTTON_ON);
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
        if (segmentController != null) {
            segmentController.stopUserTrackingProgress();
            if (seekBarSeekToMs >= 0 && playerController != null) {
                segmentController.seekTo(playerController.getMediaIdentifier(), seekBarSeekToMs);
                seekBarSeekToMs = -1;
            }
        }
    }

    private void update(long time) {
        if (playerController != null && !playerController.isReleased()) {
            boolean playing = playerController.isPlaying();
            duration = playerController.getMediaDuration();
            boolean mediaCompleted =
                    !playing && duration != 0 && time >= duration - COMPLETION_TOLERANCE_MS;

            updateTimes(time, duration);

            if (!mediaCompleted) {
                playButton.setVisibility(playing ? GONE : VISIBLE);
                pauseButton.setVisibility(playing ? VISIBLE : GONE);
                replayButton.setVisibility(View.GONE);
            } else {
                playButton.setVisibility(View.GONE);
                pauseButton.setVisibility(View.GONE);
                replayButton.setVisibility(View.VISIBLE);
            }
        } else {
            updateTimes(-1, -1);
            playButton.setVisibility(GONE);
            pauseButton.setVisibility(GONE);
            replayButton.setVisibility(View.VISIBLE);
        }
    }

    private void updateTimes(long position, long duration) {
        if (currentPosition != position || currentDuration != duration) {
            currentPosition = position;
            currentDuration = duration;
            if (segmentController != null
                    && playerController != null
                    && !segmentController.isUserChangingProgress()) {
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
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onPositionChange(@Nullable String mediaIdentifier, long position, boolean seeking) {
        update(position);
    }

    @Override
    public void onSegmentListChanged(List<Segment> segments) {
    }

    public interface Listener {
        void onReplayClick();

        void onFullscreenClick(boolean fullscreen);
    }

    public void setListener(PlayerControlView.Listener listener){
        this.listener = listener;
    }

    @Override
    public void setFullScreenButtonState(int fullScreenButtonState) {
        this.fullScreenButtonState = fullScreenButtonState;
        updateFullScreenButton();
    }
}

package ch.srg.mediaplayer.segment.view;

import android.content.Context;
import android.content.res.Configuration;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import ch.srg.mediaplayer.PlayerViewDelegate;
import ch.srg.mediaplayer.R;
import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class PlayerControlView extends LinearLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, PlayerViewDelegate {
    private static final long COMPLETION_TOLERANCE_MS = 5000;

    @Nullable
    private SRGMediaPlayerController controller;

    private SeekBar seekBar;

    private Button pauseButton;
    private Button playButton;
    private Button replayButton;
    private ImageButton fullscreenButton;
    private ImageButton subtitleButton;

    private TextView leftTime;
    private TextView rightTime;

    private long duration;

    private long seekBarSeekToMs;

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
        inflater.inflate(R.layout.player_control_view, this, true);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);

        seekBar = findViewById(R.id.segment_player_control_seekbar);
        seekBar.setOnSeekBarChangeListener(this);

        pauseButton = findViewById(R.id.segment_player_control_button_pause);
        playButton = findViewById(R.id.segment_player_control_button_play);
        replayButton = findViewById(R.id.segment_player_control_button_replay);
        fullscreenButton = findViewById(R.id.segment_player_control_button_fullscreen);
        subtitleButton = findViewById(R.id.segment_player_control_button_subtitles);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);
        replayButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        subtitleButton.setOnClickListener(this);

        leftTime = findViewById(R.id.segment_player_control_time_left);
        rightTime = findViewById(R.id.segment_player_control_time_right);

        updateFullScreenButton();
        updateSubtitleButton();
    }

    private void updateFullScreenButton() {
        if (fullscreenButton != null) {
            fullscreenButton.setSelected(getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT);
        }
    }

    private void updateSubtitleButton() {
        if (subtitleButton != null && controller != null) {
            subtitleButton.setVisibility(controller.getSubtitleTrackList().isEmpty() ? GONE : VISIBLE);
            subtitleButton.setSelected(controller.getSubtitleTrack() != null);
        }
    }

    @Override
    public void attachToController(SRGMediaPlayerController playerController) {
        this.controller = playerController;
        update(SRGMediaPlayerController.UNKNOWN_TIME);
    }

    @Override
    public void detachFromController(SRGMediaPlayerController srgMediaPlayerController) {
        this.controller = null;
    }

    @Override
    public void onClick(View v) {
        if (controller != null) {
            if (v == playButton) {
                controller.start();
            } else if (v == pauseButton) {
                controller.pause();
            } else if (v == replayButton) {
                if (listener != null) {
                    listener.onReplayClick();
                }
            } else if (v == fullscreenButton) {
                if (listener != null) {
                    listener.onFullscreenClick(fullscreenButton.isSelected());
                }
            } else if (v == subtitleButton) {
                if (listener != null) {
                    listener.onSubtitleClicked(v);
                }
            }
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && controller != null) {
            controller.sendUserTrackedProgress(seekBarSeekToMs);
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
        if (controller != null) {
            controller.stopUserTrackingProgress();
            if (seekBarSeekToMs >= 0 && controller != null) {
                controller.seekTo(seekBarSeekToMs);
                seekBarSeekToMs = -1;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float seekBarX = seekBar.getX();

        float x = event.getX() - seekBarX;
        float y = event.getY();

        float seekBarWidth = seekBar.getWidth();
        if (event.getAction() == MotionEvent.ACTION_UP || x >= 0 && x < seekBarWidth) {
            x = Math.min(Math.max(0, x), seekBarWidth);

            return seekBar.onTouchEvent(MotionEvent.obtain(
                    event.getDownTime(),
                    event.getEventTime(),
                    event.getAction(), x, y, event.getPressure(), event.getSize(), event.getMetaState(), event.getXPrecision(), event.getYPrecision(), event.getDeviceId(), 0));
        } else {
            return true;
        }
    }

    private void update(long time) {
        if (controller != null && !controller.isReleased()) {
            boolean playing = controller.isPlaying();
            duration = controller.getMediaDuration();
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
            updateSubtitleButton();
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
            if (controller != null
                    && !controller.isUserChangingProgress()) {
                int bufferPercent = controller.getBufferPercentage();
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

    public interface Listener {
        void onSubtitleClicked(View v);

        void onReplayClick();

        void onFullscreenClick(boolean fullscreen);
    }

    public void setListener(PlayerControlView.Listener listener) {
        this.listener = listener;
    }

    @Override
    public void update() {
        if (controller != null) {
            update(controller.getMediaPosition());
        }
    }

    @Override
    public void setHideCentralButton(boolean loading) {

    }
}

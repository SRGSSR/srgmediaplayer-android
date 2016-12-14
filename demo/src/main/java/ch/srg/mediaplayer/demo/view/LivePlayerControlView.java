package ch.srg.mediaplayer.demo.view;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.demo.R;

/**
 * Created by npietri on 20.05.15.
 */
public class LivePlayerControlView extends RelativeLayout implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, SRGMediaPlayerController.Listener, Runnable {
    private static final long PERIODIC_UPDATE_DELAY = 250;

    private static final String TAG = "Live PlayerControl";
    private final DateFormat liveTimeFormat;
    private SRGMediaPlayerController playerController;

    private SeekBar seekBar;

    private Button pauseButton;
    private Button playButton;

    private TextView leftTime;
    private TextView rightTime;

    private long seekBarSeekToMs;
    private long duration;
    private long position;
    private long mediaPlaylistOffset;
    private Handler handler;
    private boolean userChangingProgress;

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

        seekBar = (SeekBar) findViewById(R.id.dvr_player_control_seekbar);
        seekBar.setOnSeekBarChangeListener(this);

        pauseButton = (Button) findViewById(R.id.segment_player_control_button_pause);
        playButton = (Button) findViewById(R.id.segment_player_control_button_play);

        pauseButton.setOnClickListener(this);
        playButton.setOnClickListener(this);

        leftTime = (TextView) findViewById(R.id.segment_player_control_time_left);
        rightTime = (TextView) findViewById(R.id.segment_player_control_time_right);

        liveTimeFormat = DateFormat.getTimeInstance();

        handler = new Handler();
    }

    @Override
    public void onClick(View v) {
        if (playerController != null) {
            if (v == playButton) {
                if (playerController.getMediaPosition() < playerController.getMediaPlaylistStartTime()) {
                    playerController.seekTo(1);
                }
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
        userChangingProgress = true;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        userChangingProgress = false;
        if (seekBarSeekToMs >= 0) {
            playerController.seekTo(Math.max(1, seekBarSeekToMs));
            seekBarSeekToMs = -1;
        }
    }

    private void update() {
        if (playerController != null) {
            position = playerController.getMediaPosition();
            duration = playerController.getMediaDuration();
            mediaPlaylistOffset = playerController.getMediaPlaylistStartTime();

            Log.v(TAG, "Update: " + position + "+" + mediaPlaylistOffset + " / " + duration);

            long correctedPosition = Math.max(0, position - mediaPlaylistOffset);
            long realPosition = playerController.getLiveTime() - duration + correctedPosition;
            updateTimes(mediaPlaylistOffset, correctedPosition, duration, realPosition);

            playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
            pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
        } else {
            playButton.setVisibility(VISIBLE);
            pauseButton.setVisibility(GONE);
        }
    }

    private void updateTimes(long offset, long position, long duration, long realPosition) {
        // TODO Buffer indication
//        int bufferPercent = playerController.getBufferPercentage();
//        if (bufferPercent > 0) {
//            seekBar.setSecondaryProgress((int) duration * bufferPercent / 100);
//        } else {
//            seekBar.setSecondaryProgress(0);
//        }
        seekBar.setMax((int) duration);
        seekBar.setProgress((int) (position));

        DateFormat df = SimpleDateFormat.getTimeInstance();

        leftTime.setText(stringForTimeInMs(offset) + " + " + stringForTimeInMs(position));
        rightTime.setText(stringForTimeInMs(duration) + " (" + df.format(new Date(realPosition)) + ")");
    }

    private void updateIfNotUserTracked() {
        if (!userChangingProgress) {
            update();
        }
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

            update();
        }
    }

    public void startListening() {
        playerController.registerEventListener(this);
        handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
    }

    public void stopListening() {
        playerController.unregisterEventListener(this);
        handler.removeCallbacks(this);
    }

    public void setPlayerController(SRGMediaPlayerController playerController) {
        this.playerController = playerController;
    }

    @Override
    public void run() {
        updateIfNotUserTracked();
        handler.postDelayed(this, PERIODIC_UPDATE_DELAY);
    }
}

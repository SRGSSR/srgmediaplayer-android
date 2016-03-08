package ch.srg.mediaplayer.extras.overlay.control;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import ch.srg.mediaplayerextras.R;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * TODO: document your custom view class.
 */
public class SimplePlayerControlView extends RelativeLayout implements SRGMediaPlayerController.Listener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, Runnable {

	private static final long PERIODIC_UPDATE_DELAY = 250;
	private SRGMediaPlayerController playerController;

	private int themeColor = Color.RED;
	private SeekBar seekBar;

	private Button pauseButton;
	private Button playButton;
	private Button stopButton;
	private Button previousButton;
	private Button nextButton;

	private TextView leftTime;
	private TextView rightTime;


	public SimplePlayerControlView(Context context) {
		this(context, null);
	}

	public SimplePlayerControlView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SimplePlayerControlView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.simple_player_control, this, true);


		// Load attributes
		final TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SimplePlayerControlView, defStyle, 0);
		themeColor = a.getColor(R.styleable.SimplePlayerControlView_themeColor, themeColor);
		a.recycle();

		setBackgroundColor(themeColor);

		seekBar = (SeekBar) findViewById(R.id.simple_player_control_seekbar);
		seekBar.setOnSeekBarChangeListener(this);

		previousButton = (Button) findViewById(R.id.simple_player_control_button_previous);
		playButton = (Button) findViewById(R.id.simple_player_control_button_play);
		pauseButton = (Button) findViewById(R.id.simple_player_control_button_pause);
		stopButton = (Button) findViewById(R.id.simple_player_control_button_stop);
		nextButton = (Button) findViewById(R.id.simple_player_control_button_next);

		previousButton.setOnClickListener(this);
		playButton.setOnClickListener(this);
		pauseButton.setOnClickListener(this);
		stopButton.setOnClickListener(this);
		nextButton.setOnClickListener(this);

		leftTime = (TextView) findViewById(R.id.simple_player_control_time_left);
		rightTime = (TextView) findViewById(R.id.simple_player_control_time_right);
	}

	public void attachToController(SRGMediaPlayerController playerController) {
		this.playerController = playerController;

	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		//update UI when on screen
		postDelayed(this, PERIODIC_UPDATE_DELAY);
	}

	@Override
	protected void onDetachedFromWindow() {
		//Stop updateing UI when hidden
		removeCallbacks(this);
		super.onDetachedFromWindow();
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

	public boolean seekBarTracked = false;
	private int seekBarSeekToSecond = -1;

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			seekBarSeekToSecond = progress;
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		seekBarTracked = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		seekBarTracked = false;
		if (playerController != null && seekBarSeekToSecond >= 0) {
			playerController.seekTo(seekBarSeekToSecond * 1000);
			seekBarSeekToSecond = -1;
		}
	}

	@Override
	public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
		if (mp == playerController) {
			update();
		} else {
			throw new IllegalArgumentException("Unexpected player");
		}
	}

	private void update() {
		if (playerController != null) {
			long position = playerController.getMediaPosition();
			long duration = playerController.getMediaDuration();
			if (seekBarTracked) {
				updateTimes(seekBar.getProgress() * 1000, duration);
			} else {
				updateTimes(position, duration);
			}
			playButton.setVisibility(playerController.isPlaying() ? GONE : VISIBLE);
			pauseButton.setVisibility(playerController.isPlaying() ? VISIBLE : GONE);
			previousButton.setVisibility(GONE);
			stopButton.setVisibility(GONE);
			nextButton.setVisibility(GONE);
		} else {
			updateTimes(-1, -1);
			playButton.setVisibility(VISIBLE);
			pauseButton.setVisibility(GONE);
		}
	}

	private void updateTimes(long position, long duration) {
		int bufferPercent = playerController != null ? playerController.getBufferPercentage() : 0;
		int max = (int) (duration / 1000);
		if (bufferPercent > 0) {
			seekBar.setSecondaryProgress(max * bufferPercent / 100);
			//seekBar.setSecondaryProgress(seekBar.getSecondaryProgress()+1);
		} else {
			seekBar.setSecondaryProgress(0);
		}
		seekBar.setMax(max);
		seekBar.setProgress((int) (position / 1000));

		leftTime.setText(stringForTime(position));
		rightTime.setText(stringForTime(duration));
	}

	private String stringForTime(long millis) {
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
	public void run() {
		// This is stopped when detached from window.
		if (true) {
			update();
			postDelayed(this, PERIODIC_UPDATE_DELAY);
		}
	}
}

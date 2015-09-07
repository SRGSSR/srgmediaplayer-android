package ch.srg.mediaplayer.extras.overlay.error;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import ch.srg.mediaplayerextras.R;

import ch.srg.mediaplayer.SRGMediaPlayerController;

/**
 * Created by seb on 26/03/15.
 */
public class SimpleErrorMessage extends RelativeLayout implements View.OnClickListener, SRGMediaPlayerController.Listener {
	private static final int DEFAULT_DEBUG_CLICK_COUNT = 10;
	private final View cancelButton;

	private TextView debugMessageTextView;
	private TextView userMessageTextView;
	private int debugMessageClickCount;
	private boolean debugMode;
	private SRGMediaPlayerController playerController;

	public SimpleErrorMessage(Context context) {
		this(context, null);
	}

	public SimpleErrorMessage(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public SimpleErrorMessage(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);

		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inflater.inflate(R.layout.simple_error_message, this, true);

		debugMessageTextView = (TextView) findViewById(R.id.debug_message);
		userMessageTextView = (TextView) findViewById(R.id.user_message);
		cancelButton = findViewById(R.id.cancel);

		setOnClickListener(this);
		cancelButton.setOnClickListener(this);
		userMessageTextView.setOnClickListener(this);

		updateWithException(null);
	}

	private void updateWithException(Throwable e) {
		boolean visible = e != null;
		setVisibility(visible ? View.VISIBLE : View.GONE);
		if (visible) {
			updateTextsWithException(e);
		}
	}

	private void updateTextsWithException(Throwable e) {
		if (debugMode) {
			debugMessageTextView.setVisibility(View.VISIBLE);
		} else {
			debugMessageClickCount = DEFAULT_DEBUG_CLICK_COUNT;
			debugMessageTextView.setVisibility(View.GONE);
		}
		debugMessageTextView.setText(e.getMessage());
		userMessageTextView.setText(getLocalizedMessage(e));
	}

	protected String getLocalizedMessage(Throwable e) {
		return e.getLocalizedMessage();
	}

	@Override
	public void onClick(View v) {
		if (v == this || v == userMessageTextView) {
			--debugMessageClickCount;
			if (debugMessageClickCount == 0) {
				debugMessageTextView.setVisibility(View.VISIBLE);
			}
		} else if (v == cancelButton) {
			hide();
		} else {
			throw new IllegalArgumentException("onClick unexpected view " + v);
		}
	}

	private void hide() {
		setVisibility(View.GONE);
	}

	public void setDebugMode(boolean debugMode) {
		this.debugMode = debugMode;
	}

	public void attachToController(SRGMediaPlayerController playerController) {
		this.playerController = playerController;
		playerController.registerEventListener(this);
	}

	@Override
	public void onMediaPlayerEvent(SRGMediaPlayerController mp, SRGMediaPlayerController.Event event) {
		switch (event.type) {
			case FATAL_ERROR:
			case TRANSIENT_ERROR:
				updateWithException(event.exception);
				break;
			case STATE_CHANGE:
				if (event.state == SRGMediaPlayerController.State.READY) {
					hide();
				}
				break;
		}
	}
}

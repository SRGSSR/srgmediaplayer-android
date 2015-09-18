package ch.srg.mediaplayer.extras.overlay.control;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import java.util.ArrayList;

import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.SRGMediaPlayerException;
import ch.srg.mediaplayerextras.R;

/**
 * TODO: document your custom view class.
 */
public class LivePlayerControlView extends RelativeLayout implements SRGMediaPlayerController.Listener, View.OnClickListener, Runnable {

    private static final long PERIODIC_UPDATE_DELAY = 250;
    private SRGMediaPlayerController playerController;

    private Button playButton;
    private Button stopButton;

    private String urn;

    private Context context;

    public ArrayList<StopListener> stopListeners = new ArrayList<>();

    public LivePlayerControlView(Context context) {
        this(context, null);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LivePlayerControlView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        this.context = context;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.live_player_control, this, true);

        playButton = (Button) findViewById(R.id.live_player_control_button_play);
        stopButton = (Button) findViewById(R.id.live_player_control_button_stop);

        playButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);

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
                try {
                    playerController.play(urn);
                } catch (SRGMediaPlayerException e) {
                    e.printStackTrace();
                }
            } else if (v == stopButton) {
                //urn = playerController.getMediaIdentifier();
                playerController.release();
                for (StopListener listener : stopListeners){
                    listener.onStopCalled();
                }
            }
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

    public void playState() {
        stopButton.setVisibility(View.VISIBLE);
        playButton.setVisibility(View.GONE);
    }

    public void stopState() {
        stopButton.setVisibility(View.GONE);
        playButton.setVisibility(View.VISIBLE);
    }

    private void update() {
        if (playerController.isPlaying()) {
            playState();
        } else {
            stopState();
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

    public void addStopListener(StopListener listener){
        stopListeners.add(listener);
    }

    public void removeStopListener(StopListener listener){
        stopListeners.remove(listener);
    }

    public interface StopListener {

        void onStopCalled();

    }
}

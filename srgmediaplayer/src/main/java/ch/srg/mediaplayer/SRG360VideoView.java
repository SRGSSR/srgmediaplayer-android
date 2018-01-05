package ch.srg.mediaplayer;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.Surface;

import com.asha.vrlib.MDVRLibrary;

/**
 * Created by StahliJ on 04.01.2018.
 */

public class SRG360VideoView extends GLSurfaceView {

    public static final String TAG = "SRG360VideoView";

    public interface Callback {
        void onSurfaceReady(Surface surface);
    }

    private MDVRLibrary vrLibrary;
    private Callback callback;

    public SRG360VideoView(Context context, Callback callback) {
        super(context);
        this.callback = callback;
        Log.d(TAG, "current Thread = " + Thread.currentThread().getName());
        initVRLibrary();
        fireResume();
    }

    public void fireResume() {
        Log.d(TAG, "fireResume");
        vrLibrary.onResume(getContext());
    }

    public void firePause() {
        Log.d(TAG, "fireResume");
        vrLibrary.onPause(getContext());
    }

    public void fireDestroy() {
        Log.d(TAG, "fireDestroy");
        vrLibrary.onDestroy();
    }

    public void switchToCardBoard() {
        Log.d(TAG, "switchToCardBoard");
        vrLibrary.switchInteractiveMode(getContext(), MDVRLibrary.INTERACTIVE_MODE_CARDBORAD_MOTION);
        vrLibrary.switchDisplayMode(getContext(), MDVRLibrary.DISPLAY_MODE_GLASS);
    }

    public void switchToNormal360() {
        Log.d(TAG, "switchToNormal360");
        vrLibrary.switchInteractiveMode(getContext(), MDVRLibrary.INTERACTIVE_MODE_MOTION_WITH_TOUCH);
        vrLibrary.switchDisplayMode(getContext(), MDVRLibrary.DISPLAY_MODE_NORMAL);
    }

    private void initVRLibrary() {
        Log.d(TAG, "initVRLibrary ");
        vrLibrary = MDVRLibrary.with(getContext())
                .displayMode(MDVRLibrary.DISPLAY_MODE_NORMAL)
                .interactiveMode(MDVRLibrary.INTERACTIVE_MODE_MOTION_WITH_TOUCH)
                .asVideo(new MDVRLibrary.IOnSurfaceReadyCallback() {
                    @Override
                    public void onSurfaceReady(Surface surface) {
                        callback.onSurfaceReady(surface);
                    }
                })
                .build(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        fireResume();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        firePause();
    }

}

package ch.srg.mediaplayer.internal.session;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

import java.util.HashSet;
import java.util.Set;

import ch.srg.mediaplayer.service.AudioIntentReceiver;
import ch.srg.mediaplayer.service.R;
import ch.srg.mediaplayer.service.SRGMediaPlayerServiceMetaDataProvider;
import ch.srg.mediaplayer.service.utils.AppUtils;
import ch.srg.mediaplayer.service.utils.FetchBitmapTask;

/**
 * Created by npietri on 09.11.15.
 */
public class MediaSessionManager {

    private static final String TAG = "MediaSessionManager";

    private final Context context;
    private static MediaSessionManager instance;
    private MediaSessionCompat mediaSessionCompat;

    private FetchBitmapTask bitmapDecoderTask;
    private static int dimensionInPixels;
    private Bitmap mediaArtBitmap;

    private Set<Listener> listeners = new HashSet<>();

    protected MediaSessionManager(Context context) {
        this.context = context;
        try {
            dimensionInPixels = AppUtils.convertDpToPixel(context, context.getResources().getDimension(R.dimen.notification_image_size));
        } catch (Resources.NotFoundException e) {
            dimensionInPixels = 0;
        }
    }

    public static synchronized MediaSessionManager initialize(Context context) {
        if (instance == null) {
            Log.d(TAG, "New instance of MediaSessionManager is created");
            instance = new MediaSessionManager(context);
        }
        return instance;
    }

    public static MediaSessionManager getInstance() {
        if (instance == null) {
            String msg = "No MediaSessionManager instance was found, did you forget to initialize it?";
            Log.e(TAG, msg);
            throw new IllegalStateException(msg);
        }
        return instance;
    }

    private void createMediaSessionCompat() {
        ComponentName componentName = new ComponentName(context, AudioIntentReceiver.class);
        Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        intent.setComponent(componentName);

        mediaSessionCompat = new MediaSessionCompat(context, "MediaPlayerService", componentName, PendingIntent.getBroadcast(context, 0, intent, 0));
        mediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mediaSessionCompat.setActive(true);
        mediaSessionCompat.setCallback(new SRGMediaSessionCallback());

    }

    public MediaSessionCompat requestMediaSession(SRGMediaPlayerServiceMetaDataProvider serviceDataProvider, String mediaIdentifier) {
        Log.d(TAG, "requestMediaSession");
        if (mediaSessionCompat != null) {
            return mediaSessionCompat;
        }

        createMediaSessionCompat();

        boolean live = false;
        if (serviceDataProvider != null) {
            live = serviceDataProvider.isLive(mediaIdentifier);
        }

        String mediaThumbnailUri = "";

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();
        if (serviceDataProvider != null) {
            String title = serviceDataProvider.getTitle(mediaIdentifier);

            meta.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        }
        if (!live) {
            meta.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, serviceDataProvider != null ? serviceDataProvider.getDuration(mediaIdentifier) : 0);
        }

        mediaSessionCompat.setMetadata(meta.build());

        mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build());

        Log.d(TAG, "New mediaSessionCompat created with token: " + mediaSessionCompat.getSessionToken());

        return mediaSessionCompat;
    }

    public MediaSessionCompat requestMediaSession(MediaInfo info) {
        Log.d(TAG, "requestMediaSession()");
        if (mediaSessionCompat != null) {
            return mediaSessionCompat;
        }

        createMediaSessionCompat();

        MediaMetadataCompat.Builder meta = new MediaMetadataCompat.Builder();

        final MediaMetadata mm = info.getMetadata();
        MediaMetadataCompat currentMetadata = mediaSessionCompat.getController().getMetadata();
        MediaMetadataCompat.Builder newBuilder = currentMetadata == null
                ? new MediaMetadataCompat.Builder()
                : new MediaMetadataCompat.Builder(currentMetadata);
        mediaSessionCompat.setMetadata(
                newBuilder
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
                                mm.getString(MediaMetadata.KEY_TITLE))
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION,
                                info.getStreamDuration())
                        .build());

        mediaSessionCompat.setMetadata(meta.build());

        mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build());

        Log.d(TAG, "New mediaSessionCompat created with token: " + mediaSessionCompat.getSessionToken());

        return mediaSessionCompat;
    }

//    private void fetchMediaImage(String mediaThumbnail) {
//        Log.d(TAG, "Fetching MediaImage: " + mediaThumbnail);
//        if (TextUtils.isEmpty(mediaThumbnail)) {
//            return;
//        }
//        if (bitmapDecoderTask != null) {
//            bitmapDecoderTask.cancel(false);
//        }
//        Uri imgUri = Uri.parse(mediaThumbnail);
//
//        bitmapDecoderTask = new FetchBitmapTask() {
//            @Override
//            protected void onPostExecute(Bitmap bitmap) {
//                mediaArtBitmap = AppUtils.scaleAndFitBitmap(bitmap, dimensionInPixels,
//                        dimensionInPixels);
//                updateLockScreenImage(bitmap);
//
//                if (listeners.isEmpty()) {
//                    Log.d(TAG, "No listener found for bitmap Update");
//                } else {
//                    Log.d(TAG, listeners.size() + " listener(s) found for bitmap Update");
//                }
//                for (Listener listener : listeners) {
//                    listener.onBitmapUpdate(mediaArtBitmap);
//                }
//
//                if (this == bitmapDecoderTask) {
//                    bitmapDecoderTask = null;
//                }
//            }
//        };
//        bitmapDecoderTask.execute(imgUri);
//    }

    public void updateMediaSession(boolean playing) {
        if (mediaSessionCompat != null) {
            int state = playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;

            mediaSessionCompat.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(state, 0, 1.0f)
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE).build());

            if (listeners.isEmpty()) {
                Log.d(TAG, "No listener found for bitmap Update");
            } else {
                Log.d(TAG, listeners.size() + " listener(s) found for mediaSession Update");
            }
            for (Listener listener : listeners) {
                listener.onMediaSessionUpdated();
            }
        }
    }

    /*
     * Clears Media Session
     */
    public void clearMediaSession(MediaSessionCompat.Token token) {
        Log.d(TAG, "clearMediaSession: " + token);
        if (mediaSessionCompat != null && token != null && mediaSessionCompat.getSessionToken() == token) {
            mediaSessionCompat.setActive(false);
            mediaSessionCompat.release();
            mediaSessionCompat = null;
        }
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public interface Listener {
        void onBitmapUpdate(Bitmap bitmap);

        void onResumeSession();

        void onPauseSession();

        void onStopSession();

        void onMediaSessionUpdated();
    }

    private class SRGMediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public boolean onMediaButtonEvent(Intent mediaButtonIntent) {
            KeyEvent keyEvent = mediaButtonIntent
                    .getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (keyEvent != null && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE)) {
                if (listeners.isEmpty()) {
                    Log.d(TAG, "No listener found for onPauseSession");
                } else {
                    Log.d(TAG, listeners.size() + " listener(s) found for onPauseSession");
                }
                for (Listener listener : listeners) {
                    listener.onPauseSession();
                }
            } else if (keyEvent != null && keyEvent.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (listeners.isEmpty()) {
                    Log.d(TAG, "No listener found for onResumeSession");
                } else {
                    Log.d(TAG, listeners.size() + " listener(s) found for onResumeSession");
                }
                for (Listener listener : listeners) {
                    listener.onResumeSession();
                }
            }
            return true;
        }

        @Override
        public void onPlay() {
            super.onPlay();
            if (listeners.isEmpty()) {
                Log.d(TAG, "No listener found for onResumeSession");
            } else {
                Log.d(TAG, listeners.size() + " listener(s) found for onResumeSession");
            }
            for (Listener listener : listeners) {
                listener.onResumeSession();
            }
            updateMediaSession(true);
        }

        @Override
        public void onPause() {
            super.onPause();
            if (listeners.isEmpty()) {
                Log.d(TAG, "No listener found for onPauseSession");
            } else {
                Log.d(TAG, listeners.size() + " listener(s) found for onPauseSession");
            }
            for (Listener listener : listeners) {
                listener.onPauseSession();
            }
            updateMediaSession(false);
        }

        @Override
        public void onStop() {
            super.onStop();
            if (listeners.isEmpty()) {
                Log.d(TAG, "No listener found for onStopSession");
            } else {
                Log.d(TAG, listeners.size() + " listener(s) found for onStopSession");
            }
            for (Listener listener : listeners) {
                listener.onStopSession();
            }
            updateMediaSession(false);
        }
    }

}

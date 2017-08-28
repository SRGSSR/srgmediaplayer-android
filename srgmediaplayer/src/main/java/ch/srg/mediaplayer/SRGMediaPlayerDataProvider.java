package ch.srg.mediaplayer;

import android.net.Uri;
import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
@SuppressWarnings("UnnecessaryInterfaceModifier")
public interface SRGMediaPlayerDataProvider {
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({MEDIA_TYPE_UNKNOWN, MEDIA_TYPE_AUDIO, MEDIA_TYPE_VIDEO})
    public @interface SRGMediaType {
    }

    int MEDIA_TYPE_UNKNOWN = 0;
    int MEDIA_TYPE_AUDIO = 1;
    int MEDIA_TYPE_VIDEO = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({STREAM_HLS, STREAM_HTTP_PROGRESSIVE, STREAM_DASH, STREAM_LOCAL_FILE, STREAM_NOT_PLAYABLE})
    public @interface SRGStreamType {
    }

    int STREAM_HLS = 1;
    int STREAM_HTTP_PROGRESSIVE = 2;
    int STREAM_DASH = 3;
    int STREAM_LOCAL_FILE = 4;
    int STREAM_NOT_PLAYABLE = 5;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PLAYER_TYPE_CHROMECAST, PLAYER_TYPE_EXOPLAYER})
    public @interface SRGPlayerType {
    }

    int PLAYER_TYPE_EXOPLAYER = 0;
    int PLAYER_TYPE_CHROMECAST = 1;

    interface GetUriCallback {
        /**
         * @param mediaIdentifier     requested media identifier
         * @param uri                 Uri for the media
         * @param realMediaIdentifier the real mediaIdentifier played, in case of asking to play a logical segment
         * @param position            position in the stream
         * @param streamType          {@link #STREAM_DASH}, {@link #STREAM_HLS}, {@link #STREAM_HTTP_PROGRESSIVE}, {@link #STREAM_LOCAL_FILE}, {@link #STREAM_NOT_PLAYABLE}
         */
        void onUriLoadedOrUpdated(String mediaIdentifier, Uri uri, String realMediaIdentifier, Long position, @SRGStreamType int streamType);

        /**
         * @param mediaIdentifier requested media identifier
         * @param exception       associated exception
         */
        void onUriNonPlayable(String mediaIdentifier, SRGMediaPlayerException exception);

        /**
         * @param metadata Any metadata will be stored in controller and passed in events
         */
        void onMetadataLoadedOrUpdated(Object metadata);
    }

    interface MetadataMonitor {
        void start();

        void stop();

        MetadataMonitor NO_UPDATE = new MetadataMonitor() {
            @Override
            public void start() {
            }

            @Override
            public void stop() {
            }
        };
    }

    /**
     * Uri to play the media identifier. Must be constant for a single media identifier.
     *
     * @param mediaIdentifier media identifier
     * @param playerType      player delegate type ({@link #PLAYER_TYPE_CHROMECAST} or {@link #PLAYER_TYPE_EXOPLAYER}
     * @param getUriCallback  callback
     */
    MetadataMonitor startUriMonitor(String mediaIdentifier, @SRGPlayerType int playerType, GetUriCallback getUriCallback);
}

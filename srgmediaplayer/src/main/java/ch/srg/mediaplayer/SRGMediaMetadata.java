package ch.srg.mediaplayer;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Created by seb on 19/07/16.
 */
public interface SRGMediaMetadata {
    @NonNull
    String getUrn();

    @Nullable
    String getTitle();

    @Nullable
    String getDescription();

    @Nullable
    String getImageUrl();

    @Nullable
    String getImageTitle();

    @Nullable
    String getDate();

    long getDuration();

    @Nullable
    String getValidFrom();

    @Nullable
    String getValidTo();

    @Nullable
    String getShowTitle();

    boolean isLive();

    boolean hasDownload();

    @Nullable
    String getDownloadUri(String quality);

    @Nullable
    String getMediaType();
}

package ch.srg.mediaplayer.segment.model;

import android.support.annotation.NonNull;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class Segment implements Comparable<Segment> {

    private String identifier;
    private String title;
    private String description;
    private String imageUrl;
    private String blockingReason;
    private long markIn;
    private long markOut;
    private long duration;
    private int progress;
    private boolean isCurrent;
    private long publishedTimestamp;
    private boolean displayable;

    public Segment(String identifier, String title, String description, String imageUrl,
                   String blocking, long markIn, long markOut, long duration, long publishedTimestamp,
                   boolean displayable) {
        this.identifier = identifier;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
        this.markIn = markIn;
        this.markOut = markOut;
        this.duration = duration;
        this.publishedTimestamp = publishedTimestamp;
        this.displayable = displayable;
        blockingReason = blocking;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public long getMarkIn() {
        return markIn;
    }

    public long getMarkOut() {
        return markOut;
    }

    public long getDuration() {
        return duration;
    }

    public void setProgress(int value) {
        progress = value;
    }

    public int getProgress() {
        return progress;
    }

    public void setIsCurrent(boolean value) {
        isCurrent = value;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public long getPublishedTimestamp() {
        return publishedTimestamp;
    }

    public String getBlockingReason() {
        return blockingReason;
    }

    public boolean isBlocked() {
        return blockingReason != null;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isDisplayable() {
        return displayable;
    }

    @Override
    public int compareTo(@NonNull Segment another) {
        return ((int) (markIn - another.getMarkIn()));
    }

    @Override
    public String toString() {
        return "Segment{" +
                ", identifier='" + identifier + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", blockingReason='" + blockingReason + '\'' +
                ", markIn=" + markIn +
                ", markOut=" + markOut +
                ", duration=" + duration +
                ", progress=" + progress +
                ", isCurrent=" + isCurrent +
                ", publishedTimestamp=" + publishedTimestamp +
                ", displayable=" + displayable +
                '}';
    }

}

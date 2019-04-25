package ch.srg.mediaplayer;

import android.support.annotation.Nullable;

import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroup;

/**
 * Helper to manage ExoPlayer AudioTrack
 */
public class AudioTrack {
    public final int groupIndex;
    public final int trackIndex;
    public final String trackId;
    public final String language;
    public final String label;

    public AudioTrack(int groupIndex, int trackIndex, String trackId, String language, String label) {
        this.groupIndex = groupIndex;
        this.trackIndex = trackIndex;
        this.trackId = trackId;
        this.language = language;
        this.label = label;
    }

    @Nullable
    public static AudioTrack createFrom(TrackGroup trackGroup, int groupIndex, int trackIndex) {
        Format format = trackGroup.getFormat(trackIndex);
        if (format.id != null && format.language != null) {
            String label = format.label != null ? format.label : format.language;
            return new AudioTrack(groupIndex, trackIndex, format.id, format.language, label);
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AudioTrack that = (AudioTrack) o;

        if (trackIndex != that.trackIndex || groupIndex != that.groupIndex) return false;
        if (trackId != null ? !trackId.equals(that.trackId) : that.trackId != null) return false;
        return language != null ? language.equals(that.language) : that.language == null;

    }

    @Override
    public int hashCode() {
        int result = Integer.valueOf(groupIndex).hashCode();
        result = 31 * result + Integer.valueOf(trackIndex).hashCode();
        result = 31 * result + (trackId != null ? trackId.hashCode() : 0);
        result = 31 * result + (language != null ? language.hashCode() : 0);
        return result;
    }
}

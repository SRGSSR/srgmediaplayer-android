package ch.srg.mediaplayer;

public class SubtitleTrack {
    public int index;
    public String trackId;
    public String language;

    public SubtitleTrack(int index, String trackId, String language) {
        this.index = index;
        this.trackId = trackId;
        this.language = language;
    }
}
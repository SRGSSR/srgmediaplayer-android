package ch.srg.mediaplayer.internal.exoplayer;

import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.chunk.MultiTrackChunkSource;
import com.google.android.exoplayer.upstream.BandwidthMeter;

public interface RendererBuilderCallback {

	void onRenderers(String[][] trackNames, MultiTrackChunkSource[] multiTrackSources, TrackRenderer[] renderers, BandwidthMeter bandwidthMeter);

	void onRenderersError(Exception e);
}

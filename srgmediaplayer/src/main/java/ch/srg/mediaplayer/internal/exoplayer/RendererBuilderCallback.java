package ch.srg.mediaplayer.internal.exoplayer;

import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.upstream.BandwidthMeter;

public interface RendererBuilderCallback {

	void onRenderers(TrackRenderer[] renderers, BandwidthMeter bandwidthMeter);

	void onRenderersError(Exception e);
}

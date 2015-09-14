/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.srg.mediaplayer.internal.exoplayer;

import android.content.Context;
import android.media.MediaCodec;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;

import java.io.IOException;


/**
 * A {@link RendererBuilder} for HLS.
 */

public class HlsRendererBuilder implements RendererBuilder, ManifestCallback<HlsPlaylist> {

    private static final int BUFFER_SEGMENT_SIZE = 256 * 1024;
    private static final int BUFFER_SEGMENTS = 64;

    private final Context context;
    private final String userAgent;
    private final String url;
    private final AudioCapabilities audioCapabilities;

    private ExoPlayerDelegate player;
    private RendererBuilderCallback callback;

    public HlsRendererBuilder(Context context, String userAgent, String url,
                              AudioCapabilities audioCapabilities) {
        this.context = context;
        this.userAgent = userAgent;
        this.url = url;
        this.audioCapabilities = audioCapabilities;
    }

    @Override
    public void buildRenderers(ExoPlayerDelegate player, RendererBuilderCallback callback) {
        this.player = player;
        this.callback = callback;
        HlsPlaylistParser parser = new HlsPlaylistParser();
        ManifestFetcher<HlsPlaylist> playlistFetcher = new ManifestFetcher<HlsPlaylist>(url, new DefaultHttpDataSource(userAgent, null), parser);
        playlistFetcher.singleLoad(player.getMainHandler().getLooper(), this);
    }

    @Override
    public void onSingleManifestError(IOException e) {
        callback.onRenderersError(e);
    }

    @Override
    public void onSingleManifest(HlsPlaylist manifest) {
        LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();

        DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        HlsChunkSource chunkSource = new HlsChunkSource(dataSource, url, manifest, bandwidthMeter, null, HlsChunkSource.ADAPTIVE_MODE_SPLICE, audioCapabilities);
        HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, loadControl, BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, player.getMainHandler(), player, ExoPlayerDelegate.TYPE_VIDEO);
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(sampleSource, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, player.getMainHandler(), player, 50);
        MediaCodecAudioTrackRenderer audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource);

        TrackRenderer[] renderers = new TrackRenderer[ExoPlayerDelegate.RENDERER_COUNT];
        renderers[ExoPlayerDelegate.TYPE_VIDEO] = videoRenderer;
        renderers[ExoPlayerDelegate.TYPE_AUDIO] = audioRenderer;
        callback.onRenderers(null, null, renderers, bandwidthMeter);
    }

}


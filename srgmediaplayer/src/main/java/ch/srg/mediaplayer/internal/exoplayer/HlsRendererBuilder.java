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

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.MediaCodec;
import android.os.Handler;

import com.google.android.exoplayer.DefaultLoadControl;
import com.google.android.exoplayer.LoadControl;
import com.google.android.exoplayer.MediaCodecAudioTrackRenderer;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.hls.DefaultHlsTrackSelector;
import com.google.android.exoplayer.hls.HlsChunkSource;
import com.google.android.exoplayer.hls.HlsMasterPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylist;
import com.google.android.exoplayer.hls.HlsPlaylistParser;
import com.google.android.exoplayer.hls.HlsSampleSource;
import com.google.android.exoplayer.hls.PtsTimestampAdjusterProvider;
import com.google.android.exoplayer.metadata.MetadataTrackRenderer;
import com.google.android.exoplayer.metadata.id3.Id3Frame;
import com.google.android.exoplayer.metadata.id3.Id3Parser;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.util.ManifestFetcher;
import com.google.android.exoplayer.util.ManifestFetcher.ManifestCallback;

import java.io.IOException;
import java.util.List;


/**
 * A {@link RendererBuilder} for HLS.
 */

public class HlsRendererBuilder implements RendererBuilder, ManifestCallback<HlsPlaylist> {
    private static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    private static final int BUFFER_SEGMENTS = 256;
    private static final int TEXT_BUFFER_SEGMENTS = 2;

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
        boolean haveSubtitles = false;
        boolean haveAudios = false;
        if (manifest instanceof HlsMasterPlaylist) {
            HlsMasterPlaylist masterPlaylist = (HlsMasterPlaylist) manifest;
            haveSubtitles = !masterPlaylist.subtitles.isEmpty();
            // Following works with Apple streams but not SWI streams... So we will always force audio
//            haveAudios = !masterPlaylist.audios.isEmpty();
            haveAudios = true;
        }

        LoadControl loadControl = new DefaultLoadControl(new DefaultAllocator(BUFFER_SEGMENT_SIZE));
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        PtsTimestampAdjusterProvider timestampAdjusterProvider = new PtsTimestampAdjusterProvider();

        DataSource dataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
        HlsChunkSource chunkSource = new HlsChunkSource(true, dataSource, url, manifest, DefaultHlsTrackSelector.newDefaultInstance(context), bandwidthMeter, timestampAdjusterProvider, HlsChunkSource.ADAPTIVE_MODE_SPLICE, player);
        Handler mainHandler = player.getMainHandler();
        HlsSampleSource sampleSource = new HlsSampleSource(chunkSource, loadControl, BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, ExoPlayerDelegate.TYPE_VIDEO);
        @SuppressLint("InlinedApi")
        MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context, sampleSource, MediaCodecSelector.DEFAULT, MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, 5000, mainHandler, player, 50);
        MetadataTrackRenderer<List<Id3Frame>> id3Renderer = new MetadataTrackRenderer<>(sampleSource, new Id3Parser(), player, mainHandler.getLooper());

        TrackRenderer textRenderer = null;
        MediaCodecAudioTrackRenderer audioRenderer = null;

        if (haveAudios) {
            audioRenderer = new MediaCodecAudioTrackRenderer(sampleSource, MediaCodecSelector.DEFAULT);
        }
        if (haveSubtitles) {
            DataSource textDataSource = new DefaultUriDataSource(context, bandwidthMeter, userAgent);
            HlsChunkSource textChunkSource = new HlsChunkSource(false /* isMaster */, textDataSource,
                    url, manifest, DefaultHlsTrackSelector.newSubtitleInstance(), bandwidthMeter,
                    timestampAdjusterProvider, HlsChunkSource.ADAPTIVE_MODE_SPLICE, player);
            HlsSampleSource textSampleSource = new HlsSampleSource(textChunkSource, loadControl,
                    TEXT_BUFFER_SEGMENTS * BUFFER_SEGMENT_SIZE, mainHandler, player, ExoPlayerDelegate.TYPE_TEXT);
            textRenderer = new TextTrackRenderer(textSampleSource, player, mainHandler.getLooper());
        }

        TrackRenderer[] renderers = new TrackRenderer[ExoPlayerDelegate.RENDERER_COUNT];
        renderers[ExoPlayerDelegate.TYPE_VIDEO] = videoRenderer;
        renderers[ExoPlayerDelegate.TYPE_AUDIO] = audioRenderer;
        renderers[ExoPlayerDelegate.TYPE_TEXT] = textRenderer;
        renderers[ExoPlayerDelegate.TYPE_METADATA] = id3Renderer;

        callback.onRenderers(renderers, bandwidthMeter);
        callback.onHlsChunkSource(chunkSource);
    }

}


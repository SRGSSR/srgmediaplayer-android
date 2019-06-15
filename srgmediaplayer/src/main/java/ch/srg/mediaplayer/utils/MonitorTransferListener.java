package ch.srg.mediaplayer.utils;

import android.util.Log;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class MonitorTransferListener implements TransferListener {
    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        SRGMediaPlayerController.BANDWIDTH_METER.onTransferInitializing(source, dataSpec, isNetwork);
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        SRGMediaPlayerController.BANDWIDTH_METER.onTransferStart(source, dataSpec, isNetwork);
    }

    @Override
    public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
        SRGMediaPlayerController.BANDWIDTH_METER.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred);
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        Log.v(SRGMediaPlayerController.TAG, "DataSource: " + dataSpec.uri + " (" + dataSpec.length + ") ");
        SRGMediaPlayerController.BANDWIDTH_METER.onTransferEnd(source, dataSpec, isNetwork);
    }
}

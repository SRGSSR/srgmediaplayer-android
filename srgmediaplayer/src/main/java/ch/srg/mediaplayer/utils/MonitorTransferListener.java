package ch.srg.mediaplayer.utils;

import android.support.annotation.NonNull;
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
    private TransferListener delegate;

    public MonitorTransferListener(@NonNull TransferListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onTransferInitializing(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        delegate.onTransferInitializing(source, dataSpec, isNetwork);
    }

    @Override
    public void onTransferStart(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        delegate.onTransferStart(source, dataSpec, isNetwork);
    }

    @Override
    public void onBytesTransferred(DataSource source, DataSpec dataSpec, boolean isNetwork, int bytesTransferred) {
        delegate.onBytesTransferred(source, dataSpec, isNetwork, bytesTransferred);
    }

    @Override
    public void onTransferEnd(DataSource source, DataSpec dataSpec, boolean isNetwork) {
        Log.v(SRGMediaPlayerController.TAG, "DataSource: " + dataSpec.uri + " (" + dataSpec.length + ") ");
        delegate.onTransferEnd(source, dataSpec, isNetwork);
    }
}

package ch.srg.mediaplayer.utils;

import androidx.annotation.WorkerThread;
import com.google.android.exoplayer2.drm.DrmInitData;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public interface LicenseStoreDelegate {
    /**
     * Fetch keyset for the given init data.
     *
     * @param drmInitData drm init data
     * @return key set or null if not available.
     */
    @WorkerThread
    byte[] fetch(DrmInitData drmInitData);

    /**
     * Store keyset for the given init data.
     *
     * @param drmInitData drm init data
     * @param keySet      associated keyset or null to discard
     */
    @WorkerThread
    void store(DrmInitData drmInitData, byte[] keySet);
}
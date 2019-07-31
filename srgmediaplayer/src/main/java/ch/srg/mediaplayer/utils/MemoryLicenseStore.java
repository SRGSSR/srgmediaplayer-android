package ch.srg.mediaplayer.utils;

import com.google.android.exoplayer2.drm.DrmInitData;

import java.util.HashMap;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class MemoryLicenseStore implements LicenseStoreDelegate {
    private HashMap<DrmInitData, byte[]> map = new HashMap<>();

    @Override
    public byte[] fetch(DrmInitData drmInitData) {
        return map.get(drmInitData);
    }


    @Override
    public void store(DrmInitData drmInitData, byte[] keySet) {
        map.put(drmInitData, keySet);
    }
}

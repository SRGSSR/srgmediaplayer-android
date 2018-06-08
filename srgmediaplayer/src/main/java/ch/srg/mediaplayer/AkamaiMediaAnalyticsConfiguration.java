package ch.srg.mediaplayer;

import android.util.Pair;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public interface AkamaiMediaAnalyticsConfiguration {
    String getAkamaiMediaAnalyticsConfigUrl();

    String getAkamaiMediaAnalyticsViewerId();

    Iterable<? extends Pair<String, String>> getAkamaiMediaAnalyticsDataSet();
}

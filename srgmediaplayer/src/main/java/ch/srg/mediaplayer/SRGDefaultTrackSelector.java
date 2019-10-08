package ch.srg.mediaplayer;

import android.text.TextUtils;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public final class SRGDefaultTrackSelector extends DefaultTrackSelector {
    private static final String TAG = "SRGTrackSelector";
    private Set<Format> blackListVideoTracks = new HashSet<>();

    public SRGDefaultTrackSelector(TrackSelection.Factory trackSelectionFactory) {
        super(trackSelectionFactory);
    }

    public boolean blacklistCurrentTrackSelection(TrackSelectionArray trackSelections) {
        MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (trackSelections == null || mappedTrackInfo == null) {
            return false;
        }
        for (int renderId = 0; renderId < trackSelections.length; renderId++) {
            TrackSelection trackSelection = trackSelections.get(renderId);
            if (trackSelection != null) {
                Format format = trackSelection.getSelectedFormat();
                if (format != null && TextUtils.equals(format.containerMimeType, "video/mp4")) {
                    if (blackListVideoTracks.add(format)) {
                        Log.d(TAG, "Black list Track[" + format.containerMimeType + "] " + format.id + " " + format.bitrate + " " + format.width + "X" + format.height);
                        List<Integer> listPlayableTracks = new ArrayList<>();
                        for (int i = 0; i < trackSelection.length(); i++) {
                            if (!blackListVideoTracks.contains(trackSelection.getFormat(i))) {
                                listPlayableTracks.add(trackSelection.getIndexInTrackGroup(i));
                            }
                        }
                        if (!listPlayableTracks.isEmpty()) {
                            int[] tabTrackId = new int[listPlayableTracks.size()];
                            for (int i = 0; i < tabTrackId.length; i++) {
                                tabTrackId[i] = listPlayableTracks.get(i);
                            }
                            TrackGroupArray renderTrackGroup = mappedTrackInfo.getTrackGroups(renderId);
                            int groupIndex = renderTrackGroup.indexOf(trackSelection.getTrackGroup());
                            SelectionOverride blacklistedOverride = new SelectionOverride(groupIndex, tabTrackId, C.SELECTION_REASON_ADAPTIVE, 0);
                            setParameters(buildUponParameters().setSelectionOverride(renderId, mappedTrackInfo.getTrackGroups(renderId), blacklistedOverride));
                            return true; // Because we doesn't handle audio track, audio track are not adaptive Track group.
                        }
                    }
                }
            }
        }
        return false;
    }
}

package ch.srg.mediaplayer.segment.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;

import ch.srg.mediaplayer.ControlTouchListener;
import ch.srg.mediaplayer.R;
import ch.srg.mediaplayer.SRGMediaPlayerController;
import ch.srg.mediaplayer.segment.adapter.BaseSegmentAdapter;

/**
 * Copyright (c) SRG SSR. All rights reserved.
 * <p>
 * License information is available from the LICENSE file.
 */
public class SegmentView extends RecyclerView {
    private SRGMediaPlayerController controller;
    @Nullable
    private ControlTouchListener controlTouchListener;
    @Nullable
    private BaseSegmentAdapter adapter;

    public SegmentView(Context context) {
        this(context, null);
    }

    public SegmentView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SegmentView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.SegmentView, 0, 0);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false);
        setLayoutManager(linearLayoutManager);

        // TODO Items change often during seek. Disabling animator fix the flashing, is there a better way to do this?
        setItemAnimator(null);
        a.recycle();
    }

    public void setup(@NonNull BaseSegmentAdapter adapter,
                      @NonNull SRGMediaPlayerController controller,
                      @Nullable ControlTouchListener controlTouchListener){
        this.controlTouchListener = controlTouchListener;
        this.adapter = adapter;
        this.controller = controller;
        setAdapter(this.adapter);
        this.adapter.updateWithMediaPlayerController(controller);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        boolean handled = super.onTouchEvent(e);
        if (handled && controlTouchListener != null) {
            controlTouchListener.onMediaControlTouched();
        }
        return handled;
    }
}

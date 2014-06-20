package com.sparseboolean.ifexplorer.ui;

import android.graphics.PorterDuff;
import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import gem.kevin.util.FileUtil;
import gem.kevin.util.ui.NamedColor;
import gem.kevin.widget.DropAcceptable;
import gem.kevin.widget.OnDropDelegate;
import gem.kevin.widget.OpenDelegate;
import gem.kevin.widget.Openable;

public class DropableFilePathButton extends FilePathButton implements
        DropAcceptable, Openable {
    private static final String TAG = "gem-kevin_DropableFilePathButton";

    public static final long HOVER_DECISION_COUNTDOWN = 1000L;
    public static final int COLOR_DROP_AVAILABLE = 0x37000000 | NamedColor.RoyalBlue;
    public static final int COLOR_DROP_NA_OPEN_AVAILABLE = 0x50000000 | NamedColor.LightCoral;
    public static final int APLAH_TRANSPARENT = 0x00ffffff;

    private OnDropDelegate mOnDropDelegate;
    private OpenDelegate mOpenDelegate;

    private CountDownTimer mHoverCountDownTimer; // Do a lazy initialization

    public DropableFilePathButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DropableFilePathButton(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public DropableFilePathButton(Context context, String path,
            FilePathButtonCallback callback) {
        super(context, path, callback);
    }

    @Override
    public boolean canAcceptDrop() {
        if (mOnDropDelegate != null) {
            return mOnDropDelegate.checkDropDataAcceptable(null, this);
        }

        return false;
    }

    @Override
    public boolean canAppendContent() {
        return FileUtil.isWritableDirectory(mFilePath);
    }

    @Override
    public boolean canOpen() {
        return FileUtil.isReadableDirectory(mFilePath);
    }

    @Override
    public void markChildCanGetDragEvent(boolean childCanGetDragEvent) {
        // this is a final DropAcceptable,
        // no child to get drag events
    }

    @Override
    public boolean onDragEnded(DragEvent event) {
        boolean success = event.getResult();
        if (mOnDropDelegate != null) {
            if (!success) {
                mOnDropDelegate.onDropFailed(this);
            }
        }
        return false;
    }

    @Override
    public boolean onDragEntered(DragEvent event) {
        boolean canAcceptDrop = canAcceptDrop();
        boolean canOpen = canOpen();
        if (canAcceptDrop) {
            mPathButton.getBackground().setColorFilter(COLOR_DROP_AVAILABLE,
                    PorterDuff.Mode.MULTIPLY);
        } else {
            if (canOpen) {
                mPathButton.getBackground().setColorFilter(
                        COLOR_DROP_NA_OPEN_AVAILABLE, PorterDuff.Mode.MULTIPLY);
            }
        }

        // If it can't open, then we do not call onDragHover on it.
        // Bind behaviors 'open' and 'drag hover' to a single logic for performance purpose.
        if (canOpen) {
            if (mHoverCountDownTimer == null) {
                mHoverCountDownTimer = new CountDownTimer(
                        HOVER_DECISION_COUNTDOWN, HOVER_DECISION_COUNTDOWN / 10) {
                    @Override
                    public void onFinish() {
                        onHover();
                    }

                    @Override
                    public void onTick(long millisUntilFinished) {
                        // Do nothing
                        Log.i(TAG, "Tick: " + millisUntilFinished);
                    }
                };
            }

            mHoverCountDownTimer.start();
        }

        return canAcceptDrop;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED:
            Log.i(TAG, "DFPB Drag Started.");
            return onDragStarted(event);
        case DragEvent.ACTION_DRAG_ENTERED:
            Log.i(TAG, "DFPB Drag entered.");
            return onDragEntered(event);
        case DragEvent.ACTION_DRAG_LOCATION:
            return onDragMoveOn(event);
        case DragEvent.ACTION_DRAG_EXITED:
            Log.i(TAG, "DFPB Drag exited.");
            return onDragExited(event);
        case DragEvent.ACTION_DROP:
            Log.i(TAG, "DFPB Drag droped.");
            return onDrop(event);
        case DragEvent.ACTION_DRAG_ENDED:
            Log.i(TAG, "DFPB Drag ended.");
            return onDragEnded(event);
        }

        return super.onDragEvent(event);
    }

    @Override
    public boolean onDragExited(DragEvent event) {
        mPathButton.getBackground().clearColorFilter();
        if (mHoverCountDownTimer != null) {
            mHoverCountDownTimer.cancel();
        }

        return true;
    }

    @Override
    public boolean onDragMoveOn(DragEvent event) {
        // Do nothing
        return true;
    }

    @Override
    public boolean onDragStarted(DragEvent event) {
        return true;
    }

    @Override
    public boolean onDrop(DragEvent event) {
        mPathButton.getBackground().clearColorFilter();

        if (mHoverCountDownTimer != null) {
            mHoverCountDownTimer.cancel();
        }

        if (mOnDropDelegate != null) {
            Object data = mOnDropDelegate.generateDropData();
            boolean result = true;
            if (mOnDropDelegate.checkDropDataAcceptable(data, this)) {
                result = mOnDropDelegate.handleDrop(data, this);
                if (result) {
                    mOnDropDelegate.onDropSuccess(this);
                } else {
                    mOnDropDelegate.onDropFailed(this);
                }
            } else {
                if (mOnDropDelegate.shouldDispathUnacceptableDropToParent(this)) {
                    result = super.onDragEvent(event);
                } else {
                    // Only notify drop denial when the view has indicated it could possibly
                    // accept drop (Actual it could not)
                    // Task 'canOpen' as a possible drop indicator because it also changed the UI.
                    // User might tend to think it is dropable.
                    if (canOpen()) {
                        mOnDropDelegate.notifyDropDataDenied(data, this);
                    }
                    mOnDropDelegate.onDropFailed(this);
                }
            }

            return result;
        }

        return true;
    }

    @Override
    public void onHover() {
        if (mOnDropDelegate != null) {
            mOnDropDelegate.onHover(this);
        }
    }

    @Override
    public void open() {
        Log.i(TAG, "open");
        if (mOpenDelegate != null) {
            mOpenDelegate.doOpen(this);
        }
    }

    public void setOnDropDelegate(OnDropDelegate delegate) {
        mOnDropDelegate = delegate;
    }

    public void setOpenDelegate(OpenDelegate delegate) {
        mOpenDelegate = delegate;
    }
}

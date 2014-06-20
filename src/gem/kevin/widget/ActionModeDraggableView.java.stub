package gem.kevin.widget;

import gem.com.readystatesoftware.viewbadger.BadgeView;
import gem.kevin.util.ui.NamedColor;

import java.util.LinkedList;

import android.content.ClipData;
import android.content.Context;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class ActionModeDraggableView extends FrameLayout implements
        DropAcceptable, Openable {
    public static class DragContextInfo {
        public Object data = null;
        public ViewParent parentView = null;
        public int positionAsChild = POSITION_UNORDERED;
        public ImageView draggingView = null;
        public ImageView dragReadyView = null;
        public View normalView = null;
    }

    public interface DragProgressDelegate {
        public DragShadowBuilder generateDragShadow(
                ActionModeDraggableView dragSource);

        public void interruptDragPreparation(
                final ActionModeDraggableView dragSource);

        public boolean isDragOpDelegated(
                final ActionModeDraggableView dragSource);

        public boolean isDragPrepared(final ActionModeDraggableView dragSource);

        public void notifyAutoDragStarted(
                final ActionModeDraggableView dragSource);

        public void prepareDrag(final ActionModeDraggableView dragSource);

        public void revertDragPreparation(
                final ActionModeDraggableView dragSource);
    }

    private static final String TAG = "gem-kevin_ActionModeDraggableView";
    public static final long HOVER_DECISION_COUNTDOWN = 1000L;
    public static final long DRAG_DECISION_INTERVAL = 300L;

    public static final int POSITION_UNORDERED = -1;
    public static final int COLOR_DROP_AVAILABLE = 0x66000000 | NamedColor.RoyalBlue;
    public static final int COLOR_DROP_NA_OPEN_AVAILABLE = 0x50000000 | NamedColor.LightCoral;

    public static final int APLAH_TRANSPARENT = 0x00ffffff;

    private DragContextInfo mDragContextInfo;
    private BadgeView mBadgeView;
    private DragProgressDelegate mDragProgressDelegate;
    private LinkedList<Object> mDragBuddies;
    private boolean mInActionMode = false;
    private boolean mDragable = true;
    private boolean mIsNewDragOp = true;
    private boolean mDragging = false;
    private boolean mDragReady = false;
    private boolean mDragCanceled = false;

    private OnDropDelegate mOnDropDelegate;
    private CountDownTimer mHoverCountDownTimer; // Do a lazy initialization

    private OpenDelegate mOpenDelegate;

    // TODO: READY TO DRAG to solve touch point up outside the view.

    private long mSavedDownTime;

    public ActionModeDraggableView(Context context, AttributeSet attrs) {
        super(context, attrs);
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
        return canAcceptDrop();
    }

    @Override
    public boolean canOpen() {
        if (mOpenDelegate != null) {
            return mOpenDelegate.checkOpenable(this);
        }

        return false;
    }

    public void doClickInActionMode() {
        if (mInActionMode && mDragContextInfo != null
                && mDragContextInfo.parentView instanceof AbsListView) {
            Log.i(TAG, "Do click in action mode.");
            if (mDragReady) {
                if (mDragProgressDelegate != null) {
                    mDragProgressDelegate.revertDragPreparation(this);
                }
                mDragReady = false;
            } else {
                // Due to the interactive ability of this widget
                AbsListView parentListView = (AbsListView) mDragContextInfo.parentView;
                int position = mDragContextInfo.positionAsChild;
                if (parentListView != null && position >= 0) {
                    boolean lastChecked = parentListView
                            .isItemChecked(position);

                    parentListView.setItemChecked(position, !lastChecked);
                    parentListView.invalidate();
                }
            }
        }
    }

    public boolean doDrag(ClipData data, DragShadowBuilder shadow) {
        mDragging = startDrag(data, shadow, null, 0);
        return mDragging;
    }

    public boolean doDrag(DragShadowBuilder shadow) {
        Log.i(TAG, "do drag.");
        ClipData clipData;
        if (getDragContextInfo().data instanceof ClipData) {
            clipData = (ClipData) getDragContextInfo().data;
            mDragging = startDrag(clipData, shadow, null, 0);
        } else {
            mDragging = startDrag(null, shadow, null, 0);
        }

        return mDragging;
    }

    public BadgeView getBadgeView() {
        return mBadgeView;
    }

    public LinkedList<Object> getDragBuddies() {
        return mDragBuddies;
    }

    public DragContextInfo getDragContextInfo() {
        if (mDragContextInfo == null) {
            mDragContextInfo = getDefaultDragContextInfo();
        }

        return mDragContextInfo;
    }

    public DragProgressDelegate getDragDropDelegate() {
        return mDragProgressDelegate;
    }

    public OnDropDelegate getOnDropDelegate() {
        return mOnDropDelegate;
    }

    public boolean isDragCanceled() {
        return mDragCanceled;
    }

    public boolean isDragReady() {
        return mDragReady;
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

        // Set background color to indicate 
        // it can be drop or can be opened
        if (canAcceptDrop) {
            setBackgroundColor(COLOR_DROP_AVAILABLE);
        } else {
            if (canOpen) {
                setBackgroundColor(COLOR_DROP_NA_OPEN_AVAILABLE);
            }
        }

        // If it can't open, then we do not call onDragHover on it.
        // Bind behaviors 'open' and 'drag hover' to a single logic 
        // for performance purpose.
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
            Log.i(TAG, "ACDV Drag Started.");
            return onDragStarted(event);
        case DragEvent.ACTION_DRAG_ENTERED:
            Log.i(TAG, "ACDV Drag entered.");
            return onDragEntered(event);
        case DragEvent.ACTION_DRAG_LOCATION:
            return onDragMoveOn(event);
        case DragEvent.ACTION_DRAG_EXITED:
            Log.i(TAG, "ACDV Drag exited.");
            return onDragExited(event);
        case DragEvent.ACTION_DROP:
            Log.i(TAG, "ACDV Drag droped.");
            return onDrop(event);
        case DragEvent.ACTION_DRAG_ENDED:
            Log.i(TAG, "ACDV Drag droped.");
            return onDragEnded(event);
        }

        return super.onDragEvent(event);
    }

    @Override
    public boolean onDragExited(DragEvent event) {
        setBackgroundColor(APLAH_TRANSPARENT);

        if (mHoverCountDownTimer != null) {
            mHoverCountDownTimer.cancel();
        }

        return false;
    }

    @Override
    public boolean onDragMoveOn(DragEvent event) {
        // do nothing
        return true;
    }

    @Override
    public boolean onDragStarted(DragEvent event) {
        return true;
    }

    @Override
    public boolean onDrop(DragEvent event) {
        setBackgroundColor(APLAH_TRANSPARENT);

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
                    Log.i(TAG, "dispatch unacceptable drop to parent.");
                    result = super.onDragEvent(event);
                } else {
                    // Only notify drop denial when the view has indicated it could possibly
                    // accept drop (Actual it could not)
                    // Task 'canOpen' as a possible drop indicator because it also changed the UI
                    // as the 'canAcceptDrop' did. User might tend to think it is dropable.
                    if (canOpen()) {
                        mOnDropDelegate.notifyDropDataDenied(data, this);
                    }
                    mOnDropDelegate.onDropFailed(this);
                }
            }

            return result;
        }

        return false;
    }

    @Override
    public void onHover() {
        if (mOnDropDelegate != null) {
            mOnDropDelegate.onHover(this);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.i(TAG, "onTouchEvent");
        if (!mDragable) {
            return super.onTouchEvent(event);
        }

        boolean result = super.onTouchEvent(event);
        switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            mIsNewDragOp = true;
            mDragging = false;
            mDragCanceled = false;
            if (mInActionMode) {
                mSavedDownTime = System.currentTimeMillis();
                result = true;
            }
            break;
        case MotionEvent.ACTION_MOVE:
            Log.i(TAG, "ACTION MOVE" + " parent-width: " + this.getWidth()
                    + " parent-height: " + this.getHeight() + " touch x: "
                    + event.getX() + " touch y: " + event.getY());
            if (mInActionMode) {
                long interval = System.currentTimeMillis() - mSavedDownTime;
                Log.i(TAG, "Interval is: " + interval);
                if (interval > DRAG_DECISION_INTERVAL) {
                    if (mIsNewDragOp) {
                        prepareDrag();
                    } else {
                        if (mDragProgressDelegate != null) {
                            if (!mDragProgressDelegate.isDragOpDelegated(this)) {
                                Log.i(TAG, "Drag is not delegated.");
                                if (mDragProgressDelegate.isDragPrepared(this)
                                        && !mDragging) {
                                    Log.i(TAG, "Drag is prepared, do drag!");
                                    doDrag(mDragProgressDelegate
                                            .generateDragShadow(this));
                                    mDragProgressDelegate
                                            .notifyAutoDragStarted(this);
                                }
                            } else {
                                Log.i(TAG, "Drag is delegated.");
                            }
                        } else {
                            if (!mDragging) {
                                doDrag(new DragShadowBuilder(this));
                            }
                        }
                    }
                }
                result = true;
            }
            break;
        case MotionEvent.ACTION_UP:
            Log.i(TAG, "ACTION up");
            mIsNewDragOp = true;
            if (mInActionMode) {
                long interval = System.currentTimeMillis() - mSavedDownTime;
                Log.i(TAG, "Interval is: " + interval);
                if (interval > DRAG_DECISION_INTERVAL) {
                    if (mDragProgressDelegate != null) {
                        if (!mDragProgressDelegate.isDragPrepared(this)) {
                            mDragProgressDelegate
                                    .interruptDragPreparation(this);
                        }

                        mDragReady = false;
                        Log.i(TAG, "revert drag prepartion for action up.");
                        mDragProgressDelegate.revertDragPreparation(this);
                    }
                } else {
                    doClickInActionMode();
                }
            } else {
                doClickInActionMode();
            }

            result = true;
            break;
        }

        if (mInActionMode) {
            return result;
        } else {
            return super.onTouchEvent(event);
        }
    }

    @Override
    public void open() {
        if (mOpenDelegate != null) {
            mOpenDelegate.doOpen(this);
        }
    }

    public void setBadgeView(BadgeView badge) {
        mBadgeView = badge;
    }

    @Override
    public void markChildCanGetDragEvent(boolean childCanGetDragEvent) {
        // this is a final DropAcceptable,
        // no child to get drag events
    }

    public void setDragable(boolean dragable) {
        mDragable = dragable;
    }

    public void setDragBuddies(LinkedList<Object> buddies) {
        mDragBuddies = buddies;
    }

    public void setDragContextInfo(DragContextInfo info) {
        mDragContextInfo = info;
    }

    public void setDragDropDelegate(DragProgressDelegate delegate) {
        mDragProgressDelegate = delegate;
    }

    public void setDragReady(boolean dragReady) {
        mDragReady = dragReady;
    }

    public void setInActionMode(boolean inActionMode) {
        mInActionMode = inActionMode;
    }

    public void setOnDropDelegate(OnDropDelegate delegate) {
        mOnDropDelegate = delegate;
    }

    public void setOpenDelegate(OpenDelegate delegate) {
        mOpenDelegate = delegate;
    }

    private DragContextInfo getDefaultDragContextInfo() {
        DragContextInfo info = new DragContextInfo();
        info.parentView = getParent();

        return info;
    }

    private void prepareDrag() {
        if (!mDragReady) {
            if (mDragProgressDelegate != null) {
                mDragProgressDelegate.prepareDrag(this);
            }
        } else {
            Log.i(TAG, "Drag is already prepared.");
        }

        mIsNewDragOp = false;
    }
}

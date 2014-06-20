package com.sparseboolean.ifexplorer.ui;

import gem.kevin.innov.FilePathUrlManager;
import gem.kevin.util.FileUtil;
import gem.kevin.util.ui.ViewUtil;
import gem.kevin.widget.DropAcceptable;
import gem.kevin.widget.OnDropDelegate;
import gem.kevin.widget.OpenDelegate;

import java.util.ArrayList;

import com.sparseboolean.ifexplorer.R;
import com.sparseboolean.ifexplorer.ui.FilePathButton.FilePathButtonCallback;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.CountDownTimer;
import android.util.AttributeSet;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.ScaleAnimation;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FilePathNavigator extends LinearLayout implements
        FilePathButtonCallback, DropAcceptable {

    public interface NavigationCallback {
        public void navigateToPath(String path);
    }

    private static final String TAG = "FilePathNavigator";

    public static int MAX_BUTTON_NUM = 5;

    private ViewGroup mNavigatorContainer;
    private ViewGroup mStatusLayout;
    private TextView mStatusInfoText;
    private ImageButton mPreviousButton;
    private ImageButton mNextButton;
    private ImageButton mForwardButton;
    private ImageButton mBackwardButton;
    private String mCurrentPath;
    private ArrayList<String> mPathTree;
    private boolean mSimpleMode = false;
    private int mRootNodeIconResId = -1;
    private int mStartPosition = 0;

    private NavigationCallback mNavigationCallback;

    private OnDropDelegate mPathButtonOnDropDelegate;
    private OpenDelegate mPathButtonOpenDelegate;
    public static final int MODE_NAVIGATION = 1;

    public static final int MODE_INDICATION = 2;
    private int mMode = MODE_NAVIGATION;

    private int[] mLocationOnScreen = new int[2];
    private int[] mDragLocationOnScreen = new int[2];
    private boolean mChildCanGetDragEvent = true;
    private View mCurrentDropHandleView;
    private OnDropDelegate mOnDropDelegate;

    public FilePathNavigator(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilePathNavigator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context)
                .inflate(R.layout.file_path_navigator, this);

        mNavigatorContainer = (ViewGroup) findViewById(R.id.button_navigator);
        mStatusLayout = (ViewGroup) findViewById(R.id.pathStatusLayout);
        mStatusInfoText = (TextView) findViewById(R.id.pathStatusInfo);

        mPreviousButton = (ImageButton) findViewById(R.id.previous_button);
        mPreviousButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                shiftPreviousNode();
            }

        });
        mNextButton = (ImageButton) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                shfitNextNode();
            }

        });

        mBackwardButton = (ImageButton) findViewById(R.id.navigation_back);
        mForwardButton = (ImageButton) findViewById(R.id.navigation_previous);
    }

    @Override
    public boolean canAcceptDrop() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void onButtonClicked(FilePathButton button) {
        if (mNavigationCallback != null) {
            mNavigationCallback.navigateToPath(button.getFilePath());
        }
    }

    @Override
    public boolean onDragEnded(DragEvent event) {
        // do nothing
        return true;
    }

    @Override
    public boolean onDragEntered(DragEvent event) {
        getLocationOnScreen(mLocationOnScreen);

        return true;
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        switch (event.getAction()) {
        case DragEvent.ACTION_DRAG_STARTED:
            Log.i(TAG, "FPN Drag Started.");
            return onDragStarted(event);
        case DragEvent.ACTION_DRAG_ENTERED:
            Log.i(TAG, "FPN Drag entered.");
            return onDragEntered(event);
        case DragEvent.ACTION_DRAG_LOCATION:
            Log.i(TAG, "FPN Drag location absolute, x: " + event.getX()
                    + " y: " + event.getY());
            return onDragMoveOn(event);
        case DragEvent.ACTION_DRAG_EXITED:
            Log.i(TAG, "FPN Drag exited.");
            return onDragExited(event);
        case DragEvent.ACTION_DROP:
            Log.i(TAG, "FPN Drag droped.");
            return onDrop(event);
        case DragEvent.ACTION_DRAG_ENDED:
            Log.i(TAG, "FPN Drag ended.");
            return onDragEnded(event);
        }

        return super.onDragEvent(event);
    }

    @Override
    public boolean onDragExited(DragEvent event) {
        // do nothing
        return true;
    }

    @Override
    public boolean onDragMoveOn(DragEvent event) {
        Log.i(TAG, "onDragMoveOn");
        if (mChildCanGetDragEvent) {
            // Let navigators handle the drag event if they can.
            return true;
        } else {
            findAppropriateDragMoveHandle(event);
            return true;
        }
    }

    @Override
    public boolean onDragStarted(DragEvent event) {
        // A drag started event indicates a new drag operation happens,
        // when all the created visible navigators should be able to get
        // the following drag events
        markChildCanGetDragEvent(true);
        return true;
    }

    @Override
    public boolean onDrop(DragEvent event) {
        if (!mChildCanGetDragEvent) {
            if (findAppropriateDropHandle(event)) {
                Log.i(TAG, "mCurrentDropHandleView is handling drop.");
                return true;
            }
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
                    // Because a drop could be obscure when it happens on a GridView
                    // so we always need to notify the result if it is denied.
                    mOnDropDelegate.notifyDropDataDenied(data, this);
                    mOnDropDelegate.onDropFailed(this);
                }
            }

            return result;
        }

        return false;
    }

    @Override
    public void onHover() {
        // TODO Auto-generated method stub

    }

    @Override
    public void markChildCanGetDragEvent(boolean childCanGetDragEvent) {
        mChildCanGetDragEvent = childCanGetDragEvent;
    }

    public void setNavigationCallback(NavigationCallback callback) {
        mNavigationCallback = callback;
    }

    public void setOnDropDelegate(OnDropDelegate delegate) {
        mOnDropDelegate = delegate;
    }

    public void setPathButtonOnDropDelegate(OnDropDelegate delegate) {
        mPathButtonOnDropDelegate = delegate;
    }

    public void setPathButtonOpenDelegate(OpenDelegate delegate) {
        mPathButtonOpenDelegate = delegate;
    }

    public void setRootNodeIconResId(int resId) {
        mRootNodeIconResId = resId;
    }

    public void setStatusInfo(String statusInfo) {
        mStatusInfoText.setText(statusInfo);
    }

    public void shfitNextNode() {
        if (mPathTree != null) {
            int size = mPathTree.size();

            if (mStartPosition < size - 1) {
                mStartPosition++;
                updatePathNodes(mCurrentPath, false);
            }
        }
    }

    public void shiftPreviousNode() {
        if (mStartPosition > 0) {
            mStartPosition--;
            updatePathNodes(mCurrentPath, false);
        }
    }

    public void showInSimpleMode(boolean simpeMode) {
        mSimpleMode = simpeMode;

        if (mSimpleMode) {
            mBackwardButton.setVisibility(View.GONE);
            mForwardButton.setVisibility(View.GONE);
        }
    }

    public void switchToIndicateMode() {
        mMode = MODE_INDICATION;

        mNavigatorContainer.setVisibility(View.GONE);
        mStatusLayout.setVisibility(View.VISIBLE);

        updateButtonVisibility();
    }

    public void switchToNavigateMode() {
        mMode = MODE_NAVIGATION;

        mNavigatorContainer.setVisibility(View.VISIBLE);
        mStatusLayout.setVisibility(View.GONE);

        updateButtonVisibility();
    }

    public void updatePathNodes(String path) {
        updatePathNodes(path, true);
    }

    private boolean findAppropriateDragMoveHandle(DragEvent event) {
        Log.i(TAG, "Loc of parent: x:" + mLocationOnScreen[0] + " y:"
                + mLocationOnScreen[1]);
        mDragLocationOnScreen[0] = (int) (mLocationOnScreen[0] + event.getX());
        mDragLocationOnScreen[1] = (int) (mLocationOnScreen[1] + event.getY());
        Log.i(TAG, "FPN Drag location relative, x: " + mDragLocationOnScreen[0]
                + " y: " + mDragLocationOnScreen[1]);

        boolean moveOnHandleView;
        if (mCurrentDropHandleView != null) {
            Log.i(TAG, "There is drop handle view, test it!");
            // Test if still move on the drop handle view
            moveOnHandleView = ViewUtil.isViewContained(mCurrentDropHandleView,
                    mDragLocationOnScreen[0], mDragLocationOnScreen[1]);

            if (!moveOnHandleView) {
                // Exit
                ((DropAcceptable) mCurrentDropHandleView).onDragExited(event);
                mCurrentDropHandleView = null;
                return false;
            } else {
                return true;
            }
        } else {
            // Test if there is a new view become drop handle
            boolean foundNewDropHandle = false;
            for (int i = 0; i < mNavigatorContainer.getChildCount(); i++) {
                View view = mNavigatorContainer.getChildAt(i);

                moveOnHandleView = ViewUtil.isViewContained(view,
                        mDragLocationOnScreen[0], mDragLocationOnScreen[1]);
                if (moveOnHandleView) {
                    Log.i(TAG,
                            "Drag enter child view: " + i + " class: "
                                    + view.getClass().getSimpleName() + " l: "
                                    + view.getLeft() + " width: "
                                    + view.getWidth() + " t: " + view.getTop()
                                    + " height: " + view.getHeight() + " r: "
                                    + view.getRight() + " b: "
                                    + view.getBottom());
                    if (view instanceof DropAcceptable) {
                        mCurrentDropHandleView = view;
                        foundNewDropHandle = true;
                        ((DropAcceptable) mCurrentDropHandleView)
                                .onDragEntered(event);
                    }

                    if (foundNewDropHandle) {
                        Log.i(TAG, "Find new drop handle view!");
                        return true;
                    } else {
                        Log.i(TAG, "Not find new drop handle view yet!");
                    }
                }
            }

            return false;
        }
    }

    private boolean findAppropriateDropHandle(DragEvent event) {
        Log.i(TAG, "Loc of parent: x:" + mLocationOnScreen[0] + " y:"
                + mLocationOnScreen[1]);
        mDragLocationOnScreen[0] = (int) (mLocationOnScreen[0] + event.getX());
        mDragLocationOnScreen[1] = (int) (mLocationOnScreen[1] + event.getY());
        Log.i(TAG, "FPN Drag location relative, x: " + mDragLocationOnScreen[0]
                + " y: " + mDragLocationOnScreen[1]);

        if (mCurrentDropHandleView != null) {
            ((DropAcceptable) mCurrentDropHandleView).onDrop(event);
            mCurrentDropHandleView = null;
            return true;
        } else {
            return false;
        }
    }

    private void updateButtonVisibility() {
        switch (mMode) {
        case MODE_NAVIGATION:
            if (mPathTree != null) {
                int size = mPathTree.size();
                Log.i(TAG, "updateButtonVisibility - path tree size: " + size
                        + " start position: " + mStartPosition);
                mPreviousButton.setVisibility(mStartPosition > 0 ? View.VISIBLE
                        : View.INVISIBLE);
                mNextButton
                        .setVisibility((mStartPosition + MAX_BUTTON_NUM < size) ? View.VISIBLE
                                : View.INVISIBLE);
            }
            break;
        case MODE_INDICATION:
            mPreviousButton.setVisibility(View.GONE);
            mNextButton.setVisibility(View.GONE);
            break;
        }
    }

    private void updatePathNodes(String path, boolean newPath) {
        Log.i(TAG, "updatePathNodes - " + " path:" + path + " newPath:"
                + newPath);
        if (FilePathUrlManager.isExistingDirPath(path)) {
            mCurrentPath = path;
            mPathTree = FileUtil.getFilePathTree(path);
        } else {
            if (FilePathUrlManager.isSearchUrl(path)) {
                String dirPath = FilePathUrlManager.getDir(path);
                mCurrentPath = dirPath;
                mPathTree = FileUtil.getFilePathTree(dirPath);
                mPathTree.add(path);
            }
        }

        if (mPathTree != null) {
            // Remove old buttons
            mNavigatorContainer.removeAllViews();
            // New inflated navigators can't get drag events from last drag operation,
            // because they haven't been created when THE drag started. 
            markChildCanGetDragEvent(false);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.setMargins(2, 8, 4, 8);

            int size = mPathTree.size();
            int startIndex;
            if (newPath) {
                startIndex = (size > MAX_BUTTON_NUM) ? size - MAX_BUTTON_NUM
                        : 0;
                mStartPosition = startIndex;
            } else {
                startIndex = mStartPosition;
            }

            // Update new buttons
            for (int index = startIndex; (index < startIndex + MAX_BUTTON_NUM && index < size); index++) {
                String nodePath = mPathTree.get(index);
                DropableFilePathButton filePathButton = new DropableFilePathButton(
                        getContext(), nodePath, this);
                if (mPathButtonOnDropDelegate != null) {
                    filePathButton.setOnDropDelegate(mPathButtonOnDropDelegate);
                }

                if (mPathButtonOpenDelegate != null) {
                    filePathButton.setOpenDelegate(mPathButtonOpenDelegate);
                }

                String label = FileUtil.getFileName(nodePath);
                if (label != null && label.length() > 20) {
                    label = label.substring(0, 20) + "...";
                }

                if (label != null) {
                    if (label.equals("/")) {
                        filePathButton.setLabel("");
                        if (mRootNodeIconResId > 0) {
                            filePathButton.setIcon(mRootNodeIconResId);
                        } else {
                            filePathButton.setIcon(R.drawable.home_dir);
                        }
                    } else {
                        if (label.equals(FilePathUrlManager.URL_PATH_APK)) {
                            filePathButton.setLabel("");
                            filePathButton.setIcon(R.drawable.category_apk);
                        } else if (label
                                .equals(FilePathUrlManager.URL_PATH_MOVIES)) {
                            filePathButton.setLabel("");
                            filePathButton.setIcon(R.drawable.category_movie);
                        } else if (label
                                .equals(FilePathUrlManager.URL_PATH_MUSIC)) {
                            filePathButton.setLabel("");
                            filePathButton.setIcon(R.drawable.category_music);
                        } else if (label
                                .equals(FilePathUrlManager.URL_PATH_IMAGE)) {
                            filePathButton.setLabel("");
                            filePathButton.setIcon(R.drawable.category_picture);
                        } else {
                            filePathButton.setLabel(label);
                        }
                    }
                }

                mNavigatorContainer.addView(filePathButton, params);
            }

            updateButtonVisibility();

            if (FilePathUrlManager.isExistingDirPath(path)) {
                mStatusInfoText.setText(mPathTree.get(size - 1));
            }
        }
    }
}

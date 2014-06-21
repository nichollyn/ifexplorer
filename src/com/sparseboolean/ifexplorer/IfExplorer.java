/*
    IfExplorer, an open source file manager for the Android system.
    Copyright (C) 2014  Kevin Lin
    <chenbin.lin@tpv-tech.com>

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sparseboolean.ifexplorer;

import gem.android.widget.SlidingDrawerEx;
import gem.com.slidinglayer.SlidingLayer;
import gem.kevin.innov.FilePathUrlManager;
import gem.kevin.provider.ClipSourceProvider;
import gem.kevin.task.FileOperationTask;
import gem.kevin.task.FileOperationTask.FileOperationOptions;
import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.util.FileUtil.SimpleCopyCallback;
import gem.kevin.util.FileUtil.SimpleCreateCallback;
import gem.kevin.util.FileUtil.SimpleDeleteCallback;
import gem.kevin.util.FileUtil.SimpleRenameCallback;
import gem.kevin.util.StorageUtil;
import gem.kevin.widget.ClipItemAdapter;
import gem.kevin.widget.ClipItemAdapter.DeleteDelegate;
import gem.kevin.widget.ClipItemAdapter.PasteDelegate;
import gem.kevin.widget.DropableGridView;
import gem.kevin.widget.DropableListView;
import gem.kevin.widget.FileClip;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.OnScanCompletedListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.format.Time;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.AbsListView;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;
import android.widget.TextView;
import android.widget.Toast;

import com.sparseboolean.ifexplorer.IfAppController.FileDataGridAdapter;
import com.sparseboolean.ifexplorer.IfAppController.FileDataListAdapter;
import com.sparseboolean.ifexplorer.ui.ClipHistoryAdapter;
import com.sparseboolean.ifexplorer.ui.ClipHistoryAdapter.ClipHistoryCallback;
import com.sparseboolean.ifexplorer.ui.DeviceDataAdapter;
import com.sparseboolean.ifexplorer.ui.FavoriteDataAdapter;
import com.sparseboolean.ifexplorer.ui.FilePathNavigator;
import com.sparseboolean.ifexplorer.ui.TaskGroupListWidget;

public class IfExplorer extends Activity implements OnTouchListener,
        OnItemClickListener, ClipHistoryCallback, PasteDelegate,
        DeleteDelegate, IfAppController.DeviceDataListener,
        IfAppController.UiCallback, FilePathNavigator.NavigationCallback,
        OnQueryTextListener, SimpleCreateCallback, SimpleDeleteCallback,
        SimpleRenameCallback, SimpleCopyCallback {

    public class FileMultiChoiceListener implements MultiChoiceModeListener {
        private CheckBox selectAllCheckBox;

        public HashSet<Object> getSelections() {
            HashSet<Object> result = new HashSet<Object>();
            SparseBooleanArray choices = mCurrentShowList
                    .getCheckedItemPositions();
            ListAdapter adapter = mCurrentShowList.getAdapter();
            int total = adapter.getCount();
            for (int i = 0; i < total; i++) {
                if (choices.get(i)) {
                    result.add(adapter.getItem(i));
                }
            }

            return result;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.menu_copyFile:
                MultiChoiceConsumeOptions copyOptions = new MultiChoiceConsumeOptions(
                        ClipHistoryAdapter.CLIP_COPY);
                mClipHistoryAdapter.consumeChoice(
                        mFileListMultiChoiceListener.getSelections(),
                        copyOptions);
                mode.finish();
                mOptionPasteAvailable = true;
                IfExplorer.this.invalidateOptionsMenu();
                return true;
            case R.id.menu_cutFile:
                MultiChoiceConsumeOptions cutOptions = new MultiChoiceConsumeOptions(
                        ClipHistoryAdapter.CLIP_CUT);
                mClipHistoryAdapter.consumeChoice(
                        mFileListMultiChoiceListener.getSelections(),
                        cutOptions);
                mode.finish();
                mOptionPasteAvailable = true;
                IfExplorer.this.invalidateOptionsMenu();
                return true;
            case R.id.menu_deleteFile:
                MultiChoiceConsumeOptions deleteOptions = new MultiChoiceConsumeOptions(
                        ClipHistoryAdapter.CLIP_DELETE);
                mClipHistoryAdapter.consumeChoice(
                        mFileListMultiChoiceListener.getSelections(),
                        deleteOptions);
                mode.finish();
                return true;
            case R.id.menu_renameFile:
                MultiChoiceConsumeOptions renameOptions = new MultiChoiceConsumeOptions(
                        ClipHistoryAdapter.CLIP_RENAME_SINGLE);
                mClipHistoryAdapter.consumeChoice(
                        mFileListMultiChoiceListener.getSelections(),
                        renameOptions);
                mode.finish();
                return true;
            case R.id.menu_detail:
                IfExplorer.this.showDetailDialog(mFileListMultiChoiceListener
                        .getSelections());
                mode.finish();
                return true;
            default:
                return false;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater menuInflater = getMenuInflater();
            menuInflater.inflate(R.menu.file_list_select_menu, menu);

            View customView = LayoutInflater.from(IfExplorer.this).inflate(
                    R.layout.select_mode_custom_views, null);
            selectAllCheckBox = (CheckBox) customView
                    .findViewById(R.id.select_all_checkbox);
            selectAllCheckBox
                    .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView,
                                boolean isChecked) {
                            for (int i = 0; i < mCurrentShowList.getAdapter()
                                    .getCount(); i++) {
                                mCurrentShowList.setItemChecked(i, isChecked);
                            }
                        }
                    });
            mode.setCustomView(customView);

            setSubtitle(mode);
            updateUiForActionMode(true, true);
            IfExplorer.this.setActivatingActionMode(mode);

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            updateUiForActionMode(false, true);
            IfExplorer.this.setActivatingActionMode(null);
        }

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position,
                long id, boolean checked) {
            setSubtitle(mode);

            View targetItemView = mCurrentShowList.getChildAt(position);
            if (targetItemView != null) {
                CheckBox checkbox = (CheckBox) targetItemView
                        .findViewById(R.id.file_selected);
                if (checkbox != null) {
                    checkbox.setChecked(checked);
                }
            }

            mOptionRenameAvailable = (mCurrentShowList.getCheckedItemCount() == 1);
            HashSet<Object> selections = getSelections();
            for (Object selection : selections) {
                if (selection instanceof FileItem) {
                    if (((FileItem) selection).isSystemFile()) {
                        mSelectionContainSysFiles = true;
                        break;
                    } else {
                        mSelectionContainSysFiles = false;
                    }
                }
            }
            mode.invalidate();

            // Kevin Lin
            // It might be a bug that the associated list won't get correct
            // display for CheckBox of the checked item
            // if the items are not in the first page(In vision without scroll
            // the list)
            // So we force to refresh the list
            mAppController.forceRefreshFileListView();
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem renameItem = menu.findItem(R.id.menu_renameFile);
            MenuItem deleteItem = menu.findItem(R.id.menu_deleteFile);
            MenuItem copyItem = menu.findItem(R.id.menu_copyFile);
            MenuItem cutItem = menu.findItem(R.id.menu_cutFile);

            // visibility
            // renameItem.setVisible(sCurrentDir != null ? true : false);
            // deleteItem.setVisible(sCurrentDir != null ? true : false);
            renameItem.setVisible(true);
            deleteItem.setVisible(true);

            // enable/disable
            renameItem
                    .setEnabled((mOptionRenameAvailable && !mSelectionContainSysFiles) ? true
                            : false);
            deleteItem.setEnabled(!mSelectionContainSysFiles ? true : false);
            copyItem.setEnabled(!mSelectionContainSysFiles ? true : false);
            cutItem.setEnabled(!mSelectionContainSysFiles ? true : false);

            setSubtitle(mode);

            return true;
        }

        private void setSubtitle(ActionMode actionMode) {
            int totalNum = mCurrentShowList.getCount();
            int checkedNum = mCurrentShowList.getCheckedItemCount();

            // Could possibly restore from a savedStateInstance,
            // with a wrong count of current show list
            if (totalNum == 0 && mSavedStateInstatnce != null) {
                totalNum = mSavedStateInstatnce.getInt(
                        KEY_SAVE_FILE_LIST_TOTAL_NUM, 0);
                checkedNum = mSavedStateInstatnce.getInt(
                        KEY_SAVE_FILE_LIST_SELECTED_NUM, 0);
                if (totalNum == checkedNum && totalNum != 0) {
                    selectAllCheckBox.setChecked(true);
                }
            }

            String title = String.format("%d/%d", checkedNum, totalNum);

            // actionMode.setTitle(title);
            TextView textView = (TextView) actionMode.getCustomView()
                    .findViewById(R.id.selected_count);
            if (textView != null) {
                textView.setText(title);
            }
        }

        private void updateUiForActionMode(boolean inActionMode,
                boolean clearChoice) {
            Log.i(TAG, "updateUiForActionMode " + inActionMode);
            // sliding layer
            if (mSlidingLayer != null) {
                mSlidingLayer.setVisibility(inActionMode ? View.GONE
                        : View.VISIBLE);
            }

            mCurrentShowList.setVisibility(View.VISIBLE);
            mNavBackwardButton.setVisibility(inActionMode ? View.INVISIBLE
                    : View.VISIBLE);
            mNavForwardButton.setVisibility(inActionMode ? View.INVISIBLE
                    : View.VISIBLE);
            mCollectionSwitchButton.setVisibility(inActionMode ? View.INVISIBLE
                    : (mSlidingLayer != null ? View.VISIBLE : View.INVISIBLE));
            if (inActionMode) {
                mPathNavigator.switchToIndicateMode();
            } else {
                mPathNavigator.switchToNavigateMode();
            }

            // file list
            if (mAppController.hasActionModeBunchedViews() && !inActionMode) {
                Log.i("FileMultiChoiceListener",
                        "split bunched up views if we are not in action mode.");
                mAppController.splitActionModeBunchedViews();
            }
        }

    }

    public static class MultiChoiceConsumeOptions {
        public String consumeDescription = null;

        public MultiChoiceConsumeOptions(String consumeDescription) {
            this.consumeDescription = consumeDescription;
        }
    }

    public interface MultiChoiceConsumer {
        public boolean consumeChoice(HashSet<Object> choices,
                MultiChoiceConsumeOptions options);
    }

    private static final int VIEW_AS_LIST = 1;
    private static final int VIEW_AS_GRID = 2;

    private static final int DEVICE_UNKNOWN = 0;
    private static final int DEVICE_TAB_LAND = 1;
    private static final int DEVICE_TAB_PORT = 2;
    private static final int DEVICE_PHONE_LAND = 3;
    private static final int DEVICE_PHONE_PORT = 4;

    private static final String KEY_VIEW_MODE = "view_mode";
    private static final String KEY_SAVE_LOCATION = "location";
    private static final String KEY_SAVE_BROWSE_HISTORY = "browse_history";

    private static final String KEY_SAVE_HISTORY_POSITION = "history_position";

    private static final String KEY_SAVE_SLIDING_LAYER_OPEN = "sliding_open";
    private static final String KEY_SAVE_SEARCH_RESULTS = "search_results";
    private static final String KEY_SAVE_CACHED_AUDIO_FILEPATHS = "audio_file_paths";
    private static final String KEY_SAVE_CACHED_VIDEO_FILEPATHS = "video_file_paths";
    private static final String KEY_SAVE_CACHED_APK_FILEPATHS = "apk_file_paths";
    private static final String KEY_SAVE_FILE_LIST_TOTAL_NUM = "file_list_total_num";
    private static final String KEY_SAVE_FILE_LIST_SELECTED_NUM = "file_list_selected_num";

    private static final String TAG = "IfExplorer";
    private static final boolean KLOG = true;
    private static final boolean tempLOG = false;

    private Bundle mSavedStateInstatnce;

    public static String sCurrentDir = null;

    public static void setCurrentDir(String currentPath) {
        if (FilePathUrlManager.isExistingDirPath(currentPath)) {
            sCurrentDir = currentPath;
        } else {
            sCurrentDir = null;
        }
    }

    private FilePathUrlManager mFileManager;

    private String mInitLocation = null;

    private ArrayList<String> mSavedBrowseHistory = null;

    private int mSavedHistoryPosition = -1;
    private IfAppController mAppController;
    private FileDataListAdapter mFileDataListAdapter;
    private FileDataGridAdapter mFileDataGridAdapter;

    private AbsListView mCurrentShowList;
    private int mCurrentViewMode = VIEW_AS_GRID;
    private DropableListView mFileListView;
    private DropableGridView mFileGridView;
    private int mDeviceDisplayType;
    private View mAddressBar;
    private FilePathNavigator mPathNavigator;

    private SearchView mSearchView;
    private ImageView mCollectionSwitchButton;
    private ImageView mNavBackwardButton;
    private ImageView mNavForwardButton;
    private SlidingLayer mSlidingLayer;

    private SlidingDrawerEx mClipBoardDrawer;
    private View mClipBoardIndicator;
    private Button mButtonClearClipboard;

    private Button mButtonCloseClipboard;
    private ListView mClipHistoryListView;
    private ClipHistoryAdapter mClipHistoryAdapter;
    private boolean mOptionPasteAvailable = false;
    private boolean mOptionRenameAvailable = false;

    private boolean mSelectionContainSysFiles = false;
    /* Storage monitoring */
    private IntentFilter mStorageIntentFilter;
    private BroadcastReceiver mStorageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String path = intent.getData().getPath();
            Log.i(TAG, "ACTION: " + action + " happened on path: " + path);

            mAppController.resetCachedFilePaths();

            if (mAppController != null) {
                if (mFileManager.getCurrentUrl().contains(path)) {
                    if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                            || action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)
                            || action.equals(Intent.ACTION_MEDIA_EJECT)
                            || action.equals(Intent.ACTION_MEDIA_REMOVED)) {
                        // Update to a null directory at once.
                        // It's a tricky to make IF release possible possessive
                        // file descriptors
                        // so it won't get killed by ActivityManager
                        mAppController.updateDirectory(null);

                        mAppController.updateDirectory(mFileManager
                                .getNextUrlContent(StorageUtil.getHomeDir(),
                                        true, false));
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mAppController.updateDirectory(mFileManager
                                .refreshUrlContent());
                    }
                } else {
                    int searchType = -1;
                    String filePattern = FilePathUrlManager
                            .getSmartPattern(mFileManager.getCurrentUrl());
                    if (filePattern != null) {
                        if (filePattern
                                .endsWith(FilePathUrlManager.SMART_MUSIC)) {
                            searchType = StorageUtil.TYPE_AUDIO;
                        } else if (filePattern
                                .endsWith(FilePathUrlManager.SMART_MOVIES)) {
                            searchType = StorageUtil.TYPE_VIDEO;
                        } else if (filePattern
                                .endsWith(FilePathUrlManager.SMART_IMAGE)) {
                            searchType = StorageUtil.TYPE_IMAGE;
                        } else if (filePattern
                                .endsWith(FilePathUrlManager.SMART_APK)) {
                            searchType = StorageUtil.TYPE_APKFILE;
                        }
                        mAppController.searchFiles(searchType, FileUtil.FILE_PATTERN_ALL,
                                "/", IfConfig.MAX_SEARCH_DEPTH, false);
                    } else {
                        mAppController.updateDirectory(mFileManager
                                .refreshUrlContent());
                    }
                }

                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_MOUNTED, true);
                } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_UNMOUNTED, true);
                    // URL starts with this path now become invalid
                    mFileManager.removeInvalidUrls(path);
                } else if (action.equals(Intent.ACTION_MEDIA_REMOVED)
                        || action.equals(Intent.ACTION_MEDIA_EJECT)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_REMOVED, true);
                    mFileManager.removeInvalidUrls(path);
                } else if (action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_BAD_REMOVAL, true);
                    mFileManager.removeInvalidUrls(path);
                } else {
                    return;
                }

                refreshTaskGroups();
                preScanCategoriedFiles();
            }
        }
    };
    // Task groups
    private HashSet<TaskGroupListWidget> mMiniPanelWidgets = new HashSet<TaskGroupListWidget>();
    // Favorites
    private TaskGroupListWidget mFavoritesMiniWidget;

    // Devices
    private TaskGroupListWidget mInternalStorageMiniWidget;

    private TaskGroupListWidget mSdcardMiniWidget;
    private TaskGroupListWidget mUsb0StorageMiniWidget;
    private TaskGroupListWidget mUsb1StorageMiniWidget;
    private TaskGroupListWidget mUsb2StorageMiniWidget;
    private TaskGroupListWidget mUsb3StorageMiniWidget;
    private TaskGroupListWidget mUsb4StorageMiniWidget;
    private TaskGroupListWidget mUsb5StorageMiniWidget;
    private TaskGroupListWidget mStorageA_MiniWidget;
    private TaskGroupListWidget mStorageB_MiniWidget;
    private TaskGroupListWidget mStorageC_MiniWidget;
    private TaskGroupListWidget mStorageD_MiniWidget;
    private static final int kTagTaskGroupBase = 0;

    private FileOperationTaskAutoExecutor mFileOpAutoExecutor = new FileOperationTaskAutoExecutor();
    private FileMultiChoiceListener mFileListMultiChoiceListener;
    private ActionMode mActivatingActionMode;

    @Override
    public void cancelDelete(ClipItemAdapter assignor, boolean rollback) {
        // TODO: implement roll back function
        HashSet<FileOperationTask> combinedTasks = assignor
                .getOneShotClipTask();

        if (combinedTasks != null) {
            for (FileOperationTask task : combinedTasks) {
                mFileOpAutoExecutor.dequeueFileOpTask(task);
            }
        }
    }

    @Override
    public void cancelPaste(ClipItemAdapter assignor, boolean rollback) {
        // TODO: implement roll back function
        HashSet<FileOperationTask> combinedTasks = assignor
                .getOneShotClipTask();

        if (combinedTasks != null) {
            for (FileOperationTask task : combinedTasks) {
                mFileOpAutoExecutor.dequeueFileOpTask(task);
            }
        }
    }

    @Override
    public void doDelete(HashSet<FileClip> clipToDelete,
            ClipItemAdapter assignor) {
        if (clipToDelete == null) {
            return;
        }

        if (clipToDelete.size() != 0) {
            Time time = new Time();
            time.setToNow();
            String taskId = String.format("%02d:%02d:%02d-delete", time.hour,
                    time.minute, time.second);

            FileOperationOptions options = FileOperationTask
                    .makeDefaultOptions(FileOperationTask.OP_TYPE_DELETE_FILE);

            HashSet<FileOperationTask> combinedTasks = new HashSet<FileOperationTask>();
            FileOperationTask deleteTask = null;
            ArrayList<String> toDelete = new ArrayList<String>();
            for (FileClip clip : clipToDelete) {
                float clipType = clip.getType();
                if (ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE == clipType) {
                    toDelete.add(clip.getFilePath());
                }
            }

            deleteTask = new FileOperationTask(this, taskId, options, toDelete,
                    assignor);
            combinedTasks.add(deleteTask);
            assignor.setOneShotClipTask(combinedTasks);
            mFileOpAutoExecutor.enqueueFileOpTask(deleteTask);
        }
    }

    @Override
    public void doPaste(HashSet<FileClip> clipToPaste, String pasteDir,
            ClipItemAdapter assignor) {
        mOptionPasteAvailable = false;
        invalidateOptionsMenu();

        if (clipToPaste == null) {
            return;
        }

        if (clipToPaste.size() != 0) {
            boolean doCopy = false;
            boolean doCut = false;
            ArrayList<String> toCopy = new ArrayList<String>();
            ArrayList<String> toCut = new ArrayList<String>();
            for (FileClip clip : clipToPaste) {
                float clipType = clip.getType();
                if (ClipSourceProvider.CLIP_TYPE_COPY_SOURCE == clipType) {
                    doCopy = true;
                    toCopy.add(clip.getFilePath());
                } else if (ClipSourceProvider.CLIP_TYPE_CUT_SOURCE == clipType) {
                    doCut = true;
                    toCut.add(clip.getFilePath());
                }
            }

            if (!doCopy && !doCut) {
                Log.i(TAG, "No actual copy or cut tasks!");
                return;
            }

            FileOperationOptions options;
            Time time = new Time();
            time.setToNow();
            String taskId;

            // TODO: find out why a three level control (ClipItemAdapter =>
            // ClipTask => FileOperationTask
            // can't synchronize UI? (CPU over load?)
            // ClipTask clipTask = new ClipTask(this);
            HashSet<FileOperationTask> combinedTasks = new HashSet<FileOperationTask>();
            FileOperationTask copyTask = null;
            FileOperationTask renameTask = null;
            FileOperationTask moveTask = null;

            if (doCopy) {
                options = FileOperationTask
                        .makeDefaultOptions(FileOperationTask.OP_TYPE_COPY_FILE);
                options.targetDirectoryPath = pasteDir;
                taskId = String.format("%02d:%02d:%02d-copy", time.hour,
                        time.minute, time.second);

                copyTask = new FileOperationTask(this, taskId, options, toCopy,
                        assignor);
                combinedTasks.add(copyTask);
            }

            if (doCut) {
                // Filter the two types of 'Cut'
                // (1) Moving on same file system => Rename
                // (2) Moving between different file system => Copy and delete
                ArrayList<String> toMove = new ArrayList<String>();
                ArrayList<String> toRename = new ArrayList<String>();
                for (String srcFilePath : toCut) {
                    try {
                        if (onSameFileSystem(srcFilePath, pasteDir)) {
                            toRename.add(srcFilePath);
                        } else {
                            toMove.add(srcFilePath);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                if (toRename.size() > 0) {
                    options = FileOperationTask
                            .makeDefaultOptions(FileOperationTask.OP_TYPE_RENAME_FILE);
                    options.targetDirectoryPath = pasteDir;
                    taskId = String.format("%d:%d:%d-rename", time.hour,
                            time.minute, time.second);

                    renameTask = new FileOperationTask(this, taskId, options,
                            toRename, assignor);
                    combinedTasks.add(renameTask);
                }

                if (toMove.size() > 0) {
                    options = FileOperationTask
                            .makeDefaultOptions(FileOperationTask.OP_TYPE_MOVE_FILE);
                    options.targetDirectoryPath = pasteDir;
                    taskId = String.format("%d:%d:%d-move", time.hour,
                            time.minute, time.second);

                    moveTask = new FileOperationTask(this, taskId, options,
                            toMove, assignor);
                    combinedTasks.add(moveTask);
                }
            }

            assignor.setOneShotClipTask(combinedTasks);
            // enqueue file operation tasks after combined clip task initialized
            if (copyTask != null) {
                mFileOpAutoExecutor.enqueueFileOpTask(copyTask);
            } else if (renameTask != null) {
                mFileOpAutoExecutor.enqueueFileOpTask(renameTask);
            } else if (moveTask != null) {
                mFileOpAutoExecutor.enqueueFileOpTask(moveTask);
            }
        }
    }

    public ActionMode getActivatingActionMode() {
        return mActivatingActionMode;
    }

    public ClipHistoryAdapter getClipHistoryAdapter() {
        return mClipHistoryAdapter;
    }

    @Override
    public String getClipPasteDir() {
        return sCurrentDir;
    }

    public FileMultiChoiceListener getFileMultiChoiceListener() {
        return mFileListMultiChoiceListener;
    }

    public void handleFileItemClick(ArrayAdapter<FileItem> fileDataAdapter,
            View view, int position, long id) {
        Log.i(TAG, "handleFileItemClick");
        final String path = fileDataAdapter.getItem(position).getPath();

        File file = new File(path);
        String item_ext = null;
        try {
            item_ext = path.substring(path.lastIndexOf("."), path.length());
        } catch (IndexOutOfBoundsException e) {
            item_ext = "";
        }

        if (!item_ext.equals("")) {
            item_ext = DataUtil.getFileExtensionWithoutDot(item_ext);
        }

        if (file.isDirectory()) {
            if (file.canRead()) {
                mAppController.stopThumbnailThread();
                mAppController.updateDirectory(mFileManager.getNextUrlContent(
                        path, true, false));
                // When entering a folder, always focus on
                // the first item
                mCurrentShowList.setSelection(0);
            } else {
                Toast.makeText(this, R.string.read_fail_permission,
                        Toast.LENGTH_SHORT).show();
            }
        }
        /* special handle for vender MIME type */
        else if (item_ext.equalsIgnoreCase("apk")) {
            if (file.exists()) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file),
                        "application/vnd.android.package-archive");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.cannot_find_application,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        /* audio file */
        else if (DataUtil.getSupportedAudioFileExtensions().contains(
                item_ext.toLowerCase())) {
            if (file.exists()) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), "audio/*");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.cannot_find_application,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        /* image file */
        else if (DataUtil.getSupportedImageFileExtensions().contains(
                item_ext.toLowerCase())) {
            if (file.exists()) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), "image/*");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.cannot_find_application,
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
        /* video file */
        else if (DataUtil.getSupportedVideoFileExtensions().contains(
                item_ext.toLowerCase())) {
            if (file.exists()) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), "video/*");
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.cannot_find_application,
                            Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            String extensionWithoutDot = DataUtil
                    .getFileExtensionWithoutDot(file.getPath());
            String mimeType = DataUtil.getMimeType(extensionWithoutDot);
            if (mimeType != null) {
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(file), mimeType);
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.cannot_find_application,
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, R.string.cannot_find_application,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void navigateToPath(String path) {
        Log.i(TAG, "navigateToPath - path: " + path);
        mAppController.updateDirectory(mFileManager.getNextUrlContent(path,
                true, false));
    }

    @Override
    public void onClipHistoryChanged(ClipHistoryAdapter adapter) {
        if (adapter == null) {
            return;
        }

        if (adapter.getCount() == 0) {
            mClipBoardIndicator.setVisibility(View.GONE);
            if (mClipBoardDrawer.isOpened()) {
                mClipBoardDrawer.close();
            }

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        } else {
            mClipBoardIndicator.setVisibility(View.VISIBLE);

            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);
        }

        invalidateOptionsMenu();
        mAppController.updateDirectory(mFileManager.refreshUrlContent());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "onConfigurationChanged" + newConfig.keyboard);

        if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO) {
            Log.i(TAG, "hard keyboard presented.");
        } else if (newConfig.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_YES) {
            Log.i(TAG, "hard keyboard quits.");
        }

        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.clear();
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.file_explorer_options_menu, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onFileCopied(String srcFilePath, String copiedFilePath) {
        callMediaScanOnCreatedFile(copiedFilePath);

        String pattern = null;
        String extension = DataUtil.getFileExtensionWithoutDot(copiedFilePath);

        if (DataUtil.isSupportedAppInstaller(extension)) {
            pattern = FilePathUrlManager.SMART_APK;
        } else if (DataUtil.isSupportedAudioFile(extension)) {
            pattern = FilePathUrlManager.SMART_MUSIC;
        } else if (DataUtil.isSupportedImageFile(extension)) {
            pattern = FilePathUrlManager.SMART_IMAGE;
        } else if (DataUtil.isSupportedVideoFile(extension)) {
            pattern = FilePathUrlManager.SMART_MOVIES;
        }

        if (pattern != null) {
            HashMap<String, String> changeSet = new HashMap<String, String>();
            changeSet.put(FileUtil.NEW_GENERATED_FILE, copiedFilePath);

            updateCachedFilePaths(pattern, changeSet);
        }
    }

    @Override
    public void onFileCreated(String filePath) {
        callMediaScanOnCreatedFile(filePath);

        String pattern = null;
        String extension = DataUtil.getFileExtensionWithoutDot(filePath);

        if (DataUtil.isSupportedAppInstaller(extension)) {
            pattern = FilePathUrlManager.SMART_APK;
        } else if (DataUtil.isSupportedAudioFile(extension)) {
            pattern = FilePathUrlManager.SMART_MUSIC;
        } else if (DataUtil.isSupportedImageFile(extension)) {
            pattern = FilePathUrlManager.SMART_IMAGE;
        } else if (DataUtil.isSupportedVideoFile(extension)) {
            pattern = FilePathUrlManager.SMART_MOVIES;
        }

        if (pattern != null) {
            HashMap<String, String> changeSet = new HashMap<String, String>();
            changeSet.put(FileUtil.NEW_GENERATED_FILE, filePath);

            updateCachedFilePaths(pattern, changeSet);
        }
    }

    @Override
    public void onFileDeleted(String filePath) {
        @SuppressWarnings("unchecked")
        HashMap<String, ArrayList<String>> cachedSearchResult = (HashMap<String, ArrayList<String>>) mAppController
                .getCachedSearchResult().clone();
        if (cachedSearchResult != null) {
            for (String searchId : cachedSearchResult.keySet()) {
                ArrayList<String> searchResult = cachedSearchResult
                        .get(searchId);
                if (searchResult != null && searchResult.contains(filePath)) {
                    handleFileSearch(
                            FilePathUrlManager.parseSearchStr(searchId),
                            FilePathUrlManager.parseSearchDir(searchId), true);
                }
            }
        }

        String pattern = null;
        String extension = DataUtil.getFileExtensionWithoutDot(filePath);
        if (DataUtil.isSupportedAppInstaller(extension)) {
            pattern = FilePathUrlManager.SMART_APK;
        } else if (DataUtil.isSupportedAudioFile(extension)) {
            pattern = FilePathUrlManager.SMART_MUSIC;
        } else if (DataUtil.isSupportedImageFile(extension)) {
            pattern = FilePathUrlManager.SMART_IMAGE;
        } else if (DataUtil.isSupportedVideoFile(extension)) {
            pattern = FilePathUrlManager.SMART_MOVIES;
        }

        updateMediaStoreOnDeletedFile(filePath, pattern);

        if (pattern != null) {
            HashMap<String, String> changeSet = new HashMap<String, String>();
            changeSet.put(filePath, null);

            updateCachedFilePaths(pattern, changeSet);
        }
    }

    @Override
    public void onFileRenamed(String originalFilePath, String newFilePath) {
        callMediaScanOnCreatedFile(newFilePath);

        @SuppressWarnings("unchecked")
        HashMap<String, ArrayList<String>> cachedSearchResult = (HashMap<String, ArrayList<String>>) mAppController
                .getCachedSearchResult().clone();
        if (cachedSearchResult != null) {
            for (String searchId : cachedSearchResult.keySet()) {
                ArrayList<String> searchResult = cachedSearchResult
                        .get(searchId);
                if (searchResult != null
                        && searchResult.contains(originalFilePath)) {
                    handleFileSearch(
                            FilePathUrlManager.parseSearchStr(searchId),
                            FilePathUrlManager.parseSearchDir(searchId), true);
                }
            }
        }

        String patternOriginal = null;
        String extensionOriginal = DataUtil
                .getFileExtensionWithoutDot(originalFilePath);
        if (DataUtil.isSupportedAppInstaller(extensionOriginal)) {
            patternOriginal = FilePathUrlManager.SMART_APK;
        } else if (DataUtil.isSupportedAudioFile(extensionOriginal)) {
            patternOriginal = FilePathUrlManager.SMART_MUSIC;
        } else if (DataUtil.isSupportedImageFile(extensionOriginal)) {
            patternOriginal = FilePathUrlManager.SMART_IMAGE;
        } else if (DataUtil.isSupportedVideoFile(extensionOriginal)) {
            patternOriginal = FilePathUrlManager.SMART_MOVIES;
        }

        updateMediaStoreOnDeletedFile(originalFilePath, patternOriginal);

        String patternNew = null;
        String extensionNew = DataUtil.getFileExtensionWithoutDot(newFilePath);
        if (DataUtil.isSupportedAppInstaller(extensionNew)) {
            patternNew = FilePathUrlManager.SMART_APK;
        } else if (DataUtil.isSupportedAudioFile(extensionNew)) {
            patternNew = FilePathUrlManager.SMART_MUSIC;
        } else if (DataUtil.isSupportedImageFile(extensionNew)) {
            patternNew = FilePathUrlManager.SMART_IMAGE;
        } else if (DataUtil.isSupportedVideoFile(extensionNew)) {
            patternNew = FilePathUrlManager.SMART_MOVIES;
        }

        if (patternOriginal != null && patternNew != null
                && patternOriginal.equals(patternNew)) {
            HashMap<String, String> changeSet = new HashMap<String, String>();
            changeSet.put(originalFilePath, newFilePath);
            updateCachedFilePaths(patternNew, changeSet);
        } else {
            if (patternOriginal != null) {
                HashMap<String, String> changeSet = new HashMap<String, String>();
                changeSet.put(originalFilePath, null);
                updateCachedFilePaths(patternOriginal, changeSet);
            }

            if (patternNew != null) {
                HashMap<String, String> changeSet = new HashMap<String, String>();
                changeSet.put(FileUtil.NEW_GENERATED_FILE, newFilePath);
                updateCachedFilePaths(patternNew, changeSet);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View itemView, int pos,
            long id) {
        if (adapterView.getAdapter() instanceof FavoriteDataAdapter) {
            if (mActivatingActionMode != null) {
                mActivatingActionMode.finish();
            }

            FavoriteItem favoriteItem = (FavoriteItem) adapterView
                    .getItemAtPosition(pos);
            String filePattern = favoriteItem.getPath();
            // user relay search instead
            // mHandler.searchForFilesOfPattern(filePattern);
            int searchType = -1;
            if (filePattern.endsWith(FilePathUrlManager.SMART_MUSIC)) {
                searchType = StorageUtil.TYPE_AUDIO;
            } else if (filePattern.endsWith(FilePathUrlManager.SMART_MOVIES)) {
                searchType = StorageUtil.TYPE_VIDEO;
            } else if (filePattern.endsWith(FilePathUrlManager.SMART_IMAGE)) {
                searchType = StorageUtil.TYPE_IMAGE;
            } else if (filePattern.endsWith(FilePathUrlManager.SMART_APK)) {
                searchType = StorageUtil.TYPE_APKFILE;
            }

            // TODO: make searchDir configurable from FavoriteItem by user
            String searchDir = "/";
            mAppController.searchFiles(searchType, FileUtil.FILE_PATTERN_ALL,
                    "/", IfConfig.MAX_SEARCH_DEPTH, false);

            String url = FilePathUrlManager.URL_FIELD_DIR_PATH
                    + FilePathUrlManager.URL_KEY_VALUE_DIVIDER + searchDir
                    + FilePathUrlManager.URL_FIELD_DIVIDER + filePattern
                    + FilePathUrlManager.URL_FIELD_DIVIDER;

            mAppController.updateDirectory(mFileManager.getNextUrlContent(url,
                    true, false));
            // reset current dir
            sCurrentDir = null;

            closeSlidingLayer(false);
        } else if (adapterView.getAdapter() instanceof DeviceDataAdapter) {
            if (mActivatingActionMode != null) {
                mActivatingActionMode.finish();
            }

            DeviceItem deviceItem = (DeviceItem) adapterView
                    .getItemAtPosition(pos);
            String path = deviceItem.getPath();

            mAppController.updateDirectory(mFileManager.getNextUrlContent(path,
                    true, false));

            closeSlidingLayer(false);
        } else if (adapterView.getAdapter() instanceof FileDataListAdapter) {
            handleFileItemClick(mFileDataListAdapter, itemView, pos, id);
        } else if (adapterView.getAdapter() instanceof FileDataGridAdapter) {
            handleFileItemClick(mFileDataGridAdapter, itemView, pos, id);
        }

        // refresh options menus
        invalidateOptionsMenu();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_BACK:
            confirmOnAppQuit();
            return true;
        default:
            return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.menu_new_file_folder:
            final Dialog newFolderDialog = createNewSingleFolderDialog();
            newFolderDialog.show();
            return true;
        case R.id.menu_list_mode:
            if (item.isChecked()) {
                // Do nothing
            } else {
                item.setChecked(true);
                switchViewMode(VIEW_AS_LIST);
            }
            return true;
        case R.id.menu_grid_mode:
            if (item.isChecked()) {
                // Do nothing
            } else {
                item.setChecked(true);
                switchViewMode(VIEW_AS_GRID);
            }
            return true;
        case R.id.menu_pasteFile:
            // last clip
            int count = mClipHistoryAdapter.getCount();
            if (count >= 1) {
                ClipItemAdapter adapter = mClipHistoryAdapter
                        .getItem(mClipHistoryAdapter.getCount() - 1);
                if (adapter != null) {
                    HashSet<FileClip> toPaste = adapter.prepare();
                    if (toPaste.size() > 0) {
                        adapter.pasteFileClip(toPaste);
                    }
                }
            }
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        boolean canPaste = false;
        int clipCount = mClipHistoryAdapter.getCount();
        if (clipCount >= 1) {
            ClipItemAdapter adapter = mClipHistoryAdapter
                    .getItem(clipCount - 1);
            float clipType = adapter.getClipType();
            canPaste = (clipType == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE || clipType == ClipSourceProvider.CLIP_TYPE_CUT_SOURCE);
        }

        menu.findItem(R.id.menu_pasteFile)
                .setVisible(
                        (mOptionPasteAvailable && canPaste && sCurrentDir != null) ? true
                                : false);
        menu.findItem(R.id.menu_new_file_folder).setVisible(
                sCurrentDir != null ? true : false);
        MenuItem searchItem = menu.findItem(R.id.menu_search);
        mSearchView = (SearchView) searchItem.getActionView();
        mSearchView.setOnQueryTextListener(this);
        searchItem.setVisible(sCurrentDir != null ? true : false);

        switch (mCurrentViewMode) {
        case VIEW_AS_LIST:
            menu.findItem(R.id.menu_list_mode).setChecked(true);
            break;
        case VIEW_AS_GRID:
            menu.findItem(R.id.menu_grid_mode).setChecked(true);
            break;
        }

        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        String searchDir = FilePathUrlManager.getDir(mFileManager
                .getCurrentUrl());
        handleFileSearch(query, searchDir, false);
        return true;
    }

    public boolean onSameFileSystem(String filePath, String otherFilePath)
            throws IOException {
        if (KLOG) {
            Log.i(TAG, "Check if on same file system for path:" + filePath
                    + " and path:" + otherFilePath);
        }
        String fileCanonicalPath = new File(filePath).getCanonicalPath();
        String otherFileCanonicalPath = new File(otherFilePath)
                .getCanonicalPath();
        ArrayList<String> mountedFileSystems = mAppController
                .getCurrentMountedFileSystems();
        String fileSystem_1 = null;
        String fileSystem_2 = null;

        // Make sure we go through all the device paths,
        // because different file system path could possibly hold the same
        // prefix.
        for (String fileSystem : mountedFileSystems) {
            if (fileCanonicalPath.startsWith(fileSystem)) {
                fileSystem_1 = fileSystem;
            }

            if (otherFileCanonicalPath.startsWith(fileSystem)) {
                fileSystem_2 = fileSystem;
            }
        }

        if (fileSystem_1 == null || fileSystem_2 == null
                || !fileSystem_1.equals(fileSystem_2)) {
            if (KLOG) {
                Log.i(TAG, "File: " + filePath + " and file: " + otherFilePath
                        + " are not in the same filesystem!");
            }
            return false;
        } else {
            return true;
        }
    }

    // implement EventHandler.DeviceDataListener::onStorageDeviceItemChanged
    @Override
    public void onStorageDeviceItemChanged() {
        refreshTaskGroups();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v instanceof ImageButton) {
            Bitmap shapedArea = ((BitmapDrawable) ((ImageButton) v)
                    .getDrawable()).getBitmap();
            if (shapedArea != null) {
                if (shapedArea.getPixel((int) event.getX(), (int) event.getY()) != 0) {
                    Log.i(TAG, "File path button valid touch.");
                    v.performClick();
                    return true;
                } else {
                    Log.i(TAG, "File path button invalid touch.");
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onUrlContentUpdated(String newUrl, int contentSize,
            boolean fromHistory) {
        Log.i(TAG, "onUrlContentUpdated newUrl:" + newUrl);
        String smartPattern = FilePathUrlManager.getSmartPattern(newUrl);
        String extraInfo = null;
        if (smartPattern != null) {
            String patternStr = getPatternStrFromPattern(smartPattern);
            String query = FilePathUrlManager.getQueryStr(newUrl);
            String searchDir = FilePathUrlManager.getDir(newUrl);
            String searchDescription = null;
            Resources resouces = getResources();

            if (query != null) {
                searchDescription = resouces.getString(
                        R.string.rec_search_current_dir, searchDir);
                extraInfo = String.format(
                        "%s, %s '%s'. %s%d %s",
                        searchDescription,
                        resouces.getString(R.string.file_name_contain),
                        query,
                        contentSize < IfConfig.MAX_LISTITEM_COUNT ? resouces
                                .getString(R.string.found_) : resouces
                                .getString(R.string.show_first_), contentSize,
                        resouces.getString(R.string.items));
            } else {
                searchDescription = resouces.getString(R.string.global_search);
                extraInfo = String.format(
                        "%s %s. %s%d %s",
                        searchDescription,
                        patternStr,
                        contentSize < IfConfig.MAX_LISTITEM_COUNT ? resouces
                                .getString(R.string.found_) : resouces
                                .getString(R.string.show_first_), contentSize,
                        resouces.getString(R.string.items));
            }
        }
        mPathNavigator.setStatusInfo(extraInfo);

        if (FilePathUrlManager.isSearchUrl(newUrl)) {
            mPathNavigator.switchToIndicateMode();
        } else {
            Log.i(TAG, "Call path navigator to switch to navigate mode.");
            mPathNavigator.switchToNavigateMode();
        }

        if (mPathNavigator != null) {
            mPathNavigator.updatePathNodes(mFileManager.getCurrentUrl());
        }

        int currentPosition = mFileManager.getCurrentUrlPosition();
        boolean canForward = currentPosition < (mFileManager
                .getUrlHistoryCount() - 1);
        boolean canBackward = currentPosition > 0;
        mNavForwardButton.setEnabled(canForward);
        mNavBackwardButton.setEnabled(canBackward);

        setCurrentDir(newUrl);

        invalidateOptionsMenu();
    }

    @Override
    public void showDeleteUi() {
        if (!mClipBoardDrawer.isOpened()) {
            mClipBoardDrawer.animateOpen();
        }
    }

    @Override
    public void showPasteUi() {
        if (!mClipBoardDrawer.isOpened()) {
            mClipBoardDrawer.animateOpen();
        }
    }

    public void switchViewMode(int viewMode) {
        switch (viewMode) {
        case VIEW_AS_LIST:
            if (mSlidingLayer != null && mSlidingLayer.isOpened()) {
                mFileListView.setVisibility(View.GONE);
            } else {
                mFileListView.setVisibility(View.VISIBLE);
            }

            mFileGridView.setVisibility(View.GONE);
            mCurrentShowList = mFileListView;
            mCurrentViewMode = VIEW_AS_LIST;
            break;
        case VIEW_AS_GRID:
            if (mSlidingLayer != null && mSlidingLayer.isOpened()) {
                mFileGridView.setVisibility(View.GONE);
            } else {
                mFileGridView.setVisibility(View.VISIBLE);
            }

            mFileListView.setVisibility(View.GONE);
            mCurrentShowList = mFileGridView;
            mCurrentViewMode = VIEW_AS_GRID;
            break;
        }

        if (mAppController != null) {
            mAppController.setCurrentFileListView(mCurrentShowList);
        }

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_VIEW_MODE, viewMode);
        editor.apply();
    }

    public void updateCachedFilePaths(String pattern,
            HashMap<String, String> changeSet) {
        mFileManager.updateCachedFilePaths(pattern, changeSet);

        if (pattern.equals(FilePathUrlManager.getSmartPattern(mFileManager
                .getCurrentUrl()))) {
            mAppController.updateDirectory(mFileManager.refreshUrlContent());
        }

    }

    private void callMediaScanOnCreatedFile(String filePath) {
        File file = new File(filePath);

        MediaScannerConnection.scanFile(this,
                new String[] { file.getAbsolutePath() }, null,
                new OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        Log.i(TAG, "Media scanner scan file: " + path
                                + " successfully.");
                    }
                });
    }

    private void closeSlidingLayer(boolean smooth) {
        if (mSlidingLayer != null && mSlidingLayer.isOpened()) {
            mSlidingLayer.closeLayer(smooth);
            if (mCollectionSwitchButton != null) {
                mCollectionSwitchButton
                        .setImageResource(R.drawable.collection_expand);
            }
        }
        mCurrentShowList.setVisibility(View.VISIBLE);
    }

    private void confirmOnAppQuit() {
        Resources resources = getResources();
        String message = resources.getString(R.string.confirm_quit,
                resources.getString(R.string.app_name));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quit)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                IfExplorer.this.finish();
                            }
                        }).setNegativeButton(R.string.cancel, null)
                .setMessage(message).create();

        dialog.show();
    }

    private Dialog createNewSingleFolderDialog() {
        View contentRootView = LayoutInflater.from(this).inflate(
                R.layout.create_folder_dialog, null);
        final EditText nameEdit = (EditText) contentRootView
                .findViewById(R.id.name_edit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.create_folder)
                .setView(contentRootView)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                Resources resources = getResources();
                                String name = nameEdit.getText().toString();
                                String typeStr = resources
                                        .getString(R.string.folder);
                                String errStr = null;

                                int ret = FileUtil.createFile(
                                        IfExplorer.sCurrentDir, name,
                                        FileUtil.FILE_TYPE_DIRECTORY,
                                        IfExplorer.this);
                                if (ret == FileUtil.OP_SUCCESS) {
                                    Toast.makeText(
                                            IfExplorer.this,
                                            resources.getString(
                                                    R.string.folder_created,
                                                    name), Toast.LENGTH_SHORT)
                                            .show();
                                } else {
                                    errStr = FileUtil.getDefaultFileOpErrStr(
                                            resources, ret);

                                    Toast.makeText(
                                            IfExplorer.this,
                                            resources.getString(
                                                    R.string.new_failed,
                                                    typeStr, name, errStr),
                                            Toast.LENGTH_LONG).show();
                                }
                                dialog.dismiss();
                            }
                        })
                .setNegativeButton(R.string.cancel,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                dialog.dismiss();
                            }
                        });
        return builder.create();
    }

    private int getDeviceDisplayType() {
        if (findViewById(R.id.phone_port_root) != null) {
            return DEVICE_PHONE_PORT;
        } else if (findViewById(R.id.phone_land_root) != null) {
            return DEVICE_PHONE_LAND;
        } else if (findViewById(R.id.tablet_land_root) != null) {
            return DEVICE_TAB_LAND;
        } else if (findViewById(R.id.tablet_port_root) != null) {
            return DEVICE_TAB_PORT;
        } else {
            return DEVICE_UNKNOWN;
        }
    }

    private String getPatternStrFromPattern(String pattern) {
        if (pattern.equals(FilePathUrlManager.SMART_IMAGE)) {
            return getResources().getString(R.string.image_file);
        } else if (pattern.equals(FilePathUrlManager.SMART_MOVIES)) {
            return getResources().getString(R.string.video_file);
        } else if (pattern.equals(FilePathUrlManager.SMART_MUSIC)) {
            return getResources().getString(R.string.audio_file);
        } else if (pattern.equals(FilePathUrlManager.SMART_APK)) {
            return getResources().getString(R.string.apk_file);
        } else {
            return getResources().getString(R.string.file);
        }
    }

    private void handleFileSearch(String queryStr, String searchDir,
            boolean silent) {
        mAppController.searchFiles(StorageUtil.TYPE_FILE, queryStr, searchDir,
                IfConfig.MAX_SEARCH_DEPTH, silent);
        if (!silent) {
            String url = FilePathUrlManager.buildUrl(searchDir, queryStr,
                    FilePathUrlManager.SMART_FILE);
            mFileManager.getNextUrlContent(url, true, false);
        }
    }

    private void handleIntent(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_SEARCH.equals(action)) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            String searchDir = FilePathUrlManager.getDir(mFileManager
                    .getCurrentUrl());
            handleFileSearch(query, searchDir, false);
        }
    }

    private void initSlidingLayerState() {
        mSlidingLayer.setStickTo(SlidingLayer.STICK_TO_TOP);
        LayoutParams rlp = (LayoutParams) mSlidingLayer.getLayoutParams();
        rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP);

        mSlidingLayer.setLayoutParams(rlp);
        mSlidingLayer.setShadowWidth(0);
        mSlidingLayer.setShadowDrawable(null);
        mSlidingLayer.setSlidingEnabled(false);
        mSlidingLayer.setOpenOnTapEnabled(false);
        mSlidingLayer.setCloseOnTapEnabled(false);
    }

    private void openSlidingLayer(boolean smooth) {
        if (mSlidingLayer != null && !mSlidingLayer.isOpened()) {
            mSlidingLayer.openLayer(smooth);
            mCollectionSwitchButton
                    .setImageResource(R.drawable.collection_collapse);
            mCurrentShowList.setVisibility(View.GONE);
        }
    }

    private void preScanCategoriedFiles() {

    }

    private void setActivatingActionMode(ActionMode actionMode) {
        mActivatingActionMode = actionMode;
    }

    private void setupAddressBar() {
        mAddressBar = findViewById(R.id.task_bar);
        mPathNavigator = (FilePathNavigator) mAddressBar
                .findViewById(R.id.filePath_navigator);
        mAppController.addExtraFileDragStartedListener(mPathNavigator);
        mPathNavigator.setNavigationCallback(this);
        mPathNavigator.setOnDropDelegate(mAppController);
        mPathNavigator.setPathButtonOnDropDelegate(mAppController);
        mPathNavigator.setPathButtonOpenDelegate(mAppController);

        //if (IfConfig.PRODUCT_IS_MONITOR) {
        //    mPathNavigator.setRootNodeIconResId(R.drawable.loc_monitor_dark);
        //}

        mNavForwardButton = (ImageView) mPathNavigator
                .findViewById(R.id.navigation_previous);
        mNavForwardButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mAppController.updateDirectory(
                        mFileManager.getNextUrlContent(null, false, true), true);
            }

        });
        mNavBackwardButton = (ImageView) mPathNavigator
                .findViewById(R.id.navigation_back);
        mNavBackwardButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                mAppController.updateDirectory(
                        mFileManager.getPreviousUrlContent(), true);
            }

        });
        mCollectionSwitchButton = (ImageView) mPathNavigator
                .findViewById(R.id.collection_panel_switch);
        mCollectionSwitchButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mSlidingLayer != null && mSlidingLayer.isOpened()) {
                    closeSlidingLayer(true);
                } else if (mSlidingLayer != null && !mSlidingLayer.isOpened()) {
                    openSlidingLayer(true);
                }
            }

        });
        int currentPosition = mFileManager.getCurrentUrlPosition();

        boolean canForward = currentPosition < (mFileManager
                .getUrlHistoryCount() - 1);
        boolean canBackward = currentPosition > 0;
        mNavForwardButton.setEnabled(canForward);
        mNavBackwardButton.setEnabled(canBackward);
    }

    private void setupClipBoard() {
        mClipHistoryAdapter = new ClipHistoryAdapter(this, R.layout.clip_group,
                R.id.clip_title, new ArrayList<ClipItemAdapter>());
        mClipHistoryAdapter.setClipHistoryCallback(this);

        mClipBoardDrawer = (SlidingDrawerEx) findViewById(R.id.clipboard);
        mClipBoardIndicator = mClipBoardDrawer
                .findViewById(R.id.clipboard_indicator);
        mClipHistoryListView = (ListView) mClipBoardDrawer
                .findViewById(R.id.clip_history_list);
        mClipHistoryListView.setAdapter(mClipHistoryAdapter);
        mButtonClearClipboard = (Button) mClipBoardDrawer
                .findViewById(R.id.clearAll);
        mButtonClearClipboard.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mClipHistoryAdapter.clearClipRecords();
            }
        });
        mButtonCloseClipboard = (Button) mClipBoardDrawer
                .findViewById(R.id.close);
        mButtonCloseClipboard.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mClipBoardDrawer.close();
            }
        });
    }

    private void setupFileList() {
        mFileDataListAdapter = mAppController.getFileDataListAdapter();
        mFileDataGridAdapter = mAppController.getFileDataGridAdapter();
        mFileListMultiChoiceListener = new FileMultiChoiceListener();

        mFileListView = (DropableListView) findViewById(R.id.file_listview);
        mFileListView.setAdapter(mFileDataListAdapter);
        mFileListView.setOnItemClickListener(this);
        mFileListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mFileListView.setMultiChoiceModeListener(mFileListMultiChoiceListener);

        mFileGridView = (DropableGridView) findViewById(R.id.file_gridview);
        mFileGridView.setAdapter(mFileDataGridAdapter);
        mFileGridView.setOnItemClickListener(this);
        mFileGridView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        mFileGridView.setMultiChoiceModeListener(mFileListMultiChoiceListener);
        mFileGridView.setOnDropDelegate(mAppController);

        // default
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        int savedViewMode = prefs.getInt(KEY_VIEW_MODE, VIEW_AS_GRID);
        switchViewMode(savedViewMode);

        mAppController.updateDirectory(mFileManager.refreshUrlContent());
    }

    private void showDetailDialog(HashSet<Object> fileItems) {
        View contentRootView = LayoutInflater.from(this).inflate(
                R.layout.single_file_detail, null);

        final ImageView iconView = (ImageView) contentRootView
                .findViewById(R.id.file_icon);
        final TextView nameView = (TextView) contentRootView
                .findViewById(R.id.file_name);
        for (Object item : fileItems) {
            if (item instanceof FileItem) {
                Drawable drawable = ((FileItem) item).getIconDrawable();
                if (drawable != null) {
                    iconView.setImageDrawable(drawable);
                } else {
                    iconView.setImageResource(((FileItem) item)
                            .getIconResource());
                }
                nameView.setText(((FileItem) item).getName());
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.detail).setView(contentRootView)
                .setPositiveButton(R.string.cancel, null).create();

        dialog.show();
    }

    private void updateMediaStoreOnDeletedFile(String filePath, String pattern) {
        Uri url;
        String dbWhereStrPrefix;
        if (pattern == null) {
            url = MediaStore.Files.getContentUri("external");
            dbWhereStrPrefix = MediaStore.Files.FileColumns.DATA;
        } else {
            if (pattern.equals(FilePathUrlManager.SMART_IMAGE)) {
                url = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                dbWhereStrPrefix = MediaStore.Images.Media.DATA;
            } else if (pattern.equals(FilePathUrlManager.SMART_MUSIC)) {
                url = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                dbWhereStrPrefix = MediaStore.Audio.Media.DATA;
            } else if (pattern.equals(FilePathUrlManager.SMART_MOVIES)) {
                url = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                dbWhereStrPrefix = MediaStore.Video.Media.DATA;
            } else {
                url = MediaStore.Files.getContentUri("external");
                dbWhereStrPrefix = MediaStore.Files.FileColumns.DATA;
            }
        }

        String dbWhereStr = dbWhereStrPrefix + "=?";
        ContentResolver resolver = getContentResolver();
        resolver.delete(url, dbWhereStr, new String[] { filePath });
    }

    protected void initializeSlidingLayer() {
        // Reset at first
        mSlidingLayer = null;
        if (mCollectionSwitchButton != null) {
            mCollectionSwitchButton.setVisibility(View.GONE);
        }

        mSlidingLayer = (SlidingLayer) findViewById(R.id.sliding_panel);

        if (mSlidingLayer != null) {
            // Using sliding layer means display space is limited,
            // reduce maximum visible file path node according to device display
            // type
            switch (mDeviceDisplayType) {
            case DEVICE_TAB_PORT:
                FilePathNavigator.MAX_BUTTON_NUM = 4;
                break;
            case DEVICE_PHONE_LAND:
                FilePathNavigator.MAX_BUTTON_NUM = 4;
                break;
            case DEVICE_PHONE_PORT:
                FilePathNavigator.MAX_BUTTON_NUM = 2;
                break;
            }

            initSlidingLayerState();

            if (mCollectionSwitchButton != null) {
                mCollectionSwitchButton.setVisibility(View.VISIBLE);
            }
        } else {
            FilePathNavigator.MAX_BUTTON_NUM = 5;
        }
    }

    protected void intializeMiniWidgets() {
        /* Favorites */
        mFavoritesMiniWidget = (TaskGroupListWidget) findViewById(R.id.favorite_collection);
        mFavoritesMiniWidget.setGroupTag(kTagTaskGroupBase + 1);
        mFavoritesMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.category));
        mFavoritesMiniWidget.setEjectable(false);
        AbsListView favoriteList = mFavoritesMiniWidget.activateListMode();
        favoriteList.setOnItemClickListener(this);
        favoriteList.setAdapter(mAppController.getFavoriteDataAdapter());
        mMiniPanelWidgets.add(mFavoritesMiniWidget);

        /* Storage devices */
        // internal SD card
        mInternalStorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.internal_collection);
        mInternalStorageMiniWidget.setGroupTag(kTagTaskGroupBase + 2);
        mInternalStorageMiniWidget.setDefaultGroupLabel(getResources()
                .getString(R.string.internal_storage));
        mInternalStorageMiniWidget.setEjectable(false);
        AbsListView intSdcardList = mInternalStorageMiniWidget
                .activateListMode();
        intSdcardList.setOnItemClickListener(this);
        intSdcardList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.Internal));
        mMiniPanelWidgets.add(mInternalStorageMiniWidget);
        // external SD card
        mSdcardMiniWidget = (TaskGroupListWidget) findViewById(R.id.sdcard_collection);
        mSdcardMiniWidget.setGroupTag(kTagTaskGroupBase + 3);
        mSdcardMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.sdcard_storage));
        mSdcardMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView extSdcardList = mSdcardMiniWidget.activateListMode();
        extSdcardList.setOnItemClickListener(this);
        extSdcardList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.SDSlot));
        mMiniPanelWidgets.add(mSdcardMiniWidget);
        // USB 0 storage
        mUsb0StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb0_disk_collection);
        mUsb0StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 4);
        mUsb0StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage));
        mUsb0StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb0storageList = mUsb0StorageMiniWidget.activateListMode();
        usb0storageList.setOnItemClickListener(this);
        usb0storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort0));
        mMiniPanelWidgets.add(mUsb0StorageMiniWidget);
        // USB 1 storage
        mUsb1StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb1_disk_collection);
        mUsb1StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 5);
        mUsb1StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb1_storage));
        mUsb1StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb1storageList = mUsb1StorageMiniWidget.activateListMode();
        usb1storageList.setOnItemClickListener(this);
        usb1storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort1));
        mMiniPanelWidgets.add(mUsb1StorageMiniWidget);
        // USB 2 storage
        mUsb2StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb2_disk_collection);
        mUsb2StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 6);
        mUsb2StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb2_storage));
        mUsb2StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb2storageList = mUsb2StorageMiniWidget.activateListMode();
        usb2storageList.setOnItemClickListener(this);
        usb2storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort2));
        mMiniPanelWidgets.add(mUsb2StorageMiniWidget);
        // USB 3 storage
        mUsb3StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb3_disk_collection);
        mUsb3StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 7);
        mUsb3StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb3_storage));
        mUsb3StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb3storageList = mUsb3StorageMiniWidget.activateListMode();
        usb3storageList.setOnItemClickListener(this);
        usb3storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort3));
        mMiniPanelWidgets.add(mUsb3StorageMiniWidget);
        // USB 4 storage
        mUsb4StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb4_disk_collection);
        mUsb4StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 8);
        mUsb4StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb4_storage));
        mUsb4StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb4storageList = mUsb4StorageMiniWidget.activateListMode();
        usb4storageList.setOnItemClickListener(this);
        usb4storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort4));
        mMiniPanelWidgets.add(mUsb4StorageMiniWidget);
        // USB 5 storage
        mUsb5StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb5_disk_collection);
        mUsb5StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 9);
        mUsb5StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb5_storage));
        mUsb5StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb5storageList = mUsb5StorageMiniWidget.activateListMode();
        usb5storageList.setOnItemClickListener(this);
        usb5storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort5));
        mMiniPanelWidgets.add(mUsb5StorageMiniWidget);
        // USB Storage A
        mStorageA_MiniWidget = (TaskGroupListWidget) findViewById(R.id.usb_a_collection);
        mStorageA_MiniWidget.setGroupTag(kTagTaskGroupBase + 10);
        mStorageA_MiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage_a));
        mStorageA_MiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usba_storageList = mStorageA_MiniWidget.activateListMode();
        usba_storageList.setOnItemClickListener(this);
        DeviceDataAdapter usba_adapter = mAppController
                .getDeviceDataAdapter(StorageUtil.USBSDA);
        usba_storageList.setAdapter(usba_adapter);
        mMiniPanelWidgets.add(mStorageA_MiniWidget);
        // USB Storage B
        mStorageB_MiniWidget = (TaskGroupListWidget) findViewById(R.id.usb_b_collection);
        mStorageB_MiniWidget.setGroupTag(kTagTaskGroupBase + 11);
        mStorageB_MiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage_b));
        mStorageB_MiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usbb_storageList = mStorageB_MiniWidget.activateListMode();
        usbb_storageList.setOnItemClickListener(this);
        usbb_storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBSDB));
        mMiniPanelWidgets.add(mStorageB_MiniWidget);
        // USB Storage C
        mStorageC_MiniWidget = (TaskGroupListWidget) findViewById(R.id.usb_c_collection);
        mStorageC_MiniWidget.setGroupTag(kTagTaskGroupBase + 12);
        mStorageC_MiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage_c));
        mStorageC_MiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usbc_storageList = mStorageC_MiniWidget.activateListMode();
        usbc_storageList.setOnItemClickListener(this);
        usbc_storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBSDC));
        mMiniPanelWidgets.add(mStorageC_MiniWidget);
        // USB Storage D
        mStorageD_MiniWidget = (TaskGroupListWidget) findViewById(R.id.usb_d_collection);
        mStorageD_MiniWidget.setGroupTag(kTagTaskGroupBase + 13);
        mStorageD_MiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage_d));
        mStorageD_MiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usbd_storageList = mStorageD_MiniWidget.activateListMode();
        usbd_storageList.setOnItemClickListener(this);
        usbd_storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBSDD));
        mMiniPanelWidgets.add(mStorageD_MiniWidget);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "IfExplorer-onCreate");
        mSavedStateInstatnce = savedInstanceState;
        super.onCreate(savedInstanceState);
        // FIXME: some mechanism might not be interrupted by screen rotation
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        // requestWindowFeature(Window.FEATURE_NO_TITLE);
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        // WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.main_layout);
        // Get device display type at first, to decide UI variants.
        mDeviceDisplayType = getDeviceDisplayType();

        restoreSavedInfo(savedInstanceState);

        // file manager
        mFileManager = FilePathUrlManager.getSingleton(mInitLocation);
        if (mSavedBrowseHistory != null) {
            mFileManager.setUrlHistory(mSavedBrowseHistory);
        }
        if (mSavedHistoryPosition >= 0) {
            mFileManager.setHistoryPosition(mSavedHistoryPosition);
        }
        // event handler
        mAppController = new IfAppController(this, mFileManager);
        mAppController.setDeviceDataListener(this);
        mAppController.setUiCallback(this);

        // Restore saved data for file manager and app controller
        if (savedInstanceState != null) {
            HashMap<String, ArrayList<String>> savedSearchResult = (HashMap<String, ArrayList<String>>) savedInstanceState
                    .getSerializable(KEY_SAVE_SEARCH_RESULTS);
            mAppController.setCachedSearchResult(savedSearchResult);
            mFileManager.setSearchFilePatternPath(savedSearchResult);

            ArrayList<String> savedAudioFilePaths = savedInstanceState
                    .getStringArrayList(KEY_SAVE_CACHED_AUDIO_FILEPATHS);
            mAppController.setCachedAudioFilePaths(savedAudioFilePaths);
            mFileManager.setAudioPatternPath(savedAudioFilePaths);

            ArrayList<String> savedVideoFilePaths = savedInstanceState
                    .getStringArrayList(KEY_SAVE_CACHED_VIDEO_FILEPATHS);
            mAppController.setCachedVideoFilePaths(savedVideoFilePaths);
            mFileManager.setVideoPatternPath(savedVideoFilePaths);

            ArrayList<String> savedApkFilePaths = savedInstanceState
                    .getStringArrayList(KEY_SAVE_CACHED_APK_FILEPATHS);
            mAppController.setCachedApkFilePaths(savedApkFilePaths);
            mFileManager.setApkPatternPath(savedApkFilePaths);
        }

        // storage monitoring
        mStorageIntentFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
        mStorageIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        mStorageIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mStorageIntentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        mStorageIntentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        mStorageIntentFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
        mStorageIntentFilter.addAction(Intent.ACTION_MEDIA_SHARED);
        mStorageIntentFilter.addDataScheme("file");
        mStorageIntentFilter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        registerReceiver(mStorageBroadcastReceiver, mStorageIntentFilter);

        // initialize mini widget
        intializeMiniWidgets();
        // initialize storage device and do pre scan
        mAppController.intializeStorageDevicesAsync();
        // initialize default favorites
        mAppController.initializeDefaultFavorites();
        // refresh collection view
        refreshTaskGroups();

        setupAddressBar();
        setupFileList();
        setupClipBoard();

        // initialize sliding layer
        initializeSlidingLayer();

        // update current directory
        mAppController.updateDirectory(mFileManager.refreshUrlContent());
        // global search pre-scan
        preScanCategoriedFiles();

        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        mAppController.cancelAllBackgroundWork();
        unregisterReceiver(mStorageBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_SAVE_LOCATION, mFileManager.getCurrentUrl());
        outState.putStringArrayList(KEY_SAVE_BROWSE_HISTORY,
                mFileManager.getUrlHistory());
        outState.putInt(KEY_SAVE_HISTORY_POSITION,
                mFileManager.getHistoryPosition());
        outState.putSerializable(KEY_SAVE_SEARCH_RESULTS,
                mAppController.getCachedSearchResult());
        outState.putStringArrayList(KEY_SAVE_CACHED_AUDIO_FILEPATHS,
                mAppController.getCachedAudioFilePaths());
        outState.putStringArrayList(KEY_SAVE_CACHED_VIDEO_FILEPATHS,
                mAppController.getCachedVideoFilePaths());
        outState.putStringArrayList(KEY_SAVE_CACHED_APK_FILEPATHS,
                mAppController.getCachedApkFilePaths());
        if (mSlidingLayer != null && mSlidingLayer.isOpened()) {
            outState.putBoolean(KEY_SAVE_SLIDING_LAYER_OPEN, true);
        } else {
            outState.putBoolean(KEY_SAVE_SLIDING_LAYER_OPEN, false);
        }
        outState.putInt(KEY_SAVE_FILE_LIST_TOTAL_NUM,
                mCurrentShowList.getCount());
        outState.putInt(KEY_SAVE_FILE_LIST_SELECTED_NUM,
                mCurrentShowList.getCheckedItemCount());

        super.onSaveInstanceState(outState);
    }

    protected void refreshTaskGroups() {
        for (TaskGroupListWidget taskGroup : mMiniPanelWidgets) {
            AbsListView taskList = taskGroup.activateListMode();
            if (tempLOG) {
                Log.i("@temp", "task group: " + taskGroup.getGroupTag()
                        + " items: " + taskList.getCount());
            }

            if (taskList.getCount() == 0) {
                taskGroup.hideHeader();
            } else {
                if (taskList.getAdapter() instanceof DeviceDataAdapter) {
                    DeviceItem firstItem = (DeviceItem) taskList
                            .getItemAtPosition(0);
                    if (firstItem != null) {
                        String deviceName = firstItem.getContainerDeviceName();
                        if (deviceName != null) {
                            taskGroup.setLabel(deviceName);
                        } else {
                            taskGroup
                                    .setLabel(taskGroup.getDefaultGroupLabel());
                        }
                    }
                }

                taskGroup.showHeader();
            }
        }
    }

    protected void restoreSavedInfo(Bundle savedInfo) {
        if (savedInfo != null) {
            mInitLocation = savedInfo.getString(KEY_SAVE_LOCATION);
            mSavedBrowseHistory = savedInfo
                    .getStringArrayList(KEY_SAVE_BROWSE_HISTORY);
            mSavedHistoryPosition = savedInfo.getInt(KEY_SAVE_HISTORY_POSITION);

            super.onRestoreInstanceState(savedInfo);
        } else {
            mInitLocation = StorageUtil.getHomeDir();
        }

        File initDir = new File(mInitLocation);
        if (!initDir.exists() || !initDir.isDirectory()) {
            // Due to backward compatibility, this
            // path works in all kinds of android devices.
            mInitLocation = "/sdcard";
        }
    }
}

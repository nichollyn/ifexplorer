/*
    IfExplorer, an open source file manager for the Android system.
    Copyright (C) 2014  Kevin Lin
    <chenbin.lin@tpv-tech.com>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
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

import gem.com.readystatesoftware.viewbadger.BadgeView;
import gem.kevin.innov.FilePathUrlManager;
import gem.kevin.task.FileOperationTask;
import gem.kevin.task.FileOperationTask.FileOperationOptions;
import gem.kevin.task.FileOperationTask.SearchOpTaskListener;
import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.util.StorageUtil;
import gem.kevin.util.ThumbnailCreator;
import gem.kevin.util.ui.BunchToStackAnimation;
import gem.kevin.widget.ActionModeDraggableView;
import gem.kevin.widget.ActionModeDraggableView.DragContextInfo;
import gem.kevin.widget.ClipItemAdapter;
import gem.kevin.widget.DropAcceptable;
import gem.kevin.widget.DropableGridView;
import gem.kevin.widget.DropableListView;
import gem.kevin.widget.FileClip;
import gem.kevin.widget.OnDropDelegate;
import gem.kevin.widget.OpenDelegate;
import gem.kevin.widget.Openable;

import java.io.File;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.text.format.Time;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.DragShadowBuilder;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.sparseboolean.ifexplorer.IfExplorer.MultiChoiceConsumeOptions;
import com.sparseboolean.ifexplorer.ui.ClipHistoryAdapter;
import com.sparseboolean.ifexplorer.ui.DeviceDataAdapter;
import com.sparseboolean.ifexplorer.ui.FavoriteDataAdapter;
import com.sparseboolean.ifexplorer.ui.FilePathNavigator;
import com.sparseboolean.ifexplorer.ui.DropableFilePathButton;

public class IfAppController implements FilePathUrlManager.FilePathObserver,
        ActionModeDraggableView.DragProgressDelegate, OnDropDelegate,
        OpenDelegate, BunchToStackAnimation.BunchAnimationListener,
        FilePathNavigator.NavigationCallback, SearchOpTaskListener {

    public interface DeviceDataListener {
        public void onStorageDeviceItemChanged();
    }

    public class FileDataGridAdapter extends ArrayAdapter<FileItem> {
        public FileDataGridAdapter() {
            super(mContext, R.layout.file_griditem, new ArrayList<FileItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final FileItemViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.file_griditem, null);

                viewHolder = new FileItemViewHolder();
                viewHolder.nameTextView = (TextView) convertView
                        .findViewById(R.id.file_name);
                viewHolder.icon = (ImageView) convertView
                        .findViewById(R.id.file_icon);
                viewHolder.draggingView = (ImageView) convertView
                        .findViewById(R.id.dragging_view);
                viewHolder.dragReadyView = (ImageView) convertView
                        .findViewById(R.id.drag_ready_view);
                viewHolder.normalView = convertView
                        .findViewById(R.id.normal_view);

                viewHolder.position = position;

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (FileItemViewHolder) convertView.getTag();
            }

            viewHolder.position = position;

            AbsListView absListView = (AbsListView) parent;
            FileItem fileItem = getItem(position);
            // //////////////////////////////////////////////////////
            fileItem.setConvertView(convertView);
            fileItem.setPositionInAdapterList(position);

            if (fileItem.isVisible()) {
                convertView.setVisibility(View.VISIBLE);
            } else {
                convertView.setVisibility(View.GONE);
            }

            if ((Object) convertView instanceof ActionModeDraggableView) {
                ActionModeDraggableView dragabbleView = (ActionModeDraggableView) convertView;
                dragabbleView.setDragable(IfConfig.FILE_SUPPORT_DRAG);

                final DragContextInfo dragInfo = new DragContextInfo();
                dragInfo.data = fileItem;
                dragInfo.parentView = absListView;
                dragInfo.positionAsChild = position;
                dragInfo.draggingView = viewHolder.draggingView;
                dragInfo.dragReadyView = viewHolder.dragReadyView;
                dragInfo.normalView = viewHolder.normalView;
                dragabbleView.setDragContextInfo(dragInfo);

                dragabbleView.setInActionMode(absListView
                        .isItemChecked(position));
                dragabbleView.setDragDropDelegate(IfAppController.this);
                dragabbleView.setOnDropDelegate(IfAppController.this);
                dragabbleView.setOpenDelegate(IfAppController.this);
            } else {
                Log.i(TAG, "File grid items are not dragable!");
            }
            // ////////////////////////////////

            // icon
            Drawable iconDrawable = fileItem.getIconDrawable();
            if (iconDrawable != null) {
                viewHolder.icon.setImageDrawable(iconDrawable);
            } else {
                viewHolder.icon.setImageResource(fileItem.getIconResource());
            }
            // name
            String name = fileItem.getName();
            if (!name.contains(" ") && name.length() > 24) {
                viewHolder.nameTextView.setText(name.substring(0, 21) + "...");
            } else if (name.contains(" ") && name.length() > 22) {
                viewHolder.nameTextView.setText(name.substring(0, 19) + "...");
            } else {
                viewHolder.nameTextView.setText(name);
            }

            return convertView;
        }
    }

    public class FileDataListAdapter extends ArrayAdapter<FileItem> {
        private String mSizeStr;
        private static final String STR_NA = "N/A";

        public FileDataListAdapter() {
            super(mContext, R.layout.file_listitem, new ArrayList<FileItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final FileItemViewHolder viewHolder;

            if (convertView == null) {
                convertView = LayoutInflater.from(mContext).inflate(
                        R.layout.file_listitem, null);

                viewHolder = new FileItemViewHolder();
                viewHolder.nameTextView = (TextView) convertView
                        .findViewById(R.id.file_name);
                viewHolder.infoTextView = (TextView) convertView
                        .findViewById(R.id.file_info);
                viewHolder.icon = (ImageView) convertView
                        .findViewById(R.id.file_icon);
                viewHolder.draggingView = (ImageView) convertView
                        .findViewById(R.id.dragging_view);
                viewHolder.dragReadyView = (ImageView) convertView
                        .findViewById(R.id.drag_ready_view);
                viewHolder.normalView = convertView
                        .findViewById(R.id.normal_view);
                viewHolder.selectModeMark = (CheckBox) convertView
                        .findViewById(R.id.file_selected);

                viewHolder.position = position;

                convertView.setTag(viewHolder);
            } else {
                viewHolder = (FileItemViewHolder) convertView.getTag();
            }

            viewHolder.position = position;

            AbsListView absListView = (AbsListView) parent;
            FileItem fileItem = getItem(position);
            // //////////////////////////////////////////////////////
            fileItem.setConvertView(convertView);
            fileItem.setPositionInAdapterList(position);

            if (fileItem.isVisible()) {
                convertView.setVisibility(View.VISIBLE);
            } else {
                convertView.setVisibility(View.GONE);
            }

            if ((Object) convertView instanceof ActionModeDraggableView) {
                ActionModeDraggableView dragabbleView = (ActionModeDraggableView) convertView;
                dragabbleView.setDragable(IfConfig.FILE_SUPPORT_DRAG);

                final DragContextInfo dragInfo = new DragContextInfo();
                dragInfo.data = fileItem;
                dragInfo.parentView = absListView;
                dragInfo.positionAsChild = position;
                dragInfo.draggingView = viewHolder.draggingView;
                dragInfo.dragReadyView = viewHolder.dragReadyView;
                dragInfo.normalView = viewHolder.normalView;
                dragabbleView.setDragContextInfo(dragInfo);

                dragabbleView.setInActionMode(absListView
                        .isItemChecked(position));
                dragabbleView.setDragDropDelegate(IfAppController.this);
                dragabbleView.setOnDropDelegate(IfAppController.this);
                dragabbleView.setOpenDelegate(IfAppController.this);
            } else {
                Log.i(TAG, "File list items are not dragable!");
            }
            // ////////////////////////////////

            Resources resources = getContext().getResources();
            // initial visibility for views in convertView
            viewHolder.infoTextView.setVisibility(View.VISIBLE);

            viewHolder.selectModeMark.setChecked(absListView
                    .isItemChecked(position));

            // icon
            Drawable iconDrawable = fileItem.getIconDrawable();
            if (iconDrawable != null) {
                viewHolder.icon.setImageDrawable(iconDrawable);
            } else {
                viewHolder.icon.setImageResource(fileItem.getIconResource());
            }
            // name
            String name = fileItem.getName();
            int maxDisplayLength = resources
                    .getInteger(R.integer.file_label_max_display_length);
            if (name.length() > maxDisplayLength) {
                viewHolder.nameTextView.setText(name.substring(0,
                        maxDisplayLength) + "...");
            } else {
                viewHolder.nameTextView.setText(name);
            }
            // info
            String infoText = "";
            String permissionStr = fileItem.getPermissionStr();
            if (fileItem.isDirectory()) {
                String strItems = resources.getString(R.string.items);
                int num_items = (int) fileItem.getSize();
                if (num_items >= 0) {
                    infoText = (permissionStr != null) ? num_items + " "
                            + strItems + " " + permissionStr : num_items + " "
                            + strItems;
                } else {
                    infoText = (permissionStr != null) ? STR_NA + " "
                            + permissionStr : STR_NA;
                }
            } else {
                mSizeStr = Formatter.formatFileSize(mContext,
                        fileItem.getSize());
                infoText = (permissionStr != null) ? mSizeStr + " "
                        + permissionStr : mSizeStr;
            }
            viewHolder.infoTextView.setText(infoText);

            return convertView;
        }
    }

    public class FileDropData {
        public String fromDirPath;
        public HashSet<Object> dropData;

        public FileDropData(String fromDirPath, HashSet<Object> dropData) {
            this.fromDirPath = fromDirPath;
            this.dropData = dropData;
        }
    }

    public static interface UiCallback {
        public void onUrlContentUpdated(String newUrl, int contentSize,
                boolean fromHistory);
    }

    private class FileContentUpdateProcedure implements Runnable {
        ArrayList<String> _filePaths;
        boolean _fromHistory = false;

        @SuppressWarnings("unchecked")
        public FileContentUpdateProcedure(ArrayList<String> filePaths,
                boolean fromHistory) {
            _filePaths = (ArrayList<String>) filePaths.clone();
            _fromHistory = fromHistory;
        }

        @Override
        public void run() {
            String currentUrl = mFileManager.getCurrentUrl();

            for (String path : _filePaths) {
                appendFileItem(path);
            }

            Message msg = mUiHandler.obtainMessage(MSG_CURRENT_DIR_UPDATED);
            msg.obj = currentUrl;
            msg.arg1 = _filePaths.size();
            msg.arg2 = _fromHistory ? 1 : 0;

            mUiHandler.sendMessage(msg);
        }

    }

    private static class FileItemViewHolder {
        TextView nameTextView;
        TextView infoTextView;
        ImageView icon;
        CheckBox selectModeMark;

        ImageView draggingView;
        ImageView dragReadyView;
        View normalView;

        public int position;
    }

    private class StorageDeviceItemGenerator extends
            AsyncTask<String, Void, ArrayList<DeviceItem>> {
        private int type;

        private StorageDeviceItemGenerator(int type) {
            this.type = type;
        }

        @Override
        protected ArrayList<DeviceItem> doInBackground(String... params) {
            if (isCancelled()) {
                return null;
            }

            switch (type) {
            case GEN_SINGLE_DISK_DEVICE_ITEM: {
                final ArrayList<DeviceItem> result = new ArrayList<DeviceItem>();
                IfStorageVolume volume = mStorageUtil
                        .getIfStorageVolumeFromPath(params[0]);
                if (volume == null) {
                    Log.i("@temp", "create IfStorageVolume by path:"
                            + params[0]);
                    mStorageUtil.createIfStorageVolumeByPath(params[0]);
                    volume = mStorageUtil.getIfStorageVolumeFromPath(params[0]);
                }

                DeviceItem deviceItem = createDeviceItemFromIfStorageVolume(volume);
                if (deviceItem != null) {
                    result.add(deviceItem);
                }
                return result;
            }
            case GEN_ALL_DEVICE_ITEM: {
                final ArrayList<DeviceItem> result = new ArrayList<DeviceItem>();
                ArrayList<IfStorageVolume> volumes = mStorageUtil
                        .getIfStorageVolumes();
                if (volumes != null) {
                    for (IfStorageVolume volume : volumes) {
                        DeviceItem deviceItem = createDeviceItemFromIfStorageVolume(volume);
                        if (deviceItem != null) {
                            if (tempLOG) {
                                Log.i("@temp", "add IfStorageVolume, path:"
                                        + deviceItem.getPath());
                            }

                            result.add(deviceItem);
                        }
                    }
                }
                return result;
            }
            default:
                return null;
            }
        }

        @Override
        protected void onPostExecute(final ArrayList<DeviceItem> result) {
            switch (type) {
            case GEN_SINGLE_DISK_DEVICE_ITEM: {
                if (result == null) {
                    return;
                }

                DeviceItem deviceItem = result.get(0);
                DeviceDataAdapter adapter = getApproximateDeviceAdapter(deviceItem
                        .getMountPort());
                if (adapter != null) {
                    adapter.add(deviceItem);
                    adapter.notifyDataSetChanged();
                    DeviceDataListener deviceDataListener = getDeviceDataListener();
                    if (deviceDataListener != null) {
                        deviceDataListener.onStorageDeviceItemChanged();
                    }
                }
                break;
            }
            case GEN_ALL_DEVICE_ITEM: {
                if (result == null) {
                    return;
                }

                for (DeviceItem item : result) {
                    DeviceDataAdapter adapter = getApproximateDeviceAdapter(item
                            .getMountPort());
                    if (adapter != null) {
                        if (tempLOG) {
                            Log.i("@temp",
                                    "found adapter: " + adapter.getIfTag()
                                            + " for mount point:"
                                            + item.getPath());
                        }

                        adapter.add(item);
                        adapter.notifyDataSetChanged();
                        DeviceDataListener deviceDataListener = getDeviceDataListener();
                        if (deviceDataListener != null) {
                            deviceDataListener.onStorageDeviceItemChanged();
                        }
                    }
                }
            }
            default:
                return;
            }
        }

        @Override
        protected void onPreExecute() {
            // do nothing
        }
    }

    private UiCallback mUiCallback = null;
    private static final String TAG = "IfExplorer-IfAppController";
    private static final boolean KLOG = true;
    private static final boolean tempLOG = false;

    public static final int MSG_UPDATE_CURRENT_DIR = 0;
    public static final int MSG_CURRENT_DIR_UPDATED = 1;
    public static final int MSG_SEARCH_PATH_UPDATED = 2;
    public static final int MSG_SEARCH_RESULT_UPDATED = 3;
    public static final int MSG_SEARCH_FINISHED = 4;
    public static final int MSG_SEARCH_CANCELLED = 5;

    public static final int MSG_FILES_BUNCH_UP_TO_STACK = 10;
    public static final int MSG_FILES_SPREAD_FROM_STACK = 11;
    public static final int MSG_FILES_PREPARE_DRAGGING_VIEW = 12;
    public static final int MSG_FILES_DRAG_READY = 13;

    private static final int GEN_SINGLE_DISK_DEVICE_ITEM = 0x01;
    private static final int GEN_ALL_DEVICE_ITEM = 0x02;
    private static final int UPDATE_INTERVAL_BASE = 1500;

    private static final ArrayList<AsyncTask<String, Void, ?>> mBackgroundWorks = new ArrayList<AsyncTask<String, Void, ?>>();
    private ArrayList<String> mCachedAudioFilePaths;
    private ArrayList<String> mCachedVideoFilePaths;
    private ArrayList<String> mCachedImageFilePaths;
    private ArrayList<String> mCachedApkFilePaths;
    private HashMap<String, ArrayList<String>> mCachedSearchResult;
    private boolean mAudioCached = false;
    private boolean mImageCached = false;
    private boolean mVideoCached = false;
    private boolean mApkFilesCached = false;
    private ProgressDialog mSearchDialog = null;
    private long mSearchResultLastUpdateTiming;

    private final Context mContext;
    protected final FilePathUrlManager mFileManager;
    private static final int SORT_NONE = FilePathUrlManager.SORT_NONE;
    private static final int SORT_ALPHA = FilePathUrlManager.SORT_ALPHA;
    private static final int SORT_TYPE = SORT_ALPHA + 1;
    private int mSortType = SORT_ALPHA;
    @SuppressWarnings("rawtypes")
    private static final Comparator mTypeComparator = new Comparator<FileItem>() {
        @Override
        public int compare(FileItem fileItem0, FileItem fileItem1) {
            String fileType0 = fileItem0.getFileType();
            String fileType1 = fileItem1.getFileType();

            if (fileType0.equals(FileItem.FILE_TYPE_FILE_FOLDER)
                    && !fileType1.equals(FileItem.FILE_TYPE_FILE_FOLDER)) {
                return -1;
            } else if (!fileType0.equals(FileItem.FILE_TYPE_FILE_FOLDER)
                    && fileType1.equals(FileItem.FILE_TYPE_FILE_FOLDER)) {
                return 1;
            }

            return fileType0.toLowerCase().compareTo(fileType1.toLowerCase());
        }
    };
    private StorageUtil mStorageUtil;
    private ThumbnailCreator mThumbnailCreator;

    private AbsListView mCurrentFileListView;
    private FileDataListAdapter mFileDataListAdapter;
    private FileDataGridAdapter mFileDataGridAdapter;
    private FavoriteDataAdapter mFavoriteDataAdapter;
    private ArrayList<FileItem> mCurrentFileContent;

    // mounted device paths
    private ArrayList<String> mMountedDevicePaths = new ArrayList<String>();

    // internal storage list adapter
    private DeviceDataAdapter mInternalStorageDataAdapter;

    // external SD card storage list adapter
    private DeviceDataAdapter mSdcardDataAdapter;

    // USB storage lists adapters
    private DeviceDataAdapter mUsb0StorageDataAdapter;
    private DeviceDataAdapter mUsb1StorageDataAdapter;
    private DeviceDataAdapter mUsb2StorageDataAdapter;
    private DeviceDataAdapter mUsb3StorageDataAdapter;
    private DeviceDataAdapter mUsb4StorageDataAdapter;
    private DeviceDataAdapter mUsb5StorageDataAdapter;

    private DeviceDataAdapter mStorageA_DataAdapter;
    private DeviceDataAdapter mStorageB_DataAdapter;
    private DeviceDataAdapter mStorageC_DataAdapter;
    private DeviceDataAdapter mStorageD_DataAdapter;
    private DeviceDataAdapter mStorageE_DataAdapter;
    private DeviceDataAdapter mStorageF_DataAdapter;

    private DeviceDataListener mDeviceDataListener;

    // For animate drag buddies bunch up to a stack or spread out from a stack
    private boolean mDragBuddiesPrepared = true;
    private boolean mDragBuddiesPreparing = false;
    private BunchToStackAnimation mCurrentBunchToStackAnimation;
    private final int MAX_LAYER_NUM_4_BUNCH_STACK = 6;
    private ActionModeDraggableView mCurrentDragStartPointView;
    private LinkedList<Object> mCurrentDragBuddies;
    private Object mCurrentDragStartPointData;
    private LinkedList<View> mCurrentVisibleDragBuddyViews;
    private boolean mCurrentDragBuddyViewsStacked = false;
    private LayerDrawable mDrawableForStackedDragBuddies;
    private ArrayList<DropAcceptable> mExtraFileDragListeners = new ArrayList<DropAcceptable>();

    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_UPDATE_CURRENT_DIR:
                updateDirectory(mFileManager.refreshUrlContent());
                break;
            case MSG_CURRENT_DIR_UPDATED:
                if (mUiCallback != null) {
                    mUiCallback.onUrlContentUpdated((String) msg.obj, msg.arg1,
                            (msg.arg2 == 1));
                }
                notifyFileContentChanged();
                break;
            case MSG_SEARCH_RESULT_UPDATED:
                updateFileManagerPatternPaths();
                long currentTimeMillis = System.currentTimeMillis();
                if (currentTimeMillis - mSearchResultLastUpdateTiming > 1500) {
                    mSearchResultLastUpdateTiming = currentTimeMillis;
                    updateSearchProgressDialog((String) msg.obj);
                    if (mCurrentFileContent != null
                            && mCurrentFileContent.size() < 64) {
                        updateDirectory(mFileManager.refreshUrlContent());
                    }
                }
                break;
            case MSG_SEARCH_PATH_UPDATED:
                long currentTimeMillis1 = System.currentTimeMillis();
                if (currentTimeMillis1 - mSearchResultLastUpdateTiming > UPDATE_INTERVAL_BASE) {
                    mSearchResultLastUpdateTiming = currentTimeMillis1;
                    updateSearchProgressDialog((String) msg.obj);
                }
                break;
            case MSG_SEARCH_FINISHED:
                hideSearchProgressDialog();
                updateFileManagerPatternPaths();
                updateDirectory(mFileManager.refreshUrlContent());
                break;
            case MSG_SEARCH_CANCELLED:
                hideSearchProgressDialog();
                updateDirectory(mFileManager.refreshUrlContent());
                break;
            case MSG_FILES_BUNCH_UP_TO_STACK:
                // Ready to drag
                break;
            case MSG_FILES_PREPARE_DRAGGING_VIEW:
                prepareCurrentDraggingView();
                break;
            case MSG_FILES_DRAG_READY:
                dragCurrentDragSources();
                break;
            default:
                return;
            }
        }
    };

    public IfAppController(Context context, final FilePathUrlManager manager) {
        mContext = context;
        mFileManager = manager;
        mFileManager.setFilePathObserver(this);
        mStorageUtil = StorageUtil.getSingleton(context);
        DataUtil.getReflectedUtilAPIs();

        mCachedAudioFilePaths = new ArrayList<String>();
        mCachedImageFilePaths = new ArrayList<String>();
        mCachedVideoFilePaths = new ArrayList<String>();
        mCachedApkFilePaths = new ArrayList<String>();
        // mCachedSearchFilePaths = new ArrayList<String>();
        mCachedSearchResult = new HashMap<String, ArrayList<String>>();

        mFileDataListAdapter = new FileDataListAdapter();
        mFileDataGridAdapter = new FileDataGridAdapter();
        mCurrentFileContent = new ArrayList<FileItem>();
        Collator.getInstance(DataUtil.getLocaleFromContext(mContext));

        mFavoriteDataAdapter = new FavoriteDataAdapter(mContext,
                new ArrayList<FavoriteItem>());

        mInternalStorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mInternalStorageDataAdapter.setIfTag(1);
        // root dir
        DeviceItem rootDir = new DeviceItem("/",
                mStorageUtil.getStorageDescription(R.string.root_dir),
                mStorageUtil.getStorageDescription(R.string.internal_storage),
                false, true, StorageUtil.Internal, StorageUtil.TYPE_ROOT);
        mInternalStorageDataAdapter.add(rootDir);

        mSdcardDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mSdcardDataAdapter.setIfTag(2);
        mUsb0StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb0StorageDataAdapter.setIfTag(3);
        mUsb1StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb1StorageDataAdapter.setIfTag(4);
        mUsb2StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb2StorageDataAdapter.setIfTag(5);
        mUsb3StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb3StorageDataAdapter.setIfTag(6);
        mUsb4StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb4StorageDataAdapter.setIfTag(7);
        mUsb5StorageDataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mUsb5StorageDataAdapter.setIfTag(8);
        mStorageA_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageA_DataAdapter.setIfTag(9);
        mStorageB_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageB_DataAdapter.setIfTag(10);
        mStorageC_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageC_DataAdapter.setIfTag(11);
        mStorageD_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageD_DataAdapter.setIfTag(12);
        mStorageE_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageE_DataAdapter.setIfTag(13);
        mStorageF_DataAdapter = new DeviceDataAdapter(mContext,
                new ArrayList<DeviceItem>());
        mStorageF_DataAdapter.setIfTag(14);
    }

    public void cancelAllBackgroundWork() {
        if (mBackgroundWorks != null) {
            for (AsyncTask<String, Void, ?> task : mBackgroundWorks) {
                if (task != null
                        && (task.getStatus() == AsyncTask.Status.RUNNING)) {
                    task.cancel(true);
                }
            }
        }
    }

    public void changeDirectory(String newDirPath) {
        File file = new File(newDirPath);

        if (file.isDirectory()) {
            if (file.canRead()) {
                stopThumbnailThread();
                updateDirectory(mFileManager.getNextUrlContent(newDirPath,
                        true, false));
                // When entering a folder, always focus on
                // the first item, for compatible with a device with 
                // keyboard/remote controller as input
                if (mCurrentFileListView != null) {
                    mCurrentFileListView.setSelection(0);
                }
            } else {
                Toast.makeText(mContext, R.string.read_fail_permission,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean checkDropDataAcceptable(Object dropData, View dropTarget) {
        if (dropTarget == null) {
            return false;
        }

        if (dropTarget instanceof ActionModeDraggableView) {
            Object data = ((ActionModeDraggableView) dropTarget)
                    .getDragContextInfo().data;
            if (data == null) {
                return false;
            } else {
                // Can not drop a source on itself
                if (dropData != null && dropData instanceof FileDropData) {
                    FileDropData fileDropData = (FileDropData) dropData;
                    if (fileDropData.dropData.contains(data)) {
                        Log.i(TAG,
                                "Drop target is contained in drag source, deny!");
                        return false;
                    }
                }
            }

            if (data instanceof FileItem) {
                FileItem fileItem = (FileItem) data;
                if (!fileItem.isDirectory() || fileItem.isReadOnly()
                        || fileItem.isReadWriteDeny()
                        || fileItem.isSystemFile()) {
                    return false;
                } else {
                    return true;
                }
            }
        } else if (dropTarget instanceof DropableGridView
                || dropTarget instanceof DropableListView) {
            if (dropTarget.equals(mCurrentFileListView)
                    && FileUtil.isWritableDirectory(mFileManager
                            .getCurrentUrl())) {
                Log.i(TAG, "Drop in current file list view.");
                return true;
            }
        } else if (dropTarget instanceof DropableFilePathButton) {
            String path = ((DropableFilePathButton) dropTarget).getFilePath();
            if (FileUtil.isWritableDirectory(path)) {
                return true;
            }
        } else if (dropTarget instanceof FilePathNavigator) {
            return false;
        }

        return false;
    }

    @Override
    public boolean checkOpenable(Openable openable) {
        if (openable instanceof ActionModeDraggableView) {
            Log.i(TAG, "check openable on an ActionModeDraggableView");
            ActionModeDraggableView draggableView = (ActionModeDraggableView) openable;
            DragContextInfo info = draggableView.getDragContextInfo();
            Object data = info.data;

            if (data.equals(mCurrentDragStartPointData)) {
                Log.i(TAG,
                        "Current drag start point can't be openable (because it's being dragged), skip!");
                return false;
            }

            if (data instanceof FileItem) {
                Log.i(TAG, "openable check on an view for FileItem.");
                final FileItem fileItem = (FileItem) data;
                if (FileUtil.isReadableDirectory(fileItem.getPath())) {
                    return true;
                } else {
                    return false;
                }
            }
        }

        return false;
    }

    @Override
    public void doOpen(Openable openable) {
        if (openable instanceof ActionModeDraggableView) {
            Log.i(TAG, "Open an ActionModeDraggableView");
            ActionModeDraggableView draggableView = (ActionModeDraggableView) openable;
            DragContextInfo dragInfo = draggableView.getDragContextInfo();
            Object data = dragInfo.data;

            if (data instanceof FileItem) {
                Log.i(TAG, "Open on an view for FileItem.");
                final FileItem fileItem = (FileItem) data;
                changeDirectory(fileItem.getPath());
            }
        } else if (openable instanceof DropableFilePathButton) {
            DropableFilePathButton pathButton = (DropableFilePathButton) openable;
            changeDirectory(pathButton.getFilePath());
        }
    }

    public LinkedList<Object> findCurrentDragBuddies(
            ActionModeDraggableView dragSource) {
        LinkedList<Object> dragBuddies = null;

        ViewParent viewParent = dragSource.getParent();
        if (viewParent != null && (viewParent instanceof AbsListView)) {
            AbsListView parentListView = (AbsListView) viewParent;
            ListAdapter adapter = parentListView.getAdapter();
            int checkedCount = parentListView.getCheckedItemCount();
            if (checkedCount <= 1) {
                return null;
            } else {
                dragBuddies = new LinkedList<Object>();
            }

            int dragBuddiesCount = checkedCount - 1;
            SparseBooleanArray items = parentListView.getCheckedItemPositions();

            int found = 0;
            for (int i = 0; i < parentListView.getCount(); i++) {
                if (items.get(i)) {
                    Object checkedItem = adapter.getItem(i);
                    View buddyView = parentListView.getChildAt(i);
                    boolean isBuddy = false;

                    if (buddyView == null) {
                        // Buddy with its view out of the screen
                        // definite not the start point
                        isBuddy = true;
                    } else {
                        // Could be the drag start point
                        isBuddy = (buddyView.hashCode() != dragSource
                                .hashCode());
                    }

                    if (isBuddy) {
                        synchronized (dragBuddies) {
                            dragBuddies.add(checkedItem);
                            found++;
                        }
                    }

                    if (found == dragBuddiesCount) {
                        break;
                    }
                }
            }
        }

        dragSource.setDragBuddies(dragBuddies);
        return dragBuddies;
    }

    public void forceRefreshFileListView() {
        mFileDataListAdapter.notifyDataSetChanged();
        mFileDataGridAdapter.notifyDataSetChanged();
    }

    @Override
    public DragShadowBuilder generateDragShadow(
            ActionModeDraggableView dragSource) {
        if (mCurrentDragBuddies == null) {
            return new DragShadowBuilder(dragSource);
        } else {
            // Get dragging image view for generating drag shadow
            ImageView draggingView = dragSource.getDragContextInfo().draggingView;

            return new DragShadowBuilder((draggingView != null) ? draggingView
                    : dragSource);
        }
    }

    @Override
    public Object generateDropData() {
        /*
         *  Default data structure for drag data, the same type to parameter 'choices' of 
         *
         * IfExplorer.MultiChoiceConsumer::consumeChoice(HashSet<Object> choices,
         *              MultiChoiceConsumeOptions options)
         */
        if (mCurrentDragStartPointData != null) {
            if (mCurrentDragStartPointData instanceof FileItem) {
                Log.i(TAG, "Generate drop data for file dragging.");

                HashSet<Object> fileItems = new HashSet<Object>();
                fileItems.add(mCurrentDragStartPointData);
                if (mCurrentDragBuddies != null) {
                    synchronized (mCurrentDragBuddies) {
                        for (Object buddy : mCurrentDragBuddies) {
                            if (buddy instanceof FileItem) {
                                fileItems.add(buddy);
                            }
                        }
                    }
                }

                String currentPath = ((FileItem) mCurrentDragStartPointData)
                        .getPath();
                String parentDirPath = FileUtil.getParentDirPath(currentPath);

                Log.i(TAG, "Parent directory path is: " + parentDirPath
                        + " for current: " + currentPath);
                FileDropData dropData = new FileDropData(parentDirPath,
                        fileItems);

                return dropData;
            } // else if
              // other stuff TBD
        }

        return null;
    }

    public DeviceDataAdapter getApproximateDeviceAdapter(int position) {
        switch (position) {
        case StorageUtil.Internal:
            return mInternalStorageDataAdapter;
        case StorageUtil.SDSlot:
            return mSdcardDataAdapter;
        case StorageUtil.USBPort0:
            return mUsb0StorageDataAdapter;
        case StorageUtil.USBPort1:
            return mUsb1StorageDataAdapter;
        case StorageUtil.USBPort2:
            return mUsb2StorageDataAdapter;
        case StorageUtil.USBPort3:
            return mUsb3StorageDataAdapter;
        case StorageUtil.USBPort4:
            return mUsb4StorageDataAdapter;
        case StorageUtil.USBPort5:
            return mUsb5StorageDataAdapter;
        case StorageUtil.USBSDA:
            return mStorageA_DataAdapter;
        case StorageUtil.USBSDB:
            return mStorageB_DataAdapter;
        case StorageUtil.USBSDC:
            return mStorageC_DataAdapter;
        case StorageUtil.USBSDD:
            return mStorageD_DataAdapter;
        case StorageUtil.USBSDE:
            return mStorageE_DataAdapter;
        case StorageUtil.USBSDF:
            return mStorageF_DataAdapter;
        default:
            return null;
        }
    }

    public ArrayList<String> getCachedApkFilePaths() {
        return mCachedApkFilePaths;
    }

    public ArrayList<String> getCachedAudioFilePaths() {
        return mCachedAudioFilePaths;
    }

    public HashMap<String, ArrayList<String>> getCachedSearchResult() {
        return mCachedSearchResult;
    }

    public ArrayList<String> getCachedVideoFilePaths() {
        return mCachedVideoFilePaths;
    }

    public ActionModeDraggableView getCurrentBunchedDestView() {
        return mCurrentDragStartPointView;
    }

    public ArrayList<String> getCurrentMountedFileSystems() {
        ArrayList<String> result = new ArrayList<String>();
        // root file system
        result.add("/");

        if (mStorageUtil != null) {
            ArrayList<String> storageVolumePaths = mStorageUtil
                    .getMountedStorageVolumePaths();
            if (storageVolumePaths != null) {
                result.addAll(storageVolumePaths);
            }
        }

        return result;
    }

    public DeviceDataAdapter getDeviceDataAdapter(int storagePosition) {
        switch (storagePosition) {
        case StorageUtil.Internal:
            return mInternalStorageDataAdapter;
        case StorageUtil.SDSlot:
            return mSdcardDataAdapter;
        case StorageUtil.USBPort0:
            return mUsb0StorageDataAdapter;
        case StorageUtil.USBPort1:
            return mUsb1StorageDataAdapter;
        case StorageUtil.USBPort2:
            return mUsb2StorageDataAdapter;
        case StorageUtil.USBPort3:
            return mUsb3StorageDataAdapter;
        case StorageUtil.USBPort4:
            return mUsb4StorageDataAdapter;
        case StorageUtil.USBPort5:
            return mUsb5StorageDataAdapter;
        case StorageUtil.USBSDA:
            return mStorageA_DataAdapter;
        case StorageUtil.USBSDB:
            return mStorageB_DataAdapter;
        case StorageUtil.USBSDC:
            return mStorageC_DataAdapter;
        case StorageUtil.USBSDD:
            return mStorageD_DataAdapter;
        case StorageUtil.USBSDE:
            return mStorageE_DataAdapter;
        case StorageUtil.USBSDF:
            return mStorageF_DataAdapter;
        default:
            return null;
        }
    }

    public DeviceDataListener getDeviceDataListener() {
        return mDeviceDataListener;
    }

    public FavoriteDataAdapter getFavoriteDataAdapter() {
        return mFavoriteDataAdapter;
    }

    public FileDataGridAdapter getFileDataGridAdapter() {
        return mFileDataGridAdapter;
    }

    public FileDataListAdapter getFileDataListAdapter() {
        return mFileDataListAdapter;
    }

    public FilePathUrlManager getFileManager() {
        return mFileManager;
    }

    @Override
    public boolean handleDrop(Object dropData, View dropTarget) {
        if (dropData == null || dropTarget == null
                || !(dropData instanceof FileDropData)) {
            return false;
        }

        // Make use of ClipHistoryAdapter
        if (mContext instanceof IfExplorer) {
            if (dropTarget instanceof ActionModeDraggableView) {
                Object accepterData = ((ActionModeDraggableView) dropTarget)
                        .getDragContextInfo().data;
                if (accepterData != null && accepterData instanceof FileItem) {
                    Log.i(TAG, "Drop on the current directory: "
                            + ((FileItem) accepterData).getPath());

                    handleFileMoveAsMultiChoiceConsume((FileDropData) dropData,
                            ((FileItem) accepterData).getPath());
                    return true;
                }
            } else if (dropTarget instanceof DropableGridView
                    || dropTarget instanceof DropableListView) {
                Log.i(TAG, "Drop on the file grid instead of a file item.");
                revertDragPreparation(mCurrentDragStartPointView);
                if (dropTarget.equals(mCurrentFileListView)) {
                    String moveTargetPath = mFileManager.getCurrentUrl();

                    FileDropData fileDropData = (FileDropData) dropData;
                    if (fileDropData != null
                            && !moveTargetPath.equals(fileDropData.fromDirPath)
                            && FileUtil.isExistingFilePath(moveTargetPath)) {
                        Log.i(TAG, "Move to a different valid directory: "
                                + moveTargetPath);
                        handleFileMoveAsMultiChoiceConsume(
                                (FileDropData) dropData, moveTargetPath);
                    } else {
                        Log.i(TAG,
                                "Drop in the same directory or in an invalid directory");
                    }
                }
                return true;
            } else if (dropTarget instanceof DropableFilePathButton) {
                Log.i(TAG,
                        "Drop on the file path button item instead of a directory item.");
                revertDragPreparation(mCurrentDragStartPointView);
                String moveTargetPath = ((DropableFilePathButton) dropTarget)
                        .getFilePath();

                FileDropData fileDropData = (FileDropData) dropData;
                if (!fileDropData.fromDirPath.equals(moveTargetPath)
                        && FileUtil.isExistingFilePath(moveTargetPath)) {
                    Log.i(TAG, "Move to a different valid directory: "
                            + moveTargetPath);
                    handleFileMoveAsMultiChoiceConsume((FileDropData) dropData,
                            moveTargetPath);
                } else {
                    Log.i(TAG,
                            "Drop in the same directory or in an invalid directory");
                }
            }
        }

        return false;
    }

    public void handleFileMoveAsMultiChoiceConsume(FileDropData fileDropData,
            String moveTargetPath) {
        if (fileDropData == null && moveTargetPath == null) {
            return;
        }

        HashSet<Object> fileItems = fileDropData.dropData;
        ClipHistoryAdapter clipHistoryAdapter = ((IfExplorer) mContext)
                .getClipHistoryAdapter();

        if (clipHistoryAdapter != null) {
            // Mark
            Log.i(TAG, "Mark data to be cut.");
            MultiChoiceConsumeOptions cutOptions = new MultiChoiceConsumeOptions(
                    ClipHistoryAdapter.CLIP_CUT);
            clipHistoryAdapter.consumeChoice(fileItems, cutOptions);
            ActionMode activatingAm = ((IfExplorer) mContext)
                    .getActivatingActionMode();
            if (activatingAm != null) {
                activatingAm.finish();
            }

            // Execute
            Log.i(TAG, "Cut data to places.");
            int count = clipHistoryAdapter.getCount();
            if (count >= 1) {
                ClipItemAdapter adapter = clipHistoryAdapter
                        .getItem(clipHistoryAdapter.getCount() - 1);
                if (adapter != null) {
                    HashSet<FileClip> toPaste = adapter.prepare();
                    if (toPaste.size() > 0) {
                        adapter.pasteFileClip(toPaste, moveTargetPath);
                    }
                }
            }
        }
    }

    public boolean hasActionModeBunchedViews() {
        return mCurrentDragBuddyViewsStacked;
    }

    /** called to update the favorite contents */
    public void initializeDefaultFavorites() {
        if (!mFavoriteDataAdapter.isEmpty()) {
            mFavoriteDataAdapter.clear();
        }

        // kevin: TODO: implement as user configured
        FavoriteItem favorite_music = new FavoriteItem(
                FilePathUrlManager.SMART_MUSIC, mContext.getResources()
                        .getString(R.string.music), StorageUtil.TYPE_AUDIO,
                R.drawable.type_music);
        mFavoriteDataAdapter.add(favorite_music);

        FavoriteItem favorite_videos = new FavoriteItem(
                FilePathUrlManager.SMART_MOVIES, mContext.getResources()
                        .getString(R.string.videos), StorageUtil.TYPE_VIDEO,
                R.drawable.type_movie);
        mFavoriteDataAdapter.add(favorite_videos);

        FavoriteItem favorite_apks = new FavoriteItem(
                FilePathUrlManager.SMART_APK, mContext.getResources()
                        .getString(R.string.apkInstaller),
                StorageUtil.TYPE_APKFILE, R.drawable.type_apk);
        mFavoriteDataAdapter.add(favorite_apks);

        mFavoriteDataAdapter.notifyDataSetChanged();
    }

    @Override
    public void interruptDragPreparation(
            final ActionModeDraggableView dragSource) {
        Log.i(TAG, "Interrupt drag preparation!");
        if (mCurrentBunchToStackAnimation != null) {
            mCurrentBunchToStackAnimation.cancel();
        }

        mDragBuddiesPreparing = false;
    }

    public void intializeStorageDevicesAsync() {
        StorageDeviceItemGenerator task = new StorageDeviceItemGenerator(
                GEN_ALL_DEVICE_ITEM);
        mBackgroundWorks.add(task);
        task.execute("");
    }

    @Override
    public boolean isDragOpDelegated(final ActionModeDraggableView dragSource) {
        if (dragSource.isDragReady()) {
            // Drag ready, no asynchronous preparation steps are needed,
            // so we let the drag source do the drag by itself
            return false;
        } else {
            // If the drag source is not ready to drag and it has buddies,
            // then we will prepare its buddies for drag, including animating buddies bunch up to a stack.
            // Only when the animation finished can we start the drag,
            // so we delegate the drag operation.
            if (dragSource.getDragBuddies() != null) {
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public boolean isDragPrepared(final ActionModeDraggableView dragSource) {
        if (mDragBuddiesPrepared) {
            Log.i(TAG, "Preparing!");
            return true;
        } else {
            Log.i(TAG, "Not preparing!");
            return false;
        }
    }

    @Override
    public void navigateToPath(String path) {
        updateDirectory(mFileManager.getNextUrlContent(path, true, false));
    }

    @Override
    public void notifyAutoDragStarted(ActionModeDraggableView dragSource) {
        mCurrentDragStartPointView = dragSource;
        displayCurrentDragStartPoint(false);
        // Finish action mode
        ActionMode activatingAm = ((IfExplorer) mContext)
                .getActivatingActionMode();
        if (activatingAm != null) {
            activatingAm.finish();
        }
        // Notify extra listeners a drag started
        notifyFileDragStartedForExtraListener();
    }

    @Override
    public void notifyDropDataDenied(Object dropData, View dropTarget) {
        Log.i(TAG, "notifyDropDataDenied");
        if (dropData instanceof FileDropData) {
            FileDropData fileDropData = (FileDropData) dropData;
            if (mCurrentFileListView.equals(dropTarget)) {
                promptMoveFailureDueToPermission(fileDropData.fromDirPath,
                        mFileManager.getCurrentUrl());
            } else if (dropTarget instanceof ActionModeDraggableView) {
                Object data = ((ActionModeDraggableView) dropTarget)
                        .getDragContextInfo().data;
                if (data instanceof FileItem) {
                    promptMoveFailureDueToPermission(fileDropData.fromDirPath,
                            ((FileItem) data).getPath());
                }
            }
        }
    }

    // implement
    // gem.kevin.filmanager.FileManager.FilePathObserver::notifyOnAllEvents
    @Override
    public void notifyOnAllEvents(String path) {
        mUiHandler.sendEmptyMessage(MSG_UPDATE_CURRENT_DIR);
    }

    @Override
    public void onDropFailed(View dropTarget) {
        revertDragPreparation(mCurrentDragStartPointView);
    }

    @Override
    public void onDropSuccess(View dropTarget) {
        // do nothing
    }

    @Override
    public void onHover(View hoverTarget) {
        if (hoverTarget instanceof Openable) {
            final Openable openableTarget = (Openable) hoverTarget;

            AlphaAnimation flickingAnim = new AlphaAnimation(0.1f, 1.0f);
            flickingAnim.setAnimationListener(new AnimationListener() {
                @Override
                public void onAnimationEnd(Animation animation) {
                    // After flicking, open the target and revert drag preparation
                    openableTarget.open();
                    revertDragPreparation(mCurrentDragStartPointView);
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                    // TODO Auto-generated method stub
                }

                @Override
                public void onAnimationStart(Animation animation) {
                    // TODO Auto-generated method stub
                }
            });

            flickingAnim.setDuration(200);
            hoverTarget.startAnimation(flickingAnim);

        }
    }

    @Override
    public void onSearchOpTaskCancelled(FileOperationTask task) {
        Message msg = mUiHandler.obtainMessage(MSG_SEARCH_CANCELLED);
        mUiHandler.sendMessage(msg);
    }

    @Override
    public void onSearchOpTaskFinished(FileOperationTask task) {
        Message msg = mUiHandler.obtainMessage(MSG_SEARCH_FINISHED);
        mUiHandler.sendMessage(msg);

        Log.i(TAG, "onSearchOpTaskFinished");
        int searchType = task.getOptions().searchType;
        switch (searchType) {
        case StorageUtil.TYPE_APKFILE:
            mApkFilesCached = true;
            break;
        case StorageUtil.TYPE_AUDIO:
            mAudioCached = true;
            break;
        case StorageUtil.TYPE_IMAGE:
            mImageCached = true;
            break;
        case StorageUtil.TYPE_VIDEO:
            mVideoCached = true;
            break;
        }
    }

    @Override
    public void onSearchOpTaskProgressing(FileOperationTask task,
            String searchDirPath, boolean dirFinished) {
        Message msg;
        if (dirFinished) {
            msg = mUiHandler.obtainMessage(MSG_SEARCH_RESULT_UPDATED);
            msg.obj = searchDirPath;

            mUiHandler.sendMessage(msg);
        } else {
            msg = mUiHandler.obtainMessage(MSG_SEARCH_PATH_UPDATED);
            msg.obj = searchDirPath;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onSpreadAnimationCanceled(View animView) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onSpreadAnimationFinished(View animView) {
        // Since the view ends animating spread out from a stack
        // it should be 'back' its original position
    }

    @Override
    public void onSpreadAnimationStart(View animView) {
        Log.i(TAG, "Spread animation START.");
        // 'Split' the stacked views
        mDrawableForStackedDragBuddies = null;
        if (mCurrentDragStartPointView != null) {
            splitCurrentDragBuddies();
            mCurrentDragStartPointView.setDragReady(false);
        }
    }

    @Override
    public void onStackAnimationCanceled(View animView) {
        // TODO Auto-generated method stub
        Log.i(TAG, "Stack animation CANCELED.");
        if (mCurrentBunchToStackAnimation.isCanceled()) {
            Log.i(TAG, "All stack animations canceled.");
            if (mCurrentDragStartPointView != null) {
                Log.i(TAG,
                        "Revert drag prepartion for stack animation canceled.");
                revertDragPreparation(mCurrentDragStartPointView);

                // Drag preparation is canceled
                mDragBuddiesPreparing = false;
                mDragBuddiesPrepared = false;
            }
        } else {
            Log.i(TAG, "Not all stack animations canceled yet.");
        }
    }

    @Override
    public void onStackAnimationFinished(View animView) {
        Log.i(TAG, "Stack animation FINISHED.");
        if (mCurrentBunchToStackAnimation.isFinished()) {
            Log.i(TAG, "All stack animations finished.");
            if (mDrawableForStackedDragBuddies != null
                    && mCurrentDragStartPointView != null) {
                stackCurrentDragBuddies();

                // Drag preparation is finished
                mDragBuddiesPreparing = false;
            }
        } else {
            Log.i(TAG, "Not all stack animations finished yet.");
        }
    }

    @Override
    public void onStackAnimationStart(View animView) {
        Log.i(TAG, "onStackAnimationStart");
    }

    @Override
    public void prepareDrag(final ActionModeDraggableView dragSource) {
        Log.i(TAG, "Prepare drag!");
        mCurrentDragStartPointData = dragSource.getDragContextInfo().data;
        mCurrentDragBuddies = findCurrentDragBuddies(dragSource);
        if (mCurrentDragBuddies != null) {
            mDragBuddiesPrepared = false;
            mDragBuddiesPreparing = true;

            mCurrentVisibleDragBuddyViews = getCurrentVisibleViewsForDragBuddies(mCurrentDragBuddies);

            // Generate a stacked drawable could be time-consuming,
            // so we generate it before the stack animation then show it
            // after the animation
            // TODO: May use an asynchronous way to generate the stacked view
            if (dragSource.getDragContextInfo().draggingView != null) {
                mDrawableForStackedDragBuddies = generateDrawableForStackedFiles(
                        mCurrentVisibleDragBuddyViews, dragSource);
            }

            // Do animation only if the stacked view is generated successfully
            if (mDrawableForStackedDragBuddies != null) {
                if (mCurrentBunchToStackAnimation == null) {
                    mCurrentBunchToStackAnimation = new BunchToStackAnimation(
                            mContext, false);
                    mCurrentBunchToStackAnimation
                            .setBunchAnimationListener(this);
                }

                mCurrentBunchToStackAnimation.clearAnimViews();
                mCurrentDragStartPointView = dragSource;
                for (View toStackView : mCurrentVisibleDragBuddyViews) {
                    mCurrentBunchToStackAnimation.addAnimView(toStackView);
                }

                mCurrentBunchToStackAnimation.animateBunchUpToPoint(
                        dragSource.getLeft(), dragSource.getTop());

                // Hide buddies because they have 'left' their position
                // and moving toward the bunch destination
                displayCurrentDragBuddies(false);
            }
        }
    }

    public void resetCachedFilePaths() {
        Log.i(TAG, "Reset cached file paths.");
        if (!mCachedAudioFilePaths.isEmpty()) {
            mCachedAudioFilePaths.clear();
        }
        if (!mCachedImageFilePaths.isEmpty()) {
            mCachedImageFilePaths.clear();
        }
        if (!mCachedVideoFilePaths.isEmpty()) {
            mCachedVideoFilePaths.clear();
        }
        if (!mCachedApkFilePaths.isEmpty()) {
            mCachedApkFilePaths.clear();
        }
        /*
         * if (!mCachedSearchFilePaths.isEmpty()) {
         * mCachedSearchFilePaths.clear(); }
         */
        if (!mCachedSearchResult.isEmpty()) {
            mCachedSearchResult.clear();
        }

        mAudioCached = false;
        mImageCached = false;
        mVideoCached = false;
        mApkFilesCached = false;
        updateFileManagerPatternPaths();
    }

    @Override
    public void revertDragPreparation(final ActionModeDraggableView dragSource) {
        Log.i(TAG, "Revert drag preparation!");
        splitCurrentDragBuddies();
        dragSource.setDragReady(false);

        // 'Put back' the drag start point
        displayCurrentDragStartPoint(true);
        // 'Put back the drag buddies
        displayCurrentDragBuddies(true);

        Log.i(TAG, "After revert!");
    }

    public void searchFiles(int searchType, String searchStr, String searchDir,
            int searchDepth, boolean silent) {
        boolean cached = false;
        switch (searchType) {
        case StorageUtil.TYPE_AUDIO:
            cached = mAudioCached;
            break;
        case StorageUtil.TYPE_VIDEO:
            cached = mVideoCached;
            break;
        case StorageUtil.TYPE_IMAGE:
            cached = mImageCached;
            break;
        case StorageUtil.TYPE_APKFILE:
            cached = mApkFilesCached;
            break;
        }

        if (cached) {
            // updateDirectory(mFileManager.refreshUrlContent());
            return;
        } else {
            Log.i(TAG, "Not cached.");
        }

        HashMap<String, Object> searchParams = new HashMap<String, Object>();
        searchParams.put(FileOperationOptions.KEY_SEARCH_DIR, searchDir);
        searchParams.put(FileOperationOptions.KEY_SEARCH_RESEVERD_DIR, null);
        searchParams.put(FileOperationOptions.KEY_SEARCH_DEPTH,
                Integer.valueOf(searchDepth));
        searchParams.put(FileOperationOptions.KEY_SEARCH_RECORD_LIMIT,
                IfConfig.MAX_LISTITEM_COUNT);
        switch (searchType) {
        case StorageUtil.TYPE_AUDIO:
            mCachedAudioFilePaths.clear();
            searchParams.put(FileOperationOptions.KEY_SEARCH_SCOPE,
                    DataUtil.getSupportedAudioFileExtensions());
            searchParams.put(FileOperationOptions.KEY_SEARCH_OUTPUT,
                    mCachedAudioFilePaths);
            break;
        case StorageUtil.TYPE_VIDEO:
            mCachedVideoFilePaths.clear();
            searchParams.put(FileOperationOptions.KEY_SEARCH_SCOPE,
                    DataUtil.getSupportedVideoFileExtensions());
            searchParams.put(FileOperationOptions.KEY_SEARCH_OUTPUT,
                    mCachedVideoFilePaths);
            break;
        case StorageUtil.TYPE_IMAGE:
            mCachedImageFilePaths.clear();
            searchParams.put(FileOperationOptions.KEY_SEARCH_SCOPE,
                    DataUtil.getSupportedImageFileExtensions());
            searchParams.put(FileOperationOptions.KEY_SEARCH_OUTPUT,
                    mCachedImageFilePaths);
            break;
        case StorageUtil.TYPE_APKFILE:
            mCachedApkFilePaths.clear();
            searchParams.put(FileOperationOptions.KEY_SEARCH_SCOPE,
                    DataUtil.getSupportedAppInstallerFileExtensions());
            searchParams.put(FileOperationOptions.KEY_SEARCH_OUTPUT,
                    mCachedApkFilePaths);
            break;
        case StorageUtil.TYPE_FILE:
            String searchId = FilePathUrlManager.buildSearchId(searchStr,
                    searchDir);
            ArrayList<String> searchResult = mCachedSearchResult.get(searchId);
            if (searchResult != null) {
                Log.i(TAG, "Ever found.");
                searchResult.clear();
            } else {
                searchResult = new ArrayList<String>();
                mCachedSearchResult.put(searchId, searchResult);
            }

            searchParams.put(FileOperationOptions.KEY_SEARCH_SCOPE, null);
            searchParams.put(FileOperationOptions.KEY_SEARCH_OUTPUT,
                    searchResult);
            searchParams.put(FileOperationOptions.KEY_SEARCH_DEPTH,
                    Integer.valueOf(searchDepth) + 100);
            break;
        default: // do nothing if search request is not supported
            return;
        }

        FileOperationOptions options = FileOperationTask
                .makeDefaultOptions(FileOperationTask.OP_TYPE_SEARCH_FILE);
        options.setSearchParams(searchParams);
        options.setSearchType(searchType);
        ArrayList<String> searchTargets = new ArrayList<String>();
        searchTargets.add(searchStr);

        Time time = new Time();
        time.setToNow();
        String taskId = String.format("%02d:%02d:%02d-search", time.hour,
                time.minute, time.second);
        final FileOperationTask searchTask = new FileOperationTask(mContext,
                taskId, options, searchTargets, this);
        searchTask.start();
        mSearchResultLastUpdateTiming = System.currentTimeMillis();
        if (!silent) {
            showSearchProgressDialog(searchDir, searchStr, searchType,
                    searchTask);
        }

        updateDirectory(mFileManager.refreshUrlContent());
    }

    public void setCachedApkFilePaths(ArrayList<String> cachedApkFilePaths) {
        mCachedApkFilePaths = cachedApkFilePaths;
    }

    public void setCachedAudioFilePaths(ArrayList<String> cachedAudioFilePaths) {
        mCachedAudioFilePaths = cachedAudioFilePaths;
    }

    public void setCachedSearchResult(HashMap<String, ArrayList<String>> result) {
        mCachedSearchResult = result;
    }

    public void setCachedVideoFilePaths(ArrayList<String> cachedVideoFilePaths) {
        mCachedVideoFilePaths = cachedVideoFilePaths;
    }

    public void setCurrentFileListView(AbsListView absListView) {
        mCurrentFileListView = absListView;
    }

    public void setDeviceDataListener(DeviceDataListener listener) {
        mDeviceDataListener = listener;
    }

    public void addExtraFileDragStartedListener(DropAcceptable dropAcceptable) {
        mExtraFileDragListeners.add(dropAcceptable);
    }

    public void setPathMode(boolean absolute) {
    }

    public void setUiCallback(UiCallback callback) {
        mUiCallback = callback;
    }

    @Override
    public boolean shouldDispathUnacceptableDropToParent(View dropTarget) {
        if (dropTarget instanceof ActionModeDraggableView
                && dropTarget.getParent() != null
                && (dropTarget.getParent() instanceof DropableGridView || dropTarget
                        .getParent() instanceof DropableListView)) {
            return false;
        } else if (dropTarget instanceof DropableGridView) {
            return false;
        } else if (dropTarget instanceof DropableListView) {
            return false;
        } else if (dropTarget instanceof DropableFilePathButton) {
            return false;
        } else if (dropTarget instanceof FilePathNavigator) {
            return false;
        } else {
            return false;
        }
    }

    public void splitActionModeBunchedViews() {
        splitCurrentDragBuddies();
    }

    /** this will stop our background thread that creates thumb nail icons if the
     * thread is running. this should be stopped when ever we leave the folder
     * the image files are in. */
    public void stopThumbnailThread() {
        if (mThumbnailCreator != null) {
            mThumbnailCreator.setCancelThumbnails(true);
            mThumbnailCreator = null;
        }
    }

    public void updateDirectory(ArrayList<String> fileContent) {
        updateDirectory(fileContent, false);
    }

    public void updateDirectory(ArrayList<String> fileContent,
            boolean fromHistory) {
        // TODO: too much work on main thread, refactor to working thread
        Log.i(TAG, "update directory");
        clearFileContent();
        if (fileContent == null) {
            notifyFileContentChanged();
            return;
        }

        FileContentUpdateProcedure updateProcedure = new FileContentUpdateProcedure(
                fileContent, fromHistory);
        // Run work thread
        // new Thread(updateProcedure).start();
        // RUn on main thread
        updateProcedure.run();
    }

    public void updateFileManagerPatternPaths() {
        if (mFileManager != null) {
            mFileManager.setAudioPatternPath(mCachedAudioFilePaths);
            mFileManager.setVideoPatternPath(mCachedVideoFilePaths);
            mFileManager.setImagePatternPath(mCachedImageFilePaths);
            mFileManager.setApkPatternPath(mCachedApkFilePaths);
            mFileManager.setSearchFilePatternPath(mCachedSearchResult);
        }
    }

    public void updateStorageDevice(String mountPoint, String newState,
            boolean async) {
        int mountPort = StorageUtil.getMountPort(mountPoint);
        DeviceDataAdapter adapter = getApproximateDeviceAdapter(mountPort);
        if (adapter == null) {
            Log.e(TAG, "Can't get adapter for path: " + mountPoint);
            return;
        }

        if (newState.equals(Environment.MEDIA_MOUNTED)) {
            if (async) {
                StorageDeviceItemGenerator task = new StorageDeviceItemGenerator(
                        GEN_SINGLE_DISK_DEVICE_ITEM);
                mBackgroundWorks.add(task);
                task.execute(mountPoint);
            } else {
                // These operations are time-consuming
                // Be careful to run them in main thread
                IfStorageVolume volume = mStorageUtil
                        .getIfStorageVolumeFromPath(mountPoint);
                DeviceItem newDeviceItem = createDeviceItemFromIfStorageVolume(volume);
                adapter.add(newDeviceItem);
                adapter.notifyDataSetChanged();
                getDeviceDataListener().onStorageDeviceItemChanged();
            }
        } else if (newState.equals(Environment.MEDIA_UNMOUNTED)) {
            for (int i = 0; i < adapter.getCount(); i++) {
                DeviceItem target = adapter.getItem(i);
                String path = target.getPath();
                if (path.equals(mountPoint)) {
                    mMountedDevicePaths.remove(path);
                    adapter.remove(target);
                    adapter.notifyDataSetChanged();
                    mDeviceDataListener.onStorageDeviceItemChanged();
                    break;
                }
            }
        } else if (newState.equals(Environment.MEDIA_BAD_REMOVAL)
                || newState.equals(Environment.MEDIA_REMOVED)) {
            if (!adapter.isEmpty()) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    String path = adapter.getItem(i).getPath();
                    mMountedDevicePaths.remove(path);
                }
                adapter.clear();
                adapter.notifyDataSetChanged();
                mDeviceDataListener.onStorageDeviceItemChanged();
            }
        }
    }

    public void updateStorageDevices(boolean async) {

    }

    private void appendFileItem(String path) {
        synchronized (mCurrentFileContent) {
            FileItem fileItem = new FileItem(mContext, path);
            mCurrentFileContent.add(fileItem);
        }
    }

    @SuppressWarnings("unchecked")
    private void sortFileItems(int sortType) {
        if (sortType == mFileManager.getPreSortType()) {
            // Sort type is the same to pre sort by file maanger,
            // no need to sort again
            return;
        } else {
            @SuppressWarnings("rawtypes")
            Comparator sortComparator;
            switch (sortType) {
            case SORT_TYPE:
                sortComparator = mTypeComparator;
                break;
            default:
                sortComparator = null;
            }

            if (sortComparator != null) {
                synchronized (mCurrentFileContent) {
                    Object[] array = mCurrentFileContent.toArray();
                    mCurrentFileContent.clear();

                    Arrays.sort(array, sortComparator);

                    for (Object a : array) {
                        mCurrentFileContent.add((FileItem) a);
                    }
                }
            }
        }
    }

    private void clearFileContent() {
        synchronized (mCurrentFileContent) {
            if (!mCurrentFileContent.isEmpty()) {
                mCurrentFileContent.clear();
            }
        }
    }

    private DeviceItem createDeviceItemFromIfStorageVolume(
            IfStorageVolume volume) {
        if (volume == null) {
            return null;
        }

        String mountPoint = volume.getPath();
        boolean removable = volume.isRemovable();
        boolean emulated = volume.isEmulated();
        boolean mounted = mStorageUtil.isMounted(mountPoint);
        if (!mounted) {
            // Currently doesn't support mount operation on single volume,
            // so no need to show unmounted volumes
            return null;
        }

        int mountPort = StorageUtil.getMountPort(mountPoint);
        int type;
        switch (mountPort) {
        case StorageUtil.Internal:
            type = StorageUtil.TYPE_HOME;
            break;
        case StorageUtil.SDSlot:
            type = StorageUtil.TYPE_SDCARD;
            break;
        default:
            type = StorageUtil.TYPE_UDISK;
        }

        String volumeLabel = null;
        if (IfConfig.PRODUCT_SUPPORT_KEVIN_VOLD) {
            volumeLabel = mStorageUtil.readVolIdForPath(mountPoint,
                    StorageUtil.VOLID_TAG_LABEL);
        }

        if (volumeLabel == null) {
            if (emulated) {
                volumeLabel = mStorageUtil
                        .getStorageDescription(R.string.home_dir);
            } else {
                if (mountPort == StorageUtil.Internal
                        && IfConfig.PRODUCT_ROCK_CHIP) {
                    volumeLabel = mContext.getResources().getString(
                            R.string.home_dir);
                } else {
                    volumeLabel = String.format("%s (%s)", mStorageUtil
                            .getStorageDescription(R.string.removable_disk),
                            FileUtil.getFileName(mountPoint));
                }
            }
        }

        String diskName = null;
        if (IfConfig.PRODUCT_SUPPORT_KEVIN_VOLD) {
            diskName = mStorageUtil.readDiskNameForPath(mountPoint);
        }

        final DeviceItem deviceItem = new DeviceItem(mountPoint,
                (volumeLabel != null) ? volumeLabel : mStorageUtil
                        .getStorageDescription(emulated ? R.string.home_dir
                                : R.string.removable_disk),
                (diskName != null && !diskName.isEmpty()) ? diskName : null,
                removable, mounted, mountPort, type);

        if (tempLOG) {
            Log.i("@temp",
                    "createDeviceItemFromIfStorageVolume --- get device item for path: "
                            + mountPoint);
        }

        return deviceItem;
    }

    private void displayCurrentDragBuddies(boolean show) {
        /* 
         * If the views start animating bunch up to a stack
         * they should have 'left' their original positions
         * But we can't demonstrate the 'left' behavior
         * by setting visibility of the views
         * 
         * Because these views are AdapterView used in a AbsListView,
         * they could be reused for different items. In other words,
         * a view is not exactly correspond to FileItem, but a view
         * could be used by several FileItems.
         *
         * So, we mark the exact data object to be 'dragged' from
         * its original place and let the mark decide the visibility of
         * its view
         */
        if (mCurrentDragStartPointView != null) {
            ViewParent parentView = mCurrentDragStartPointView.getParent();
            if (parentView instanceof AbsListView
                    && mCurrentDragBuddies != null) {
                synchronized (mCurrentDragBuddies) {
                    for (Object buddy : mCurrentDragBuddies) {
                        if (buddy instanceof FileItem) {
                            ((FileItem) buddy).setVisible(show);
                        }
                    }
                }
                ListAdapter adapter = ((AbsListView) parentView).getAdapter();
                if (adapter instanceof FileDataGridAdapter) {
                    ((FileDataGridAdapter) adapter).notifyDataSetChanged();
                } else if (adapter instanceof FileDataListAdapter) {
                    ((FileDataListAdapter) adapter).notifyDataSetChanged();
                }
            }
        }
    }

    private void displayCurrentDragStartPoint(boolean show) {
        Log.i(TAG, "displayCurrentDragStartPoint: " + show);
        if (mCurrentDragStartPointView != null) {
            ViewParent parentView = mCurrentDragStartPointView.getParent();
            if (parentView instanceof AbsListView) {
                ListAdapter adapter = ((AbsListView) parentView).getAdapter();
                Object tag = mCurrentDragStartPointView.getTag();
                if (tag instanceof FileItemViewHolder) {
                    int position = ((FileItemViewHolder) tag).position;
                    if (position >= 0) {
                        Object data = adapter.getItem(position);
                        if (data instanceof FileItem) {
                            ((FileItem) data).setVisible(show);
                        }
                    }

                    if (adapter instanceof FileDataGridAdapter) {
                        ((FileDataGridAdapter) adapter).notifyDataSetChanged();
                    } else if (adapter instanceof FileDataListAdapter) {
                        ((FileDataListAdapter) adapter).notifyDataSetChanged();
                    }
                }
            }
        }
    }

    private void dragCurrentDragSources() {
        mDragBuddiesPrepared = true;

        mCurrentDragStartPointView.setDragReady(true);
        if (mCurrentDragStartPointView
                .doDrag(generateDragShadow(mCurrentDragStartPointView))) {
            // Hide the view because it has been 'dragged' from its original place
            displayCurrentDragStartPoint(false);

            // Finish action mode
            ActionMode activatingAm = ((IfExplorer) mContext)
                    .getActivatingActionMode();
            if (activatingAm != null) {
                activatingAm.finish();
            }

            // Notify extra listeners a drag started
            notifyFileDragStartedForExtraListener();
        } else {
            revertDragPreparation(mCurrentDragStartPointView);
        }
    }

    private LayerDrawable generateDrawableForStackedFiles(
            LinkedList<View> visibleDragBuddyViews, View topView) {
        if (visibleDragBuddyViews == null || visibleDragBuddyViews.size() == 0) {
            return null;
        } else {
            final int visibleBuddyCount = visibleDragBuddyViews.size();
            final int layerCount;
            if (visibleBuddyCount <= MAX_LAYER_NUM_4_BUNCH_STACK - 1) {
                layerCount = visibleBuddyCount + 1;
            } else {
                layerCount = MAX_LAYER_NUM_4_BUNCH_STACK;
            }
            // Drawable layers for buddy views and the top view
            Drawable[] shadowLayers = new Drawable[layerCount];
            // Dimensions for build a stacked view from views
            Resources resources = mContext.getResources();
            int horizontalGap = resources
                    .getDimensionPixelSize(R.dimen.file_stack_horizontal_gap);
            int verticalGap = resources
                    .getDimensionPixelSize(R.dimen.file_stack_vertical_gap);
            int totalCutWidth = horizontalGap * (layerCount - 1);
            int totalCutHeight = verticalGap * (layerCount - 1);

            int layerIndex = 0;
            // Buddies layers
            for (View buddyView : mCurrentVisibleDragBuddyViews) {
                if (layerIndex >= layerCount - 1) {
                    break;
                }

                // FIXME: 
                // Since the stacked view works for a drag preparation,
                // we can stop the generating process if drag preparation is already finished or canceled
                if (mDragBuddiesPrepared || !mDragBuddiesPreparing) {
                    return null;
                }

                Bitmap originalBitmap = DataUtil.getBitmapFromView(buddyView);
                // Scale down each bitmap for single layer,
                // so we can stack the bitmaps in layers with gap
                // and keep same size with original bitmap
                Bitmap scaledBitmap = Bitmap.createScaledBitmap(originalBitmap,
                        originalBitmap.getWidth() - totalCutWidth,
                        originalBitmap.getHeight() - totalCutHeight, true);
                shadowLayers[layerIndex] = new BitmapDrawable(resources,
                        scaledBitmap);

                layerIndex++;
            }
            // Source layer is on top.
            Bitmap topOriginal = DataUtil.getBitmapFromView(topView);
            Bitmap topScaled = Bitmap.createScaledBitmap(topOriginal,
                    topOriginal.getWidth() - totalCutWidth,
                    topOriginal.getHeight() - totalCutHeight, true);
            shadowLayers[layerCount - 1] = new BitmapDrawable(resources,
                    topScaled);
            // Stack together the layers and set gap between layers 
            // so we can see the stack effect
            LayerDrawable shadowDrawable = new LayerDrawable(shadowLayers);
            for (int index = 0; index < layerCount; index++) {
                shadowDrawable
                        .setLayerInset(index, (layerCount - 1 - index)
                                * horizontalGap, index * verticalGap, index
                                * horizontalGap, (layerCount - 1 - index)
                                * verticalGap);
            }

            return shadowDrawable;
        }
    }

    private LinkedList<View> getCurrentVisibleViewsForDragBuddies(
            LinkedList<Object> dragBuddies) {
        if (dragBuddies == null) {
            return null;
        }

        LinkedList<View> visibleDragBuddyViews = new LinkedList<View>();
        synchronized (dragBuddies) {
            for (Object buddy : dragBuddies) {
                if (buddy instanceof FileItem) {
                    FileItem fileItem = (FileItem) buddy;
                    if (fileItem.getConvertView() != null) {
                        FileItemViewHolder viewHolder = (FileItemViewHolder) fileItem
                                .getConvertView().getTag();
                        if (viewHolder.position == fileItem
                                .getPositionInAdapterList()) {
                            visibleDragBuddyViews
                                    .add(fileItem.getConvertView());
                        }
                    }
                }
            }
        }

        return visibleDragBuddyViews;
    }

    private void hideSearchProgressDialog() {
        if (mSearchDialog != null) {
            mSearchDialog.dismiss();
        }
    }

    private void notifyFileContentChanged() {
        synchronized (mCurrentFileContent) {
            mFileDataListAdapter.clear();
            mFileDataListAdapter.addAll(mCurrentFileContent);
            mFileDataGridAdapter.clear();
            mFileDataGridAdapter.addAll(mCurrentFileContent);
        }

        // Every time file list view got updated, its child views (file item views)
        // should be marked as 'can not get drag event'
        // Because there could be some new child views been created after last drag started
        if (mCurrentFileListView != null
                && mCurrentFileListView instanceof DropAcceptable) {
            Log.i(TAG, "Mark file list item views can not get drag event.");
            ((DropAcceptable) mCurrentFileListView)
                    .markChildCanGetDragEvent(false);
        }
    }

    private void notifyFileDragStartedForExtraListener() {
        if (mExtraFileDragListeners != null) {
            for (DropAcceptable extraListener : mExtraFileDragListeners) {
                if (extraListener != null) {
                    extraListener.onDragStarted(null);
                }
            }
        }
    }

    private void prepareCurrentDraggingView() {
        ImageView draggingView = mCurrentDragStartPointView
                .getDragContextInfo().draggingView;
        BadgeView badge = mCurrentDragStartPointView.getBadgeView();
        if (draggingView != null) {
            if (badge != null) {
                Drawable[] drawableWithBadge = new Drawable[2];
                drawableWithBadge[0] = mDrawableForStackedDragBuddies;
                drawableWithBadge[1] = new BitmapDrawable(
                        mContext.getResources(),
                        DataUtil.getBitmapFromView(badge));
                LayerDrawable layerWithBadge = new LayerDrawable(
                        drawableWithBadge);
                layerWithBadge.setLayerInset(
                        1,
                        mCurrentDragStartPointView.getWidth()
                                - badge.getWidth(),
                        0,
                        0,
                        mCurrentDragStartPointView.getHeight()
                                - badge.getHeight());
                draggingView.setImageDrawable(layerWithBadge);
                badge.setVisibility(View.GONE);
            } else {
                draggingView.setImageDrawable(mDrawableForStackedDragBuddies);
            }
            draggingView.setVisibility(View.VISIBLE);

            ImageView dragReadyView = mCurrentDragStartPointView
                    .getDragContextInfo().dragReadyView;
            if (dragReadyView != null) {
                dragReadyView.setImageDrawable(null);
                dragReadyView.setVisibility(View.GONE);
            }
        }

        mCurrentDragStartPointView.invalidate();
        mUiHandler.sendEmptyMessageDelayed(MSG_FILES_DRAG_READY, 10);
    }

    private void promptMoveFailureDueToPermission(String fromPath,
            String targetPath) {
        Log.i(TAG, "Move data from dir: " + fromPath + " to dir: " + targetPath);
        // Move file drop data to its from directory means no move,
        // in that case prompt for drop denied is not necessary.
        if (!fromPath.equals(targetPath)) {
            Toast.makeText(mContext, R.string.move_failed_permission_deny,
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showSearchProgressDialog(String searchPath, String searchStr,
            int searchType, final FileOperationTask searchTask) {
        if (mSearchDialog == null) {
            mSearchDialog = new ProgressDialog(mContext);
        }

        Resources resources = mContext.getResources();
        String name = "";
        switch (searchType) {
        case StorageUtil.TYPE_AUDIO:
            name = resources.getString(R.string.audio_file);
            break;
        case StorageUtil.TYPE_VIDEO:
            name = resources.getString(R.string.video_file);
            break;
        case StorageUtil.TYPE_IMAGE:
            name = resources.getString(R.string.image_file);
            break;
        case StorageUtil.TYPE_APKFILE:
            name = resources.getString(R.string.apk_file);
            break;
        case StorageUtil.TYPE_FILE:
            name = "'" + searchStr + "'";
            break;
        }

        String title;
        if (searchType == StorageUtil.TYPE_FILE) {
            title = resources.getString(R.string.search_dlg_title, name);
        } else {
            title = resources.getString(R.string.global_search_dlg_title, name);
        }
        String message = resources.getString(R.string.search_dlg_message,
                searchPath);
        mSearchDialog.setTitle(title);
        mSearchDialog.setMessage(message);
        mSearchDialog.setCancelable(false);
        mSearchDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                resources.getString(R.string.cancel),
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        searchTask.cancel();
                        mSearchDialog.dismiss();
                    }
                });

        mSearchDialog.show();
    }

    private void splitCurrentDragBuddies() {
        Log.i(TAG, "splitCurrentDragBuddies");
        if (mCurrentDragBuddyViewsStacked && mCurrentDragStartPointView != null) {
            mCurrentDragStartPointView.setDragBuddies(null);

            BadgeView badge = mCurrentDragStartPointView.getBadgeView();
            if (badge != null) {
                badge.hide();
            }

            ImageView dragReadyView = mCurrentDragStartPointView
                    .getDragContextInfo().dragReadyView;
            ImageView draggingView = mCurrentDragStartPointView
                    .getDragContextInfo().draggingView;
            View normalView = mCurrentDragStartPointView.getDragContextInfo().normalView;

            if (dragReadyView != null) {
                dragReadyView.setImageDrawable(null);

                dragReadyView.setVisibility(View.GONE);
            }

            if (draggingView != null) {
                draggingView.setImageDrawable(null);

                draggingView.setVisibility(View.GONE);
            }

            if (normalView != null) {
                normalView.setVisibility(View.VISIBLE);
            }
            mCurrentDragStartPointView.invalidate();
            mCurrentDragBuddyViewsStacked = false;
        }
    }

    private void stackCurrentDragBuddies() {
        if (!mCurrentDragBuddyViewsStacked) {
            Log.i(TAG, "stackCurrentDragBuddies");
            ImageView dragReadyView = mCurrentDragStartPointView
                    .getDragContextInfo().dragReadyView;
            if (dragReadyView != null) {
                dragReadyView.setImageDrawable(mDrawableForStackedDragBuddies);
                dragReadyView.setVisibility(View.VISIBLE);

                if (mCurrentDragBuddies != null) {
                    BadgeView badge = new BadgeView(mContext, dragReadyView);
                    mCurrentDragStartPointView.setBadgeView(badge);
                    badge.setText(String.format("%d",
                            mCurrentDragBuddies.size() + 1));
                    badge.show();
                }
            }

            View normalView = mCurrentDragStartPointView.getDragContextInfo().normalView;
            if (normalView != null) {
                normalView.setVisibility(View.GONE);
            }
            mCurrentDragStartPointView.invalidate();
            mCurrentDragBuddyViewsStacked = true;

            mUiHandler.sendMessageDelayed(
                    mUiHandler.obtainMessage(MSG_FILES_PREPARE_DRAGGING_VIEW),
                    10);
        }
    }

    private void updateSearchProgressDialog(String searchPath) {
        if (mSearchDialog != null) {
            Resources resources = mContext.getResources();
            mSearchDialog.setMessage(resources.getString(
                    R.string.search_dlg_message, searchPath));
        }
    }
}

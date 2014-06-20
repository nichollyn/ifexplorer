package com.sparseboolean.ifexplorer;

import gem.com.slidinglayer.SlidingLayer;
import gem.kevin.innov.FilePathUrlManager;
import gem.kevin.util.DataUtil;
import gem.kevin.util.StorageUtil;
import gem.kevin.widget.DropableListView;

import java.io.File;
import java.util.HashSet;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.sparseboolean.ifexplorer.IfAppController.FileDataListAdapter;
import com.sparseboolean.ifexplorer.ui.DeviceDataAdapter;
import com.sparseboolean.ifexplorer.ui.FilePathNavigator;
import com.sparseboolean.ifexplorer.ui.TaskGroupListWidget;
import com.sparseboolean.ifexplorer.ui.FilePathNavigator.NavigationCallback;
import com.sparseboolean.ifexplorer.R;

public class IfFileChooser extends Activity implements OnItemClickListener,
        NavigationCallback, IfAppController.DeviceDataListener,
        IfAppController.UiCallback {
    private static final String TAG = "IfExplorer-IfFileChooser";
    private static final boolean KLOG = true;

    private static final String KEY_SAVE_LOCATION = "location";

    private String mInitLocation;
    private FilePathUrlManager mFileManager;
    private IfAppController mAppController;
    private FileDataListAdapter mFileDataListAdapter;
    private DropableListView mFileListView;
    private ImageView mCollectionSwitchButton;
    private SlidingLayer mSlidingLayer;
    // Task groups
    private HashSet<TaskGroupListWidget> mMiniPanelWidgets = new HashSet<TaskGroupListWidget>();

    // Devices
    private TaskGroupListWidget mInternalStorageMiniWidget;

    private TaskGroupListWidget mSdcardMiniWidget;
    private TaskGroupListWidget mUsb1StorageMiniWidget;
    private TaskGroupListWidget mUsb2StorageMiniWidget;
    private TaskGroupListWidget mUsb3StorageMiniWidget;
    private TaskGroupListWidget mUsb4StorageMiniWidget;
    private TaskGroupListWidget mStorageA_MiniWidget;
    private TaskGroupListWidget mStorageB_MiniWidget;
    private TaskGroupListWidget mStorageC_MiniWidget;
    private TaskGroupListWidget mStorageD_MiniWidget;
    private static final int kTagTaskGroupBase = 0;

    private AbsListView mCurrentShowList;

    private FilePathNavigator mPathNavigator;

    private Button mCancelButton;

    /* Storage monitoring */
    private IntentFilter mStorageIntentFilter;
    private BroadcastReceiver mStorageBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String path = intent.getData().getPath();
            Log.i(TAG, "ACTION: " + action + " happened on path: " + path);

            if (mAppController != null) {
                if (mFileManager.getCurrentUrl().contains(path)) {
                    Log.i(TAG, "CONTAINS");
                    if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)
                            || action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)) {
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
                }

                if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_MOUNTED, true);
                } else if (action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_UNMOUNTED, true);
                    // URL starts with this path now become invalid
                    mFileManager.removeInvalidUrls(path);
                } else if (action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)) {
                    mAppController.updateStorageDevice(path,
                            Environment.MEDIA_BAD_REMOVAL, true);
                    mFileManager.removeInvalidUrls(path);
                } else {
                    return;
                }
            }
        }
    };

    public void handleFileItemClick(ArrayAdapter<FileItem> fileDataAdapter,
            View view, int position, long id) {
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
        } else {
            onFileSelected(file);
        }
    }

    @Override
    public void navigateToPath(String path) {
        mAppController.updateDirectory(mFileManager.getNextUrlContent(path,
                true, false));
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
    public void onItemClick(AdapterView<?> adapterView, View itemView, int pos,
            long id) {
        if (adapterView.getAdapter() instanceof DeviceDataAdapter) {
            DeviceItem deviceItem = (DeviceItem) adapterView
                    .getItemAtPosition(pos);
            String path = deviceItem.getPath();

            mAppController.updateDirectory(mFileManager.getNextUrlContent(path,
                    true, false));

            closeSlidingLayer(false);
        } else if (adapterView.getAdapter() instanceof FileDataListAdapter) {
            handleFileItemClick(mFileDataListAdapter, itemView, pos, id);
        }
    }

    @Override
    public void onStorageDeviceItemChanged() {
        // do nothing
    }

    @Override
    public void onUrlContentUpdated(String newUrl, int contentSize,
            boolean fromHistory) {
        if (mPathNavigator != null) {
            mPathNavigator.updatePathNodes(mFileManager.getCurrentUrl());
            mPathNavigator.switchToNavigateMode();
        }
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

    private void onFileSelected(File file) {
        if (file != null) {
            if (file.canRead()) {
                Uri uri = Uri.fromFile(file);
                setResult(RESULT_OK,
                        new Intent().setData(Uri.parse(uri.toString())));
                finish();
            } else {
                String errMessage = getResources().getString(
                        R.string.no_permission_read_file, file.getName());
                Toast.makeText(this, errMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openSlidingLayer(boolean smooth) {
        if (mSlidingLayer != null && !mSlidingLayer.isOpened()) {
            mSlidingLayer.openLayer(smooth);
            mCollectionSwitchButton
                    .setImageResource(R.drawable.collection_collapse);
            mCurrentShowList.setVisibility(View.GONE);
        }
    }

    private void setupAddressBar() {
        mPathNavigator = (FilePathNavigator) findViewById(R.id.filePath_navigator);
        mPathNavigator.setNavigationCallback(this);
        mPathNavigator.setPathButtonOnDropDelegate(mAppController);
        mPathNavigator.setPathButtonOpenDelegate(mAppController);
        mPathNavigator.showInSimpleMode(true);

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
    }

    private void setupFileList() {
        mFileDataListAdapter = mAppController.getFileDataListAdapter();

        mFileListView = (DropableListView) findViewById(R.id.file_listview);
        mFileListView.setAdapter(mFileDataListAdapter);
        mFileListView.setOnItemClickListener(this);

        // default
        mCurrentShowList = mFileListView;
        mFileListView.setVisibility(View.VISIBLE);

        mAppController.updateDirectory(mFileManager.refreshUrlContent());
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

    protected void intializeMiniWidgets() {
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
        // USB 1 storage
        mUsb1StorageMiniWidget = (TaskGroupListWidget) findViewById(R.id.usb1_disk_collection);
        mUsb1StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 4);
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
        mUsb2StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 5);
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
        mUsb3StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 6);
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
        mUsb4StorageMiniWidget.setGroupTag(kTagTaskGroupBase + 7);
        mUsb4StorageMiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb4_storage));
        mUsb4StorageMiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usb4storageList = mUsb4StorageMiniWidget.activateListMode();
        usb4storageList.setOnItemClickListener(this);
        usb4storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBPort4));
        mMiniPanelWidgets.add(mUsb4StorageMiniWidget);
        // USB Storage A
        mStorageA_MiniWidget = (TaskGroupListWidget) findViewById(R.id.usb_a_collection);
        mStorageA_MiniWidget.setGroupTag(kTagTaskGroupBase + 8);
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
        mStorageB_MiniWidget.setGroupTag(kTagTaskGroupBase + 9);
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
        mStorageC_MiniWidget.setGroupTag(kTagTaskGroupBase + 10);
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
        mStorageD_MiniWidget.setGroupTag(kTagTaskGroupBase + 11);
        mStorageD_MiniWidget.setDefaultGroupLabel(getResources().getString(
                R.string.usb_storage_d));
        mStorageD_MiniWidget.setEjectable(IfConfig.SIGNED_WITH_PLATFORM_KEY);
        AbsListView usbd_storageList = mStorageD_MiniWidget.activateListMode();
        usbd_storageList.setOnItemClickListener(this);
        usbd_storageList.setAdapter(mAppController
                .getDeviceDataAdapter(StorageUtil.USBSDD));
        mMiniPanelWidgets.add(mStorageD_MiniWidget);
    }

    protected void refreshTaskGroups() {
        for (TaskGroupListWidget taskGroup : mMiniPanelWidgets) {
            AbsListView taskList = taskGroup.activateListMode();

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

    protected void initializeSlidingLayer() {
        // Reset at first
        mSlidingLayer = null;
        if (mCollectionSwitchButton != null) {
            mCollectionSwitchButton.setVisibility(View.GONE);
        }

        mSlidingLayer = (SlidingLayer) findViewById(R.id.sliding_panel);

        if (mSlidingLayer != null) {
            // Using sliding layer means display space is limited,
            FilePathNavigator.MAX_BUTTON_NUM = 2;

            initSlidingLayerState();

            if (mCollectionSwitchButton != null) {
                mCollectionSwitchButton.setVisibility(View.VISIBLE);
            }
        } else {
            FilePathNavigator.MAX_BUTTON_NUM = 5;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ifexplorer_file_chooser);

        restoreSavedInfo(savedInstanceState);

        // file manager
        mFileManager = FilePathUrlManager.getLocalFileManager(mInitLocation);

        // event handler
        mAppController = new IfAppController(this, mFileManager);
        mAppController.setUiCallback(this);

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

        // initialize sliding layer
        initializeSlidingLayer();

        mCancelButton = (Button) findViewById(R.id.cancelButton);
        mCancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                IfFileChooser.this.finish();
            }

        });

        // update current directory
        mAppController.updateDirectory(mFileManager.refreshUrlContent());
    }

    @Override
    protected void onDestroy() {
        mAppController.cancelAllBackgroundWork();
        unregisterReceiver(mStorageBroadcastReceiver);

        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(KEY_SAVE_LOCATION, mFileManager.getCurrentUrl());
        super.onSaveInstanceState(outState);
    }

    protected void restoreSavedInfo(Bundle savedInfo) {
        if (savedInfo != null) {
            mInitLocation = savedInfo.getString(KEY_SAVE_LOCATION);

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

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

package gem.kevin.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import com.sparseboolean.ifexplorer.IfConfig;
import com.sparseboolean.ifexplorer.IfStorageVolume;
import com.sparseboolean.ifexplorer.R;

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.util.Log;

/** @author kevin.lin */
public final class StorageUtil {
    public class StorageVolumeStatFs {
        private long mTotalSize = 0;
        private long mAvailableSize = 0;
        private long mUsedSize = 0;

        StorageVolumeStatFs(IfStorageVolume storageVolume) {
            if (storageVolume != null) {
                final StatFs stat = new StatFs(storageVolume.getPath());
                final long blockSize = stat.getBlockSize();
                final long totalBlocks = stat.getBlockCount();
                final long availableBlocks = stat.getAvailableBlocks();

                mTotalSize = totalBlocks * blockSize;
                mAvailableSize = availableBlocks * blockSize;
                mUsedSize = mTotalSize - mAvailableSize;
            }
        }

        public long getAvailableSize() {
            return mAvailableSize;
        }

        public long getTotalSize() {
            return mTotalSize;
        }

        public long getUsedSize() {
            return mUsedSize;
        }
    }

    private static final String TAG = "IfManager-Utils";
    private static final boolean KLOG = false;
    private static final boolean tempLOG = true;

    /* favorites */
    public static final String PATH_HOME = Environment
            .getExternalStorageDirectory().getPath();
    public static final String PATH_DOWNLOADS = PATH_HOME + "/"
            + Environment.DIRECTORY_DOWNLOADS;
    public static final String PATH_MUSIC = PATH_HOME + "/"
            + Environment.DIRECTORY_MUSIC;
    public static final String PATH_PICTURES = PATH_HOME + "/"
            + Environment.DIRECTORY_PICTURES;
    public static final String PATH_MOVIES = PATH_HOME + "/"
            + Environment.DIRECTORY_MOVIES;

    public static final String PATH_DCIM = PATH_HOME + "/"
            + Environment.DIRECTORY_DCIM;

    public static float GigaByteToByte = 1024 * 1024 * 1024;

    /* exclude some android directories for searching */
    private static ArrayList<String> sExcludeSearchPaths = null;
    public static final int TYPE_AUDIO = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_IMAGE = 3;
    public static final int TYPE_FILE = 4;
    public static final int TYPE_SDCARD = 5;
    public static final int TYPE_UDISK = 6;
    public static final int TYPE_APKFILE = 7;
    public static final int TYPE_HOME = 8;
    public static final int TYPE_ROOT = 9;

    /* Android Linux system directories and files */
    private static ArrayList<String> sAndroidSysPaths = null;

    // Storage position
    public static final int USBPort0 = 0;
    public static final int USBPort1 = 1;
    public static final int USBPort2 = 2;
    public static final int USBPort3 = 3;
    public static final int USBPort4 = 4;
    public static final int USBPort5 = 5;
    public static final int SDSlot = 6;
    public static final int Internal = 7;
    // For covering any other non configurable external storage policy
    // A Linux device name style
    public static final int USBSDA = 8;
    public static final int USBSDB = 9;
    public static final int USBSDC = 10;
    public static final int USBSDD = 11;
    public static final int USBSDE = 12;
    public static final int USBSDF = 13;

    // 'diskid' file related
    public static final String DISKID_FILE_NAME = ".diskid";
    public static final String DISKID_TAG_DISKNAME = "DISKNAME";
    public static final String DISKID_TAG_DISKVENDOR = "DISKVENDOR";
    public static final String DISKID_TAG_DISKMODEL = "DISKMODEL";

    // 'volid' file related
    public static final String VOLID_FILE_NAME = ".volid";
    public static final String VOLID_TAG_LABEL = "LABEL";
    public static final String VOLID_TAG_TYPE = "TYPE";
    public static final String VOLID_TAG_ENCODING = "ENCODING";

    public static final String BRACKET = "\"";

    public static boolean detectMountStateFromProc(String path) {
        if (tempLOG) {
            Log.i("@temp", "detectMountStateFromProc --- for path: " + path);
        }

        File procFile = new File(IfConfig.MOUNT_PROC_PATH);
        if (procFile.exists() && procFile.isFile()) {
            try {
                BufferedReader input = new BufferedReader(new FileReader(
                        procFile));
                String line;

                while ((line = input.readLine()) != null) {
                    if (line.contains(path)) {
                        input.close();
                        return true;
                    }
                }

                input.close();
            } catch (IOException ioException) {
                Log.i(TAG, "io error when try to read /proc/mounts.");
            }
        }

        if (tempLOG) {
            Log.i("@temp", "not mounted path: " + path);
        }

        return false;
    }

    public static ArrayList<String> getExcludeSearchPath() {
        if (sExcludeSearchPaths != null) {
            return sExcludeSearchPaths;
        } else {
            sExcludeSearchPaths = new ArrayList<String>();
            sExcludeSearchPaths.add("/mnt/obb");
            sExcludeSearchPaths.add("/mnt/asec");
            sExcludeSearchPaths.add("/mnt/secure");
        }

        return sExcludeSearchPaths;
    }

    public static String getExtraMountRoot() {
        // return "/mnt";
        return "/mnt";
    }

    public static String getHomeDir() {
        if (IfConfig.PRODUCT_ROCK_CHIP) {
            return "/sdcard";
        }

        if (android.os.Build.VERSION.SDK_INT >= 17) {
            int id = 0;
            try {
                Class<?> class_UserHandle = null;
                try {
                    class_UserHandle = Class.forName("android.os.UserHandle");
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }

                if (class_UserHandle != null) {
                    Method method_myUserId = class_UserHandle.getMethod(
                            "myUserId", new Class[] {});
                    try {
                        id = (Integer) method_myUserId.invoke(class_UserHandle,
                                new Object[] {});
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

                return "/storage/emulated/" + id;
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
                return "/sdcard";
            }
        } else {
            return "/sdcard";
        }
    }

    public static boolean isAndroidSysFile(String path) {
        if (path == null) {
            return false;
        }

        if (sAndroidSysPaths == null) {
            sAndroidSysPaths = new ArrayList<String>();
            // DIRS
            sAndroidSysPaths.add("/acct/");
            sAndroidSysPaths.add("/cache/");
            sAndroidSysPaths.add("/config/");
            sAndroidSysPaths.add("/d/");
            sAndroidSysPaths.add("/data/");
            sAndroidSysPaths.add("/dev/");
            sAndroidSysPaths.add("/proc/");
            sAndroidSysPaths.add("/root/");
            sAndroidSysPaths.add("/sbin/");
            sAndroidSysPaths.add("/sys/");
            sAndroidSysPaths.add("/system");
            sAndroidSysPaths.add("/usbdrive/");
            sAndroidSysPaths.add("/vendor/");
            // FILES
            sAndroidSysPaths.add("/init");
            sAndroidSysPaths.add("/default.prop");
        }

        if (sAndroidSysPaths.contains(path)) {
            return true;
        } else {
            for (String sysPath : sAndroidSysPaths) {
                if (path.startsWith(sysPath)
                        || (path + "/").startsWith(sysPath)) {
                    return true;
                }
            }

            return false;
        }
    }

    public static boolean isExcludeSearchPath(String absolutePath,
            ArrayList<String> reservedDirs) {
        // kevin@xmic: special handle for trashed in Windows, Linux and MacOSX,
        // on SD APK host,
        // LOST.DIR, etc
        if (absolutePath.contains("$RECYCLE.BIN")
                || absolutePath.contains(".android_secure")
                || absolutePath.contains("Recycled")
                || absolutePath.contains("RECYCLER")
                || absolutePath.contains("System Volume Information")
                || absolutePath.contains(".Trashes")
                || absolutePath.contains(".DS_Store")
                || absolutePath.contains(".Spotlight-V")
                || absolutePath.contains("LOST.DIR")
                || absolutePath.contains(".Spotlight-V")
                || absolutePath.contains("Androind/obb")
                || absolutePath.contains("emulated/legacy")
                || absolutePath.contains("lost+found")) {
            return true;
        }

        // if reserved directory is explicitly specified, make sure we don't
        // exclude directory listed in it
        if (reservedDirs != null) {
            for (String reserved : reservedDirs) {
                if (absolutePath.startsWith(reserved)) {
                    return false;
                }
            }
        }

        return false;
    }

    public static boolean isExtendPartition(String mountPoint) {
        if (mountPoint == null) {
            return false;
        }

        final StatFs stat;
        try {
            stat = new StatFs(mountPoint);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "illegal argument: " + mountPoint
                    + " for StatFs intialization");
            return false;
        }

        final long totalBlocks = stat.getBlockCount();
        final long blockSize = stat.getBlockSize();
        final long totalSize = totalBlocks * blockSize;
        final double totalSizeInGB = totalSize / GigaByteToByte;
        // Log.i(TAG, "total size %.2fGB: " + totalSizeInGB + " for path" +
        // mountPoint);
        // kevin@xmic: small than 20MB, assume as an extended partition
        if (totalSizeInGB < 0.02f && totalSizeInGB > 0) {
            return true;
        }

        return false;
    }

    // kevin@xmic: tricky for skipping unnecessary validation on first volume of
    // disk
    public static boolean isFirstVolume(String mountPoint) {
        if (mountPoint.contains("p1")) {
            return true;
        } else {
            return false;
        }
    }

    // kevin@xmic: hard-coded primary volume
    // primary mount point(EXTERNAL_STORAGE) is configured in init.[product].rc
    public static boolean isPrimaryVolume(String mountPoint) {
        if (mountPoint == null) {
            return false;
        }

        if (Environment.getExternalStorageDirectory().getPath()
                .equals(mountPoint)) {
            return true;
        } else {
            return false;
        }
    }

    /*
     * kevin@xmic: A method to check if a volume is a logical partition Well,
     * doing the stuff by counting volume size in Java space could be
     * inefficient choice underlying facilities is responsible to get logical
     * partition filtered as mount candidate
     */
    public static boolean isValidVolume(String mountPoint) {
        if (mountPoint == null) {
            return false;
        }

        final StatFs stat;
        try {
            stat = new StatFs(mountPoint);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "illegal argument: " + mountPoint
                    + " for StatFs intialization");
            return false;
        }

        final long totalBlocks = stat.getBlockCount();
        final long blockSize = stat.getBlockSize();
        final long totalSize = totalBlocks * blockSize;
        final double totalSizeInGB = totalSize / GigaByteToByte;
        // Log.i(TAG, "total size %.2fGB: " + totalSizeInGB + " for path" +
        // mountPoint);
        // kevin@xmic: small than 20MB, considered as non valid volume (might be
        // an extended volume
        if (totalSizeInGB < 0.02f) {
            return false;
        }

        return true;
    }

    // Instance fields
    private final Context mContext;
    /* mount points */
    // mount points should be unique, so we use HashSet
    private static final HashSet<String> sMountPoints = new HashSet<String>();
    /* storage volumes and storage unique id to volume map */
    private static final ArrayList<IfStorageVolume> sStorageVolumes = new ArrayList<IfStorageVolume>();

    private static final HashMap<String, IfStorageVolume> sMountPointVolumeMap = new HashMap<String, IfStorageVolume>();

    private static final HashMap<String, IfStorageVolume> sDeviceNodeVolumeMap = new HashMap<String, IfStorageVolume>();

    // HashMap to keep disk names and volume labels for mount points
    private static final HashMap<String, String> sDiskIds = new HashMap<String, String>();
    private static final HashMap<String, String> sVolumeIds = new HashMap<String, String>();

    // HashMap to keep a top level table for interested IDs and its hash map
    public static final HashMap<String, HashMap<String, String>> sStorageIdMap = new HashMap<String, HashMap<String, String>>();

    private static StorageUtil sStorageUtil = null;

    public static int getMountPort(String path) {
        if (path == null) {
            return -1;
        }

        if (path.contains("emulated") || path.contains("internal")) {
            return Internal;
        } else if (path.contains("sdcard") || path.contains("external_sd")) {
            return SDSlot;
        } else if (path.contains("usb0") || path.contains("USB_DISK0")) {
            return USBPort0;
        } else if (path.contains("usb1") || path.contains("USB_DISK1")) {
            return USBPort1;
        } else if (path.contains("usb2") || path.contains("USB_DISK2")) {
            return USBPort2;
        } else if (path.contains("usb3") || path.contains("USB_DISK3")) {
            return USBPort3;
        } else if (path.contains("usb4") || path.contains("USB_DISK4")) {
            return USBPort4;
        } else if (path.contains("sda")) {
            return USBSDA;
        } else if (path.contains("sdb")) {
            return USBSDB;
        } else if (path.contains("sdc") && !path.contains("sdcard")) {
            return USBSDC;
        } else if (path.contains("sdd")) {
            return USBSDD;
        } else if (path.contains("sde")) {
            return USBSDE;
        } else if (path.contains("sdf")) {
            return USBSDF;
        }

        return -1;
    }

    public static StorageUtil getSingleton(Context context) {
        if (sStorageUtil == null && context != null) {
            sStorageUtil = new StorageUtil(context);
        }

        return sStorageUtil;
    }

    private StorageUtil(Context context) {
        mContext = context;

        initializeIfStorageVolumes();
        initializeStorageIds();
    }

    public void createIfStorageVolumeByPath(String path) {
        Log.i(TAG, "createIfStorageVolumeByPath, path=" + path);
        boolean removable = true;
        int descriptionId = getStorageDescriptionResId(path);
        boolean primary = false;
        boolean emulated = path.contains("emulated");
        IfStorageVolume volume = new IfStorageVolume(null, path, descriptionId,
                primary, removable, emulated, null);

        sMountPointVolumeMap.put(path, volume);
        if (!sStorageVolumes.contains(volume)) {
            sStorageVolumes.add(volume);
        }
        sMountPoints.add(path);

        fulfillIfStorageVolumesByProc();
    }

    public String getCachedId(String path, String idTag) {
        HashMap<String, String> map = sStorageIdMap.get(idTag);
        if (map == null) {
            if (KLOG) {
                Log.i(TAG, "== kevin@xmic == Can't get hash map for " + idTag);
            }
            return null;
        }

        return map.get(path);
    }

    public IfStorageVolume getIfStorageVolumeFromPath(String path) {
        if (sMountPointVolumeMap == null) {
            return null;
        }

        return sMountPointVolumeMap.get(path);
    }

    public ArrayList<IfStorageVolume> getIfStorageVolumes() {
        return sStorageVolumes;
    }

    public ArrayList<String> getMountedStorageVolumePaths() {
        ArrayList<String> result = new ArrayList<String>();

        if (sStorageVolumes == null) {
            return null;
        }

        for (IfStorageVolume volume : sStorageVolumes) {
            String path = volume.getPath();

            if (isMounted(path)) {
                result.add(path);
            }
        }

        return result;
    }

    public String getMountpoint(String containingPath) {
        for (String mountPoint : sMountPoints) {
            if (containingPath.contains(mountPoint)) {
                return mountPoint;
            }
        }

        return null;
    }

    public ArrayList<String> getMountpointsForMountedBuddy(String buddy) {
        if (buddy == null) {
            return null;
        }

        int port = getMountPort(buddy);
        ArrayList<String> matchs = new ArrayList<String>();
        HashSet<String> all = sMountPoints;
        for (String item : all) {
            if (getMountPort(item) == port && !item.equals(buddy)
                    && detectMountStateFromProc(item)) {
                matchs.add(item);
            }
        }

        return matchs;
    }

    public String getStorageDescription(int resId) {
        Resources res = mContext.getResources();
        return res != null ? res.getString(resId) : null;
    }

    public int getStorageDescriptionResId(String path) {
        if (path == null) {
            return -1;
        }

        if (path.contains("sdcard") || path.contains("emulated")) {
            if (IfConfig.PRODUCT_BUILT_IN_SDCARD) {
                return R.string.internal_sdcard;
            } else {
                return R.string.sdcard_storage;
            }
        } else if (path.contains("internal_sd")) {
            return R.string.home_dir;
        } else if (path.contains("extsd") || path.contains("external_sd")) {
            return R.string.sdcard_storage;
        } else if (path.contains("usb0") || path.contains("USB_DISK0")) {
            return R.string.usb_storage;
        } else if (path.contains("usb1") || path.contains("USB_DISK1")) {
            return R.string.usb1_storage;
        } else if (path.contains("usb2") || path.contains("USB_DISK2")) {
            return R.string.usb2_storage;
        } else if (path.contains("usb3") || path.contains("USB_DISK3")) {
            return R.string.usb3_storage;
        } else if (path.contains("usb4") || path.contains("USB_DISK4")) {
            return R.string.usb4_storage;
        } else if (path.contains("usb")) {
            return R.string.usb_storage;
        } else if (path.contains("sda")) {
            return R.string.usb_storage_a;
        } else if (path.contains("sdb")) {
            return R.string.usb_storage_b;
        } else if (path.contains("sdc") && !path.contains("sdcard")) {
            return R.string.usb_storage_c;
        } else if (path.contains("sdd")) {
            return R.string.usb_storage_d;
        } else if (path.contains("sde")) {
            return R.string.usb_storage_e;
        } else if (path.contains("sdf")) {
            return R.string.usb_storage_f;
        }

        return -1;
    }

    public String getStringFromResId(int resid) {
        return mContext.getResources().getString(resid);
    }

    public ArrayList<String> getSynthesizedStorageVolumePaths(
            ArrayList<String> toSynthesize) {
        if (toSynthesize == null) {
            return null;
        }

        ArrayList<String> result = new ArrayList<String>();

        for (String item : toSynthesize) {
            // Add all to-synthesize by default,
            // We might want to filter some volumes in the future
            result.add(item);
        }

        return result;
    }

    // kevin@xmic: Be sure to get this method called before using getCachedId
    // method
    public void initializeStorageIds() {
        // Currently we are interested in disk name(vendor+model) and volume
        // label
        sStorageIdMap.put(DISKID_TAG_DISKNAME, sDiskIds);
        sStorageIdMap.put(DISKID_TAG_DISKVENDOR, sDiskIds);
        sStorageIdMap.put(DISKID_TAG_DISKMODEL, sDiskIds);
        sStorageIdMap.put(VOLID_TAG_LABEL, sVolumeIds);
    }

    public boolean isMounted(IfStorageVolume storageVolume) {
        if (storageVolume == null) {
            return false;
        }

        String path = storageVolume.getPath();
        if (detectMountStateFromProc(path)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isMounted(String mountPoint) {
        if (detectMountStateFromProc(mountPoint)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isMountPoint(String path) {
        if (sMountPoints != null) {
            return sMountPoints.contains(path);
        }

        return false;
    }

    public boolean isPathDirtyForUsage(String path, int usage) {
        // TODO: to implement
        return true;
    }

    public String readDiskIdForPath(String path, String diskidTag) {
        String diskidFilePath = path + "/" + DISKID_FILE_NAME;
        File diskidFile = new File(diskidFilePath);

        FileInputStream fistream;
        try {
            fistream = new FileInputStream(diskidFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Can't find diskid file from " + diskidFilePath);
            return cacheId(path, diskidTag, null);
        }

        InputStreamReader isreader = null;
        BufferedReader bufreader = null;

        String result = null;
        String diskid = null;

        // All known disk IDs are named in Latin language, UTF-8 would always be
        // enough
        try {
            isreader = new InputStreamReader(fistream, "UTF-8");
            bufreader = new BufferedReader(isreader);
            while ((diskid = bufreader.readLine()) != null) {
                if (diskid.contains(diskidTag)) {
                    if (KLOG) {
                        Log.i(TAG, "Get disk id: " + diskid + "for " + path);
                    }
                    int openBracket = diskid.indexOf(BRACKET);
                    int closeBracket = diskid.lastIndexOf(BRACKET);
                    if (closeBracket > (openBracket + 1)) {
                        result = diskid
                                .substring(openBracket + 1, closeBracket)
                                .trim();
                        Log.i(TAG, "Disk Name is: " + result);
                    } else {
                        result = null;
                    }
                }
            }

            bufreader.close();
            isreader.close();
            fistream.close();
            return cacheId(path, diskidTag, result);
        } catch (IOException e) {
            Log.e(TAG, "Failed due exception while read {" + diskidTag
                    + "} from " + diskidFilePath);
        }

        return cacheId(path, diskidTag, result);
    }

    public String readDiskNameForPath(String path) {
        String diskidFilePath = path + "/" + DISKID_FILE_NAME;
        File diskidFile = new File(diskidFilePath);
        String diskVendor = null;
        String diskModel = null;

        String diskName = null;

        FileInputStream fistream;
        try {
            fistream = new FileInputStream(diskidFile);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Can't find diskid file from " + diskidFilePath);
            return cacheId(path, DISKID_TAG_DISKNAME, null);
        }

        InputStreamReader isreader = null;
        BufferedReader bufreader = null;

        String diskid = null;

        // All known disk IDs are named in Latin language, UTF-8 would always be
        // enough
        try {
            isreader = new InputStreamReader(fistream, "UTF-8");
            bufreader = new BufferedReader(isreader);
            while ((diskid = bufreader.readLine()) != null) {
                if (diskid.contains(DISKID_TAG_DISKVENDOR)) {
                    int openBracket = diskid.indexOf(BRACKET);
                    int closeBracket = diskid.lastIndexOf(BRACKET);
                    if (closeBracket > (openBracket + 1)) {
                        diskVendor = diskid.substring(openBracket + 1,
                                closeBracket).trim();
                        Log.i(TAG, "Disk vendor is: " + diskVendor);
                    } else {
                        diskVendor = null;
                    }
                } else if (diskid.contains(DISKID_TAG_DISKMODEL)) {
                    int openBracket = diskid.indexOf(BRACKET);
                    int closeBracket = diskid.lastIndexOf(BRACKET);
                    if (closeBracket > (openBracket + 1)) {
                        diskModel = diskid.substring(openBracket + 1,
                                closeBracket).trim();
                        Log.i(TAG, "Disk model is: " + diskModel);
                    } else {
                        diskModel = null;
                    }
                }
            }

            bufreader.close();
            isreader.close();
            fistream.close();

            // Combine vendor and model as disk name
            if (diskVendor != null && diskModel != null) {
                diskName = diskVendor + "  " + diskModel;
            } else if (diskModel == null) {
                diskName = diskVendor;
            }

            return cacheId(path, DISKID_TAG_DISKNAME, diskName);
        } catch (IOException e) {
            Log.e(TAG, "Failed due exception while read disk name from "
                    + diskidFilePath);
        }

        return cacheId(path, DISKID_TAG_DISKNAME, diskName);
    }

    public String readVolIdForPath(String path, String volidTag) {
        String volidFilePath = path + "/" + VOLID_FILE_NAME;
        File volidFile = new File(volidFilePath);

        FileInputStream fistream;
        try {
            fistream = new FileInputStream(volidFile);
        } catch (FileNotFoundException e) {
            Log.w(TAG, "Can't find volid file from " + volidFilePath);
            return cacheId(path, volidTag, null);
        }

        InputStreamReader isreader = null;
        BufferedReader bufreader = null;

        String result = null;
        String volid = null;
        boolean isUtf8 = true;

        try {
            // firstly try to read 'volid' file in UTF-8 encoding
            isreader = new InputStreamReader(fistream, "UTF-8");
            bufreader = new BufferedReader(isreader);
            while ((volid = bufreader.readLine()) != null) {
                if (volid.contains(volidTag)) {
                    if (KLOG) {
                        Log.i(TAG, "Get volume id: " + volid + "for " + path);
                    }
                    int openBracket = volid.indexOf(BRACKET);
                    int closeBracket = volid.lastIndexOf(BRACKET);
                    if (closeBracket > (openBracket + 1)) {
                        result = volid.substring(openBracket + 1, closeBracket)
                                .trim();
                    } else {
                        result = null;
                    }
                }

                if (volid.contains("ENCODING=\"ansi\"")) {
                    // stop read as UTF-8 and reset reader
                    bufreader.close();
                    bufreader = null;
                    isreader.close();
                    isreader = null;
                    fistream.close();
                    fistream = null;
                    isUtf8 = false;
                    break;
                }
            }

            if (!isUtf8) {
                try {
                    fistream = new FileInputStream(volidFile);
                } catch (FileNotFoundException e) {
                    Log.w(TAG, "Can't find volid file from " + volidFilePath);
                    return cacheId(path, volidTag, null);
                }
                isreader = new InputStreamReader(fistream, "GBK");
                bufreader = new BufferedReader(isreader);
                while ((volid = bufreader.readLine()) != null) {
                    if (volid.contains(volidTag)) {
                        int openBracket = volid.indexOf(BRACKET);
                        int closeBracket = volid.lastIndexOf(BRACKET);
                        if (closeBracket > (openBracket + 1)) {
                            result = volid.substring(openBracket + 1,
                                    closeBracket).trim();
                        } else {
                            result = null;
                        }
                    }
                }
            }

            bufreader.close();
            isreader.close();
            fistream.close();
            return cacheId(path, volidTag, result);
        } catch (IOException e) {
            Log.e(TAG, "Failed due exception while read {" + volidTag
                    + "} from " + volidFilePath);
        }

        return cacheId(path, volidTag, result);
    }

    public StorageVolumeStatFs statFsContainerFileSystem(String filePath) {
        if (sMountPoints == null || filePath == null) {
            return null;
        } else {
            String fileCanonicalPath;

            try {
                fileCanonicalPath = new File(filePath).getCanonicalPath();
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }

            for (String mountPoint : sMountPoints) {
                if (fileCanonicalPath.equals(mountPoint)
                        || fileCanonicalPath.startsWith(mountPoint)) {
                    return new StorageVolumeStatFs(
                            getIfStorageVolumeFromPath(mountPoint));
                }
            }

            return null;
        }
    }

    private void clearIfStorageVolumeInfo() {
        if (!sMountPointVolumeMap.isEmpty()) {
            sMountPointVolumeMap.clear();
        }
        if (!sDeviceNodeVolumeMap.isEmpty()) {
            sDeviceNodeVolumeMap.clear();
        }
        if (!sStorageVolumes.isEmpty()) {
            sStorageVolumes.clear();
        }
        if (!sMountPoints.isEmpty()) {
            sMountPoints.clear();
        }
    }

    private void createIfStorageVolumesByProc() {
        File procFile = new File(IfConfig.MOUNT_PROC_PATH);
        if (procFile.exists() && procFile.isFile()) {
            try {
                BufferedReader input = new BufferedReader(new FileReader(
                        procFile));
                String line;

                while ((line = input.readLine()) != null) {
                    String[] fields = line.split(" ");
                    String devNode = fields[0];
                    if (devNode.contains("block")
                            && !devNode.contains("mtdblock")) {
                        String path = fields[1];
                        String fsFormat = fields[2];
                        if (!sDeviceNodeVolumeMap.containsKey(devNode)
                                && !path.equals(IfConfig.ANDROID_ASEC_PATH)) {
                            // FIXME
                            boolean isSDStorage = (devNode.contains("mmcblk") || path
                                    .contains("sdcard"));
                            boolean removable = !(isSDStorage && IfConfig.PRODUCT_BUILT_IN_SDCARD);
                            int descriptionId = getStorageDescriptionResId(path);
                            boolean primary = isSDStorage;
                            boolean emulated = path.contains("emulated");
                            IfStorageVolume volume = new IfStorageVolume(
                                    devNode, path, descriptionId, primary,
                                    removable, emulated, fsFormat);

                            sDeviceNodeVolumeMap.put(devNode, volume);
                            sMountPointVolumeMap.put(path, volume);
                            if (!sStorageVolumes.contains(volume)) {
                                sStorageVolumes.add(volume);
                            }
                            sMountPoints.add(path);
                        }
                    } else {
                        continue;
                    }
                }

                input.close();
            } catch (IOException ioException) {
                Log.i(TAG, "io error when try to read /proc/mounts.");
            }
        }
    }

    private void fulfillIfStorageVolumesByProc() {
        if (KLOG) {
            Log.i(TAG, "Fulfill IfStorageVolumes by proc.");
        }
        File procFile = new File(IfConfig.MOUNT_PROC_PATH);
        if (procFile.exists() && procFile.isFile()) {
            try {
                BufferedReader input = new BufferedReader(new FileReader(
                        procFile));
                String line;

                while ((line = input.readLine()) != null) {
                    String[] fields = line.split(" ");
                    String devNode = fields[0];
                    String path = fields[1];
                    if (devNode.contains("block")
                            && !devNode.contains("mtdblock")
                            && sMountPointVolumeMap.containsKey(path)) {
                        String fsFormat = fields[2];
                        // FIXME
                        boolean isSDStorage = (devNode.contains("mmcblk") || path
                                .contains("sdcard"));
                        boolean removable = !(isSDStorage && IfConfig.PRODUCT_BUILT_IN_SDCARD);
                        int descriptionId = getStorageDescriptionResId(path);
                        boolean primary = isSDStorage;
                        IfStorageVolume if_volume = sMountPointVolumeMap
                                .get(path);
                        if (if_volume != null) {
                            if_volume.setDevNode(devNode);
                            sDeviceNodeVolumeMap.put(devNode, if_volume);
                            if_volume.setDescriptionId(descriptionId);
                            if_volume.setFilesystemFormat(fsFormat);
                            if_volume.setRemovable(removable);
                            if_volume.setPrimary(primary);
                        }
                    } else {
                        continue;
                    }
                }

                input.close();
            } catch (IOException ioException) {
                Log.i(TAG, "io error when try to read /proc/mounts.");
            }
        }
    }

    private void initializeIfStorageVolumes() {
        clearIfStorageVolumeInfo();
        ArrayList<Object> storageVolumeList = new ArrayList<Object>();

        Method method_getVolumeList = null;
        try {
            method_getVolumeList = StorageManager.class.getMethod(
                    "getVolumeList", new Class[] {});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        if (method_getVolumeList != null) {
            StorageManager storageManager = (StorageManager) mContext
                    .getSystemService(Context.STORAGE_SERVICE);
            Object storageVolumesArray = null;
            try {
                storageVolumesArray = method_getVolumeList.invoke(
                        storageManager, new Object[] {});
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (storageVolumesArray != null) {
                int length = java.lang.reflect.Array
                        .getLength(storageVolumesArray);
                for (int i = 0; i < length; i++) {
                    storageVolumeList.add(java.lang.reflect.Array.get(
                            storageVolumesArray, i));
                }
            }
        }

        initializeIfStorageVolumesInner(storageVolumeList);
        // For Rock chip solution
        initializeIfStorageVolumesInnerForRKPlatform();
    }

    private void initializeIfStorageVolumesInnerForRKPlatform() {
        String internal_path = "/mnt/internal_sd";
        IfStorageVolume volume = new IfStorageVolume(internal_path, false,
                false, false);
        sMountPointVolumeMap.put(internal_path, volume);
        if (!sStorageVolumes.contains(volume)) {
            sStorageVolumes.add(volume);
        }
        sMountPoints.add(internal_path);

        Method method_getSecondaryStoragePath = null;
        Method method_getInterHardDiskPath = null;
        Method method_getExtUsb0Path = null;
        Method method_getExtUsb1Path = null;
        Method method_getExtUsb2Path = null;
        Method method_getExtUsb3Path = null;
        Method method_getExtUsb4Path = null;
        Method method_getExtUsb5Path = null;

        try {
            method_getExtUsb0Path = Environment.class.getMethod(
                    "getHostStorage_Extern_0_Directory", new Class[] {});
            method_getExtUsb1Path = Environment.class.getMethod(
                    "getHostStorage_Extern_1_Directory", new Class[] {});
            method_getExtUsb2Path = Environment.class.getMethod(
                    "getHostStorage_Extern_2_Directory", new Class[] {});
            method_getExtUsb3Path = Environment.class.getMethod(
                    "getHostStorage_Extern_3_Directory", new Class[] {});
            method_getExtUsb4Path = Environment.class.getMethod(
                    "getHostStorage_Extern_4_Directory", new Class[] {});
            method_getExtUsb5Path = Environment.class.getMethod(
                    "getHostStorage_Extern_5_Directory", new Class[] {});
            method_getSecondaryStoragePath = Environment.class.getMethod(
                    "getSecondVolumeStorageDirectory", new Class[] {});
            method_getInterHardDiskPath = Environment.class.getMethod(
                    "getInterHardDiskStorageDirectory", new Class[] {});
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

        if (method_getSecondaryStoragePath != null) {
            String secondaryPath = null;
            try {
                secondaryPath = ((File) method_getSecondaryStoragePath.invoke(
                        null, null)).getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (secondaryPath != null) {
                IfStorageVolume if_volume = new IfStorageVolume(secondaryPath,
                        false, true, false);
                sMountPointVolumeMap.put(secondaryPath, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(secondaryPath);
            }
        }

        if (method_getInterHardDiskPath != null) {
            String interDiskPath = null;
            try {
                interDiskPath = ((File) method_getInterHardDiskPath.invoke(
                        null, null)).getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (interDiskPath != null) {
                IfStorageVolume if_volume = new IfStorageVolume(interDiskPath,
                        false, false, false);
                sMountPointVolumeMap.put(interDiskPath, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(interDiskPath);
            }
        }

        if (method_getExtUsb0Path != null) {
            String usb0path = null;
            try {
                usb0path = ((File) method_getExtUsb0Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb0path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb0path,
                        false, true, false);
                sMountPointVolumeMap.put(usb0path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb0path);
            }
        }

        if (method_getExtUsb1Path != null) {
            String usb1path = null;
            try {
                usb1path = ((File) method_getExtUsb1Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb1path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb1path,
                        false, true, false);
                sMountPointVolumeMap.put(usb1path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb1path);
            }
        }

        if (method_getExtUsb2Path != null) {
            String usb2path = null;
            try {
                usb2path = ((File) method_getExtUsb2Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb2path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb2path,
                        false, true, false);
                sMountPointVolumeMap.put(usb2path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb2path);
            }
        }

        if (method_getExtUsb3Path != null) {
            String usb3path = null;
            try {
                usb3path = ((File) method_getExtUsb3Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb3path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb3path,
                        false, true, false);
                sMountPointVolumeMap.put(usb3path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb3path);
            }
        }

        if (method_getExtUsb4Path != null) {
            String usb4path = null;
            try {
                usb4path = ((File) method_getExtUsb4Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb4path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb4path,
                        false, true, false);
                sMountPointVolumeMap.put(usb4path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb4path);
            }
        }

        if (method_getExtUsb5Path != null) {
            String usb5path = null;
            try {
                usb5path = ((File) method_getExtUsb5Path.invoke(null, null))
                        .getPath();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            if (usb5path != null) {
                IfStorageVolume if_volume = new IfStorageVolume(usb5path,
                        false, true, false);
                sMountPointVolumeMap.put(usb5path, if_volume);
                if (!sStorageVolumes.contains(if_volume)) {
                    sStorageVolumes.add(if_volume);
                }
                sMountPoints.add(usb5path);
            }
        }
    }

    private void initializeIfStorageVolumesInner(
            ArrayList<Object> android_storageVolumes) {
        boolean androidStorageVolumeAvailable = false;
        Class<?> class_StorageVolume = null;

        if (android_storageVolumes != null && !android_storageVolumes.isEmpty()) {
            try {
                class_StorageVolume = Class
                        .forName("android.os.storage.StorageVolume");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if (class_StorageVolume != null
                    && android_storageVolumes.get(0).getClass()
                            .equals(class_StorageVolume)) {
                androidStorageVolumeAvailable = true;
            }
        }

        // Create IfStorageVolumes by android StorageVolume
        if (androidStorageVolumeAvailable) {
            boolean methodAccessiable = false;
            Method method_getPath = null;
            Method method_isPrimary = null;
            Method method_isRemovable = null;
            Method method_isEmulated = null;
            try {
                method_getPath = class_StorageVolume.getMethod("getPath",
                        new Class[] {});
                method_isPrimary = class_StorageVolume.getMethod("isPrimary",
                        new Class[] {});
                method_isRemovable = class_StorageVolume.getMethod(
                        "isRemovable", new Class[] {});
                method_isEmulated = class_StorageVolume.getMethod("isEmulated",
                        new Class[] {});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }

            if (method_getPath != null && method_isPrimary != null
                    && method_isRemovable != null && method_isEmulated != null) {
                methodAccessiable = true;
            }

            if (methodAccessiable) {
                String path = null;
                Boolean isPrimary = null;
                Boolean isRemovable = null;
                Boolean isEmulated = null;

                for (int i = 0; i < android_storageVolumes.size(); i++) {
                    Object android_volume = android_storageVolumes.get(i);
                    try {
                        path = (String) method_getPath.invoke(android_volume,
                                new Object[] {});
                        isPrimary = (Boolean) method_isPrimary.invoke(
                                android_volume, new Object[] {});
                        isRemovable = (Boolean) method_isRemovable.invoke(
                                android_volume, new Object[] {});
                        isEmulated = (Boolean) method_isEmulated.invoke(
                                android_volume, new Object[] {});
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }

                    if (path != null) {
                        IfStorageVolume if_volume = new IfStorageVolume(path,
                                isPrimary ? isPrimary.booleanValue() : false,
                                isRemovable ? isRemovable.booleanValue()
                                        : false,
                                isEmulated ? isEmulated.booleanValue() : false);
                        sMountPointVolumeMap.put(path, if_volume);
                        if (!sStorageVolumes.contains(if_volume)) {
                            sStorageVolumes.add(if_volume);
                        }
                        sMountPoints.add(path);
                    }
                }

            }
        }

        // Create IfStorageVolumes by proc
        createIfStorageVolumesByProc();

        // Fulfill IfStorageVolume fields by Linux /proc/mounts file
        fulfillIfStorageVolumesByProc();
    }

    protected String cacheId(String path, String idTag, String idValue) {
        String newValue = idValue;
        HashMap<String, String> map = sStorageIdMap.get(idTag);
        if (map == null) { // Can't cache, just return
            return newValue;
        }

        map.put(path, idValue);
        return newValue;
    }
}

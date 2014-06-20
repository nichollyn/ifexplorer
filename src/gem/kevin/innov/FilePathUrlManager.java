package gem.kevin.innov;

import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.util.StorageUtil;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.sparseboolean.ifexplorer.DirectoryMonitor;

import android.util.Log;

public class FilePathUrlManager implements DirectoryMonitor.FileEventHandler {
    public interface FilePathObserver {
        public void notifyOnAllEvents(String path);
    }

    private static final String TAG = "IfExlporer-FilePathUrlManager";

    private static final int BUFFER = 2048;
    public static final int SORT_NONE = 0;
    public static final int SORT_ALPHA = 1;
    private boolean mShowHiddenFiles = false;
    private int mPreSortType = SORT_ALPHA;
    private long mDirSize = 0;
    private ArrayList<String> mUrlHistoryList;

    private int mHistoryPosition = 0;
    private ArrayList<String> mCurrentUrlContent;

    private String mLastMonitoringPath = null;

    private DirectoryMonitor mCurrentDirMonitor = null;
    private FilePathObserver mFilePathObserver;

    private ArrayList<String> mAudioPatternFilePaths = new ArrayList<String>();

    private ArrayList<String> mVideoPatternFilePaths = new ArrayList<String>();
    private ArrayList<String> mImagePatternFilePaths = new ArrayList<String>();
    private ArrayList<String> mApkPatternFilePaths = new ArrayList<String>();
    private HashMap<String, ArrayList<String>> mSearchPatternFilePaths = new HashMap<String, ArrayList<String>>();
    @SuppressWarnings("rawtypes")
    private static final Comparator mAlphComparator = new Comparator<String>() {
        @Override
        public int compare(String arg0, String arg1) {
            return arg0.toLowerCase().compareTo(arg1.toLowerCase());
        }
    };

    public static final String ROOT_DIR = "/";

    public static String buildUrl(String dirPath, String query, String pattern) {
        String field_path = (dirPath != null) ? (URL_FIELD_DIR_PATH
                + URL_KEY_VALUE_DIVIDER + dirPath + URL_FIELD_DIVIDER) : "";
        String field_name = (query != null) ? (URL_FIELD_QUERY
                + URL_KEY_VALUE_DIVIDER + query + URL_FIELD_DIVIDER) : "";
        String field_pattern = (pattern != null) ? pattern + URL_FIELD_DIVIDER
                : "";
        return field_path + field_name + field_pattern;
    }

    public static String getDir(String url) {
        if (isExistingDirPath(url)) {
            return url;
        }

        String fields[] = url.split(URL_FIELD_DIVIDER);
        if (fields != null && fields.length > 0) {
            for (String field : fields) {
                if (field.contains(URL_FIELD_DIR_PATH)) {
                    String[] pathKeyValue = field.split(URL_KEY_VALUE_DIVIDER);
                    if (pathKeyValue != null && pathKeyValue.length == 2) {
                        String[] paths = pathKeyValue[1]
                                .split(URL_MULTI_VALUE_DIVIDER);
                        if (paths != null && paths.length > 0) {
                            // If multiple dirs exist, we choose the first one
                            Log.i(TAG, "Get dir: " + paths[0]);
                            return paths[0];
                        }
                    }
                }
            }
        }

        return null;
    }

    public static String getQueryStr(String url) {
        String fields[] = url.split(URL_FIELD_DIVIDER);
        if (fields != null && fields.length > 0) {
            for (String field : fields) {
                if (field.contains(URL_FIELD_QUERY)) {
                    String[] keyValue = field.split(URL_KEY_VALUE_DIVIDER);
                    if (keyValue != null && keyValue.length == 2) {
                        return keyValue[1];
                    }
                }
            }
        }

        return null;
    }

    public static String getSmartPattern(String url) {
        String fields[] = url.split(URL_FIELD_DIVIDER);
        if (fields != null && fields.length > 0) {
            for (String field : fields) {
                if (field.startsWith(SMART_PREFIX)) {
                    Log.i(TAG, "Smart patter is: " + field);
                    return field;
                }
            }
        }

        return null;
    }

    /** converts integer from wifi manager to an IP address.
     * 
     * @param des
     * @return */
    public static String integerToIPAddress(int ip) {
        String ascii_address = "";
        int[] num = new int[4];

        num[0] = (ip & 0xff000000) >> 24;
        num[1] = (ip & 0x00ff0000) >> 16;
        num[2] = (ip & 0x0000ff00) >> 8;
        num[3] = ip & 0x000000ff;

        ascii_address = num[0] + "." + num[1] + "." + num[2] + "." + num[3];

        return ascii_address;
    }

    public static boolean isExistingDirPath(String path) {
        if (!path.startsWith("/")) {
            return false;
        }

        File file = new File(path);
        return file.exists() && file.isDirectory();
    }

    public static boolean isSearchUrl(String url) {
        if (url == null) {
            return false;
        }

        if (url.contains(URL_FIELD_QUERY + URL_KEY_VALUE_DIVIDER)
                || url.contains(SMART_PREFIX)) {
            return true;
        } else {
            return false;
        }
    }

    // Inspired by org.apache.commons.io.FileUtils.isSymlink()
    private static boolean isSymlink(File file) throws IOException {
        File fileInCanonicalDir = null;
        if (file.getParent() == null) {
            fileInCanonicalDir = file;
        } else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }
        return !fileInCanonicalDir.getCanonicalFile().equals(
                fileInCanonicalDir.getAbsoluteFile());
    }

    private final Comparator size = new Comparator<String>() {
        @Override
        public int compare(String arg0, String arg1) {
            String dir = getCurrentUrl();
            Long first = new File(dir + "/" + arg0).length();
            Long second = new File(dir + "/" + arg1).length();

            return first.compareTo(second);
        }
    };
    private final Comparator type = new Comparator<String>() {
        @Override
        public int compare(String arg0, String arg1) {
            String ext = null;
            String ext2 = null;
            int ret;

            try {
                ext = arg0.substring(arg0.lastIndexOf(".") + 1, arg0.length())
                        .toLowerCase();
                ext2 = arg1.substring(arg1.lastIndexOf(".") + 1, arg1.length())
                        .toLowerCase();

            } catch (IndexOutOfBoundsException e) {
                return 0;
            }
            ret = ext.compareTo(ext2);

            if (ret == 0) {
                return arg0.toLowerCase().compareTo(arg1.toLowerCase());
            }

            return ret;
        }
    };

    public static final String PATTERN_ALL = "*";

    /* smart folder path suffix */
    public static final String SMART_APK = "type: apk";
    public static final String SMART_MOVIES = "type: video";
    public static final String SMART_IMAGE = "type: image";
    public static final String SMART_MUSIC = "type: audio";
    public static final String SMART_FILE = "type: file";

    public static final String SMART_PREFIX = "type: ";

    /* external storage volumes mount points parent directory */

    public static final String URL_MULTI_VALUE_DIVIDER = " + ";

    public static final String URL_FIELD_QUERY = "query";

    public static final String URL_FIELD_DIR_PATH = "dir";

    public static final String URL_KEY_VALUE_DIVIDER = ": ";

    public static final String URL_FIELD_DIVIDER = " & ";

    public static final String URL_PATH_APK = URL_FIELD_DIVIDER + SMART_APK
            + URL_FIELD_DIVIDER;
    public static final String URL_PATH_MOVIES = URL_FIELD_DIVIDER
            + SMART_MOVIES + URL_FIELD_DIVIDER;
    public static final String URL_PATH_IMAGE = URL_FIELD_DIVIDER + SMART_IMAGE
            + URL_FIELD_DIVIDER;
    public static final String URL_PATH_MUSIC = URL_FIELD_DIVIDER + SMART_MUSIC
            + URL_FIELD_DIVIDER;
    public static final String URL_PATH_FILE = URL_FIELD_DIVIDER + SMART_FILE
            + URL_FIELD_DIVIDER;

    private static FilePathUrlManager sFilePathUrlManager;

    public static String buildSearchId(String searchStr, String searchDir) {
        return searchStr + URL_KEY_VALUE_DIVIDER + searchDir;
    }

    public static FilePathUrlManager getLocalFileManager(String initLocation) {
        return new FilePathUrlManager(initLocation);
    }

    public static FilePathUrlManager getSingleton(String initLocation) {
        if (sFilePathUrlManager == null) {
            sFilePathUrlManager = new FilePathUrlManager(initLocation);
        }

        return sFilePathUrlManager;
    }

    public static String parseSearchDir(String searchId) {
        String keyValue[] = searchId.split(URL_KEY_VALUE_DIVIDER);
        if (keyValue != null && keyValue.length >= 2) {
            return keyValue[1];
        }

        return null;
    }

    public static String parseSearchStr(String searchId) {
        String keyValue[] = searchId.split(URL_KEY_VALUE_DIVIDER);
        if (keyValue != null && keyValue.length >= 1) {
            return keyValue[0];
        }

        return null;
    }

    /** Constructs an object of the class <br>
     * this class uses a stack to handle the navigation of directories. */
    private FilePathUrlManager(String initLocation) {
        mCurrentUrlContent = new ArrayList<String>();
        mUrlHistoryList = new ArrayList<String>();

        mUrlHistoryList.add(initLocation);
    }

    /** @param path */
    public void createZipFile(String path) {
        File dir = new File(path);
        String[] list = dir.list();
        String name = path.substring(path.lastIndexOf("/"), path.length());
        String _path;

        if (!dir.canRead() || !dir.canWrite()) {
            return;
        }

        int len = list.length;

        if (path.charAt(path.length() - 1) != '/') {
            _path = path + "/";
        } else {
            _path = path;
        }

        try {
            ZipOutputStream zip_out = new ZipOutputStream(
                    new BufferedOutputStream(new FileOutputStream(_path + name
                            + ".zip"), BUFFER));

            for (int i = 0; i < len; i++) {
                zip_folder(new File(_path + list[i]), zip_out);
            }

            zip_out.close();

        } catch (FileNotFoundException e) {
            Log.e("File not found", e.getMessage());

        } catch (IOException e) {
            Log.e("IOException", e.getMessage());
        }
    }

    /** @param zip_file
     * @param directory */
    public void extractZipFiles(String zip_file, String directory) {
        byte[] data = new byte[BUFFER];
        String name, path, zipDir;
        ZipEntry entry;
        ZipInputStream zipstream;

        if (!(directory.charAt(directory.length() - 1) == '/')) {
            directory += "/";
        }

        if (zip_file.contains("/")) {
            path = zip_file;
            name = path.substring(path.lastIndexOf("/") + 1, path.length() - 4);
            zipDir = directory + name + "/";

        } else {
            path = directory + zip_file;
            name = path.substring(path.lastIndexOf("/") + 1, path.length() - 4);
            zipDir = directory + name + "/";
        }

        new File(zipDir).mkdir();

        try {
            zipstream = new ZipInputStream(new FileInputStream(path));

            while ((entry = zipstream.getNextEntry()) != null) {
                String buildDir = zipDir;
                String[] dirs = entry.getName().split("/");

                if (dirs != null && dirs.length > 0) {
                    for (int i = 0; i < dirs.length - 1; i++) {
                        buildDir += dirs[i] + "/";
                        new File(buildDir).mkdir();
                    }
                }

                int read = 0;
                FileOutputStream out = new FileOutputStream(zipDir
                        + entry.getName());
                while ((read = zipstream.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, read);
                }

                zipstream.closeEntry();
                out.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** @param zipName
     * @param toDir
     * @param fromDir */
    public void extractZipFilesFromDir(String zipName, String toDir,
            String fromDir) {
        if (!(toDir.charAt(toDir.length() - 1) == '/')) {
            toDir += "/";
        }
        if (!(fromDir.charAt(fromDir.length() - 1) == '/')) {
            fromDir += "/";
        }

        String org_path = fromDir + zipName;

        extractZipFiles(org_path, toDir);
    }

    public String getCurrentUrl() {
        return mUrlHistoryList.get(mHistoryPosition);
    }

    public int getCurrentUrlPosition() {
        return mHistoryPosition;
    }

    public int getHistoryPosition() {
        return mHistoryPosition;
    }

    public ArrayList<String> getNextUrlContent(String url, boolean isNewUrl,
            boolean forward) {
        if (url != null && !url.equals(getCurrentUrl())) {
            if (isNewUrl) {
                int size = mUrlHistoryList.size();
                for (int i = size - 1; i > mHistoryPosition; i--) {
                    mUrlHistoryList.remove(i);
                }
                mUrlHistoryList.add(url);
                mHistoryPosition = mUrlHistoryList.size() - 1;
            }
        }

        if (forward) {
            mHistoryPosition++;
            if (mHistoryPosition >= mUrlHistoryList.size()) {
                mHistoryPosition = mUrlHistoryList.size() - 1;
            }
        }

        return populateCurrentUrlContent();
    }

    public ArrayList<String> getPreviousUrlContent() {
        mHistoryPosition--;
        if (mHistoryPosition < 0) {
            mHistoryPosition = 0;
        }

        return populateCurrentUrlContent();
    }

    public ArrayList<String> getUrlHistory() {
        return mUrlHistoryList;
    }

    public int getUrlHistoryCount() {
        return mUrlHistoryList.size();
    }

    // implements DirectoryMonitor.FileEventHandler::onAllEvents
    @Override
    public void onAllEvents(String path) {
        if (mFilePathObserver != null) {
            mFilePathObserver.notifyOnAllEvents(path);
        }
    }

    public ArrayList<String> refreshUrlContent() {
        return getNextUrlContent(getCurrentUrl(), false, false);
    }

    /*
     * kevin@xmic search file of specific type in a directory and return
     * sub-directories for caller to relay following searches make sure reserved
     * dirs added to sub-directories.
     */
    public ArrayList<String> relaySearchFiles(String topDir, String searchStr,
            ArrayList<String> typeList, ArrayList<String> accumulateResult,
            ArrayList<String> currentResult,
            ArrayList<String> reservedSearchDirs) {
        Log.i(TAG, "relaySearchFiles - topDir: " + topDir + " searchStr: "
                + searchStr);
        ArrayList<String> subDirs = new ArrayList<String>();

        File root_dir = new File(topDir);
        String[] list = root_dir.list();

        if (list != null && root_dir.canRead()) {
            int len = list.length;

            for (int i = 0; i < len; i++) {
                File check = new File(topDir + "/" + list[i]);

                // skip symbol link
                try {
                    if (isSymlink(check)) {
                        continue;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to check symbol link!");
                }

                if (check.isFile()) {
                    if (typeList != null) {
                        String extension = DataUtil
                                .getFileExtensionWithoutDot(check.getPath());
                        if (typeList.contains(extension.toLowerCase())) {
                            if (PATTERN_ALL.equals(searchStr)) {
                                Log.i(TAG, "ADD result: " + check.getPath()
                                        + " as * match");
                                accumulateResult.add(check.getPath());
                                currentResult.add(check.getPath());
                            } else {
                                if (searchStr != null
                                        && check.getName().contains(searchStr)) {
                                    accumulateResult.add(check.getPath());
                                    currentResult.add(check.getPath());
                                }
                            }
                        }
                    } else {
                        if (searchStr != null
                                && check.getName().contains(searchStr)) {
                            accumulateResult.add(check.getPath());
                            currentResult.add(check.getPath());
                        }
                    }
                }
                if (check.isDirectory()) {
                    if (StorageUtil.getExcludeSearchPath().contains(
                            check.getAbsolutePath())
                            || StorageUtil
                                    .isExcludeSearchPath(
                                            check.getAbsolutePath(),
                                            reservedSearchDirs)) {
                        continue;
                    } else {
                        if (searchStr != null
                                && check.getName().contains(searchStr)) {
                            accumulateResult.add(check.getPath());
                            currentResult.add(check.getPath());
                        }
                        if (check.canRead() && !topDir.equals("/")) {
                            subDirs.add(check.getPath());
                        }
                    }
                }
            }
        }

        return subDirs;
    }

    public void removeInvalidUrls(String invalidUrlPrefix) {
        @SuppressWarnings("unchecked")
        ArrayList<String> clone = (ArrayList<String>) mUrlHistoryList.clone();
        for (int i = 0; i < clone.size(); i++) {
            String url = clone.get(i);
            if (url.startsWith(invalidUrlPrefix)) {
                mUrlHistoryList.remove(url);
            }
        }

        mHistoryPosition = mUrlHistoryList.size() > 0 ? mUrlHistoryList.size() - 1
                : 0;
    }

    /*
     * @deprecated
     * 
     * Search file of specific type in a directory
     * recursively and make sure reserved dirs be searched if provided This
     * method might be time-consuming, so you can use #relaySearchFilesOfType to
     * get search right after one level directory scanned.
     */
    public boolean searchFilesOfType(String topDir, int type,
            ArrayList<String> result, ArrayList<String> reservedSearchDirs) {
        ArrayList<String> typeList = null;
        switch (type) {
        case StorageUtil.TYPE_AUDIO:
            typeList = DataUtil.getSupportedAudioFileExtensions();
            break;
        case StorageUtil.TYPE_VIDEO:
            typeList = DataUtil.getSupportedVideoFileExtensions();
            break;
        case StorageUtil.TYPE_IMAGE:
            typeList = DataUtil.getSupportedImageFileExtensions();
            break;
        case StorageUtil.TYPE_APKFILE:
            typeList = DataUtil.getSupportedAppInstallerFileExtensions();
            break;
        default:
            // do nothing
            return false;
        }

        File root_dir = new File(topDir);
        String[] list = root_dir.list();

        if (list != null && root_dir.canRead()) {
            int len = list.length;

            for (int i = 0; i < len; i++) {
                File check = new File(topDir + "/" + list[i]);

                // skip symbol link and hidden files
                try {
                    if (isSymlink(check) || check.isHidden()) {
                        continue;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to check symbol link!");
                }

                if (check.isFile()) {
                    String extension = DataUtil
                            .getFileExtensionWithoutDot(check.getPath());
                    if (typeList.contains(extension.toLowerCase())) {
                        result.add(check.getPath());
                    }
                }
                if (check.isDirectory()) {
                    if (StorageUtil.getExcludeSearchPath().contains(
                            check.getAbsolutePath())
                            || StorageUtil
                                    .isExcludeSearchPath(
                                            check.getAbsolutePath(),
                                            reservedSearchDirs)) {
                        continue;
                    } else {
                        if (check.canRead() && !topDir.equals("/")) {
                            searchFilesOfType(check.getAbsolutePath(), type,
                                    result, reservedSearchDirs);
                        }
                    }
                }
            }
        }

        return true;
    }

    public void setApkPatternPath(ArrayList<String> apkPatternFilePaths) {
        mApkPatternFilePaths = apkPatternFilePaths;
    }

    public void setAudioPatternPath(ArrayList<String> audioPatternFilePaths) {
        mAudioPatternFilePaths = audioPatternFilePaths;
    }

    public void setFilePathObserver(FilePathObserver observer) {
        mFilePathObserver = observer;
    }

    public void setHistoryPosition(int position) {
        mHistoryPosition = position;
    }

    public void setImagePatternPath(ArrayList<String> imagePatternFilePaths) {
        mImagePatternFilePaths = imagePatternFilePaths;
    }

    public void setSearchFilePatternPath(
            HashMap<String, ArrayList<String>> searchPatternFilePaths) {
        mSearchPatternFilePaths = searchPatternFilePaths;
    }

    public void setShowHiddenFiles(boolean choice) {
        mShowHiddenFiles = choice;
    }

    public void setPreSortType(int type) {
        mPreSortType = type;
    }

    public int getPreSortType() {
        return mPreSortType;
    }

    public void setUrlHistory(ArrayList<String> history) {
        mUrlHistoryList = history;
    }

    public void setVideoPatternPath(ArrayList<String> videoPatternFilePaths) {
        mVideoPatternFilePaths = videoPatternFilePaths;
    }

    public void updateCachedFilePaths(String pattern,
            HashMap<String, String> explicitChangeSets) {
        // FIXME
        // There might be concurrent exception risk here
        if (explicitChangeSets != null && explicitChangeSets.size() > 0) {
            ArrayList<String> targetCaches;
            if (FilePathUrlManager.SMART_MUSIC.equals(pattern)) {
                targetCaches = mAudioPatternFilePaths;
            } else if (FilePathUrlManager.SMART_IMAGE.equals(pattern)) {
                targetCaches = mImagePatternFilePaths;
            } else if (FilePathUrlManager.SMART_MOVIES.equals(pattern)) {
                targetCaches = mVideoPatternFilePaths;
            } else if (FilePathUrlManager.SMART_APK.equals(pattern)) {
                targetCaches = mApkPatternFilePaths;
            } else {
                return;
            }

            if (targetCaches == null) {
                return;
            }

            for (String oldValue : explicitChangeSets.keySet()) {
                if (targetCaches.contains(oldValue)) {
                    int index = targetCaches.indexOf(oldValue);
                    String newValue = explicitChangeSets.get(oldValue);
                    if (newValue != null) {
                        targetCaches.set(index,
                                explicitChangeSets.get(oldValue));
                    } else {
                        targetCaches.remove(oldValue);
                    }
                } else {
                    if (FileUtil.NEW_GENERATED_FILE.equals(oldValue)) {
                        targetCaches.add(explicitChangeSets.get(oldValue));
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String> populateCurrentUrlContent() {
        if (!mCurrentUrlContent.isEmpty()) {
            mCurrentUrlContent.clear();
        }

        // Smart folder mode
        // Return clone list instead the pattern file path list itself,
        // because they maybe get modified by setter method when caller of this
        // method is using the list
        if (FilePathUrlManager.SMART_MUSIC
                .equals(getSmartPattern(getCurrentUrl()))) {
            return (ArrayList<String>) mAudioPatternFilePaths.clone();
        } else if (FilePathUrlManager.SMART_IMAGE
                .equals(getSmartPattern(getCurrentUrl()))) {
            return (ArrayList<String>) mImagePatternFilePaths.clone();
        } else if (FilePathUrlManager.SMART_MOVIES
                .equals(getSmartPattern(getCurrentUrl()))) {
            return (ArrayList<String>) mVideoPatternFilePaths.clone();
        } else if (FilePathUrlManager.SMART_APK
                .equals(getSmartPattern(getCurrentUrl()))) {
            return (ArrayList<String>) mApkPatternFilePaths.clone();
        } else if (FilePathUrlManager.SMART_FILE
                .equals(getSmartPattern(getCurrentUrl()))) {
            String searchId = buildSearchId(getQueryStr(getCurrentUrl()),
                    getDir(getCurrentUrl()));
            Log.i(TAG, "search id: " + searchId);
            ArrayList<String> searchRecord = mSearchPatternFilePaths
                    .get(searchId);
            if (searchRecord != null) {
                return (ArrayList<String>) searchRecord.clone();
            } else {
                return null;
            }
        }

        // Common folder mode
        File file = new File(getCurrentUrl());

        if (file.exists() && file.canRead()) {
            String currentPath = file.getPath();
            if (!currentPath.equals(mLastMonitoringPath)) {
                // monitoring the new current directory
                if (mCurrentDirMonitor != null) {
                    mCurrentDirMonitor.stopWatching();
                }
                mCurrentDirMonitor = new DirectoryMonitor(currentPath,
                        FilePathUrlManager.this);
                mCurrentDirMonitor.startWatching();
                mLastMonitoringPath = currentPath;
            }

            String[] children = file.list();
            if (children != null) {
                int len = children.length;

                /* add files/folder to ArrayList depending on hidden status */
                for (int i = 0; i < len; i++) {
                    if (!mShowHiddenFiles) {
                        if (children[i].toString().charAt(0) != '.') {
                            if (currentPath.endsWith("/")) {
                                mCurrentUrlContent.add(currentPath
                                        + children[i]);
                            } else {
                                mCurrentUrlContent.add(currentPath + "/"
                                        + children[i]);
                            }
                        }
                    } else {
                        mCurrentUrlContent.add(file.getPath() + "/"
                                + children[i]);
                    }
                }
            }

            /* sort the ArrayList that was made from above for loop */
            switch (mPreSortType) {
            case SORT_NONE:
                // no sorting needed
                break;
            case SORT_ALPHA:
                Object[] tt = mCurrentUrlContent.toArray();
                mCurrentUrlContent.clear();

                Arrays.sort(tt, mAlphComparator);

                for (Object a : tt) {
                    mCurrentUrlContent.add((String) a);
                }
                break;
            }
        }

        return mCurrentUrlContent;
    }

    private void zip_folder(File file, ZipOutputStream zout) throws IOException {
        byte[] data = new byte[BUFFER];
        int read;

        if (file.isFile()) {
            ZipEntry entry = new ZipEntry(file.getName());
            zout.putNextEntry(entry);
            BufferedInputStream instream = new BufferedInputStream(
                    new FileInputStream(file));

            while ((read = instream.read(data, 0, BUFFER)) != -1) {
                zout.write(data, 0, read);
            }

            zout.closeEntry();
            instream.close();

        } else if (file.isDirectory()) {
            String[] list = file.list();
            int len = list.length;

            for (int i = 0; i < len; i++) {
                zip_folder(new File(file.getPath() + "/" + list[i]), zout);
            }
        }
    }
}

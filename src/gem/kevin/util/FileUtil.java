package gem.kevin.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.ArrayList;

import com.sparseboolean.ifexplorer.R;

import android.content.res.Resources;
import android.util.Log;

public final class FileUtil {
    public interface CopyCallback {
        public boolean copyProgressCanceled();

        public boolean copyProgressPaused();

        public String generateCopyName(String srcName, int existDuplicateCount);

        public Object getPauseLock();

        public void onBytesCopied(String srcFilePath, long copiedBytes);

        public void onCopyError(String srcFilePath, String targetFilePath,
                int errorCode);

        public void onCopyProgressFinished(String srcFilePath,
                String copiedFilePath, long copiedBytes);

        public void onExistDirDetected(String srcDirPath, String existDirPath);

        public void onExistFileDetected(String srcFilePath, String existFilePath);

        public void onPercentageCopied(String srcFilePath, int copiedPercentage);

        public boolean overwriteExist();

        public void pause();

        public void saveCopyProgress(String srcFilePath, long position);

        public boolean skipCopyError();

        public boolean skipExist();
    }

    public interface DeleteCallback {
        public boolean deleteProgressCanceled();

        public boolean deleteProgressPaused();

        public Object getPauseLock();

        public void onDeleteError(String filePath, int errorCode);

        public void onDeleteFinished(String filePath);

        public void pause();

        public boolean skipDeleteError();
    }

    public interface RenameCallback {
        public Object getPauseLock();

        public void onRenameError(String srcFilePath, String targetFilePath,
                int errorCode);

        public void onRenameFinished(String srcFilePath, String renamedFilePath);

        public void pause();

        public boolean renameProgressCanceled();

        public boolean renameProgressPaused();

        public boolean skipRenameError();
    }

    public interface SearchCallback {
        public void onDirSearched(String searchedDirPath);

        public void onDirSearching(String searchingDirPath);

        public void onSearchCancelled();

        public void onSearchFinished();

        public boolean searchProgressCanceled();
    }

    public interface SimpleCopyCallback {
        public void onFileCopied(String srcFilePath, String copiedFilePath);
    }

    public interface SimpleCreateCallback {
        public void onFileCreated(String filePath);
    }

    public interface SimpleDeleteCallback {
        public void onFileDeleted(String filePath);
    }

    public interface SimpleRenameCallback {
        public void onFileRenamed(String originalFilePath, String newFilePath);
    }

    private static final String TAG = "FileUtil";
    private static final boolean KLOG = false;
    private static final boolean DEBUG = true;
    public static final int OP_SUCCESS = 0;

    public static final int OP_CANCELED = 1;
    public static final int OP_PAUSED = 2;
    public static final int OP_SKIP = 3;
    public static final int OP_PENDING = 4;
    public static final int ERROR_FILE_EXISTS = -1;
    public static final int ERROR_DIR_EXISTS = -2;
    public static final int ERROR_TARGET_PERMISSION_DENY = -3;
    public static final int ERROR_SOURCE_READ_DENY = -4;
    public static final int ERROR_SOURCE_WRITE_DENY = -5;
    public static final int ERROR_INVALID_SRC_PATH = -6;
    public static final int ERROR_INVALID_DEST_PATH = -7;
    public static final int ERROR_UNKNOWN_TYPE = -8;
    public static final int ERROR_IO_ERROR = -9;
    public static final int ERROR_SRC_LOCKED = -10;
    public static final int ERROR_DEST_LOCKED = -11;
    public static final int ERROR_RENAME_FAILED = -12;
    public static final int ERROR_RENAME_AS_MOVE_FAILED = -13;
    public static final int ERROR_DELETE_FAILED = -14;
    public static final int ERROR_DELETE_SUB_FAILED = -15;
    public static final int ERROR_SEEK_OVERFLOW = -16;
    public static final int ERROR_COPY_SUBFILES_FAILED = -17;

    public static final int ERROR_EMPTY_NAME = -18;
    public static final int ERROR_OP_UNAVAILABLE = -19;
    public static final int ERROR_NO_SPACE = -20;
    // ...
    public static final int ERROR_UNKNOWN = -21;

    public static final int FILE_TYPE_EMPTY = 0;
    public static final int FILE_TYPE_FILE = 1;
    public static final int FILE_TYPE_DIRECTORY = 2;

    public static final int FILE_TYPE_FILESET = 3;
    public static final String COPYING_FILE_SUFFIX = ".copying";
    public static final String JAVA_ERRNO_NOSPACE = "ENOSPC";

    public static final String FILE_PATTERN_ALL = "*";
    public static final String NEW_GENERATED_FILE = "new_generated_file$";

    private static final int KB = 1024;

    private static final int MG = KB * KB;

    private static final int GB = MG * KB;

    public static void closeQuietly(Closeable... closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    Log.i("TAG", "Catch IOException when try to close: "
                            + closeable.toString());
                    e.printStackTrace();
                }
            }
        }
    }

    public static int createFile(String path, String name, int fileType,
            SimpleCreateCallback createCallback) {
        Log.i(TAG, "create: " + name + " on path:" + path);
        if (path == null) {
            Log.i(TAG, "Failed to create file due to null path!");
            return ERROR_INVALID_SRC_PATH;
        } else if (name == null || name.isEmpty()) {
            return ERROR_EMPTY_NAME;
        }

        int len = path.length();
        if (len < 1 || len < 1) {
            return ERROR_INVALID_SRC_PATH;
        }

        if (path.charAt(len - 1) != '/') {
            path += "/";
        }

        File target = new File(path + name);
        if (target.exists() && target.isFile()) {
            return ERROR_FILE_EXISTS;
        } else if (target.exists() && target.isDirectory()) {
            return ERROR_DIR_EXISTS;
        }
        switch (fileType) {
        case FILE_TYPE_DIRECTORY:
            if (target.mkdirs()) {
                if (createCallback != null) {
                    createCallback.onFileCreated(target.getAbsolutePath());
                }

                return OP_SUCCESS;
            } else {
                return ERROR_TARGET_PERMISSION_DENY;
            }
        case FILE_TYPE_FILE:
            try {
                if (target.createNewFile()) {
                    if (createCallback != null) {
                        createCallback.onFileCreated(target.getAbsolutePath());
                    }

                    return OP_SUCCESS;
                } else {
                    return ERROR_TARGET_PERMISSION_DENY;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return ERROR_IO_ERROR;
            }
        default:
            return ERROR_UNKNOWN_TYPE;
        }
    }

    public static int deleteFile(String filePath, boolean continueOnError,
            DeleteCallback deleteCallback) throws Exception {
        if (deleteCallback == null) {
            throw new Exception("Can't do delete!");
        }

        // Check if operation canceled at first
        if (deleteCallback.deleteProgressCanceled()) {
            return OP_CANCELED;
        }

        final Object pauseLock = deleteCallback.getPauseLock();
        // Wait if paused
        synchronized (pauseLock) {
            while (deleteCallback.deleteProgressPaused()) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        File fileToDelete = new File(filePath);

        if (!fileToDelete.exists()) {
            if (DEBUG) {
                Log.i(TAG, "File:" + filePath
                        + " doesn't exists, can't delete.");
            }
            deleteCallback.onDeleteError(filePath, ERROR_INVALID_SRC_PATH);
            return ERROR_INVALID_SRC_PATH;
        }

        if (!fileToDelete.canWrite()) {
            if (DEBUG) {
                Log.i(TAG, "Permission deny to delete file:" + fileToDelete);
            }

            if (continueOnError) {
                Log.i(TAG, "Continue on delete error");
                return OP_SKIP;
            } else {
                deleteCallback.pause();
                deleteCallback.onDeleteError(filePath, ERROR_SOURCE_WRITE_DENY);
                synchronized (pauseLock) {
                    while (deleteCallback.deleteProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (deleteCallback.skipDeleteError()) {
                    return OP_SKIP;
                } else {
                    return ERROR_DELETE_FAILED;
                }
            }
        }

        // Empty the target at first if it is a directory
        if (fileToDelete.isDirectory()) {
            File[] files = fileToDelete.listFiles();
            for (File file : files) {
                String path = file.getAbsolutePath();
                int result = deleteFile(path, deleteCallback.skipDeleteError(),
                        deleteCallback);
                if (result == OP_CANCELED) {
                    return OP_CANCELED;
                }
            }
        }

        if (fileToDelete.delete()) {
            deleteCallback.onDeleteFinished(filePath);
            return OP_SUCCESS;
        } else {
            deleteCallback.onDeleteError(filePath, ERROR_DELETE_FAILED);
            return ERROR_DELETE_FAILED;
        }
    }

    public static String formattedSizeStr(long size) {
        if (size > GB) {
            return String.format("%.2f GB", (double) size / GB);
        } else if (size < GB && size > MG) {
            return String.format("%.2f MB", (double) size / MG);
        } else if (size < MG && size > KB) {
            return String.format("%.2f KB", (double) size / KB);
        } else {
            return String.format("%.2f B", (double) size);
        }
    }

    public static String generateCopyName(String parentDirPath, String srcName,
            CopyCallback copyCallback) {
        int existCount = 1;
        String copyName = copyCallback.generateCopyName(srcName, existCount);
        while (isExistingFilePath(parentDirPath + "/" + copyName)) {
            copyName = copyCallback.generateCopyName(srcName, existCount++);
        }

        return copyName;
    }

    public static String getDefaultFileOpErrStr(Resources resources,
            int errorCode) {
        String errStr;

        switch (errorCode) {
        case FileUtil.ERROR_INVALID_SRC_PATH:
            errStr = resources.getString(R.string.error_invalid_src_path);
            break;
        case FileUtil.ERROR_EMPTY_NAME:
            errStr = resources.getString(R.string.error_empty_name);
            break;
        case FileUtil.ERROR_FILE_EXISTS:
            errStr = resources.getString(R.string.error_file_exists);
            break;
        case FileUtil.ERROR_DIR_EXISTS:
            errStr = resources.getString(R.string.error_folder_exists);
            break;
        case FileUtil.ERROR_TARGET_PERMISSION_DENY:
        case FileUtil.ERROR_SOURCE_READ_DENY:
        case FileUtil.ERROR_SOURCE_WRITE_DENY:
            errStr = resources.getString(R.string.permission_insufficient);
            break;
        case FileUtil.ERROR_IO_ERROR:
            errStr = resources.getString(R.string.error_io_occur);
            break;
        case FileUtil.ERROR_UNKNOWN_TYPE:
            errStr = resources.getString(R.string.error_unsupported_type);
            break;
        case FileUtil.ERROR_RENAME_FAILED:
            errStr = resources.getString(R.string.error_invalid_target_path);
            break;
        default:
            errStr = resources.getString(R.string.error_unknown);
        }

        return errStr;
    }

    public static String getFileName(String filePath) {
        if (filePath.equals("/")) {
            return "/";
        } else {
            String fileName = filePath.substring(filePath.lastIndexOf("/") + 1,
                    filePath.length());
            return fileName;
        }
    }

    public static ArrayList<String> getFilePathTree(String currentPath) {
        if (!isExistingFilePath(currentPath)) {
            return null;
        }

        ArrayList<String> tree = new ArrayList<String>();
        tree.add(0, currentPath);
        if (currentPath.equals("/")) {
            return tree;
        } else {
            String parent = currentPath.substring(0,
                    currentPath.lastIndexOf("/"));
            while (parent.contains("/") && parent.length() > 1) {
                tree.add(0, parent);

                parent = parent.substring(0, parent.lastIndexOf("/"));
            }
        }

        tree.add(0, "/");

        return tree;
    }

    public static String getParentDirPath(String filePath) {
        if (!isExistingFilePath(filePath)) {
            return null;
        } else {
            if (filePath.length() > 1) {
                String nonSlashEnd = filePath.endsWith("/") ? filePath
                        .substring(0, filePath.lastIndexOf("/")) : filePath;
                String parentDir = nonSlashEnd.substring(0,
                        nonSlashEnd.lastIndexOf("/"));
                // Special handle for root directory
                parentDir = parentDir.isEmpty() ? "/" : parentDir;

                return parentDir;
            }
        }

        return null;
    }

    public static long getFileSize(String filePath) {
        if (!isExistingFilePath(filePath)) {
            return -1;
        } else {
            long size = 0;
            File file = new File(filePath);
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) {
                    return -1;
                }
                for (File child : children) {
                    long childSize = getFileSize(child.getPath());
                    size += ((childSize > 0) ? childSize : 0);
                }
            } else {
                size = file.length();
            }

            return size;
        }
    }

    public static long getLastModifyTime(String filePath) {
        File file = new File(filePath);

        return file.lastModified();
    }

    public static long getSubFileCount(String filePath) {
        if (!isExistingFilePath(filePath)) {
            return -1;
        } else {
            long count = 1;
            File file = new File(filePath);
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children == null) {
                    return -1;
                }
                for (File child : children) {
                    long childCount = getSubFileCount(child.getPath());
                    count += ((childCount > 1) ? childCount : 1);
                }
            } else {
                count = 1;
            }

            return count;
        }
    }

    public static boolean hasFullReadPermission(String dirPath,
            ArrayList<String> noFullPermission) {
        if (!isExistingFilePath(dirPath)) {
            return false;
        }
        if (noFullPermission == null) {
            noFullPermission = new ArrayList<String>();
        }

        File file = new File(dirPath);
        if (file.isDirectory() && file.canRead()) {
            File[] children = file.listFiles();
            boolean result = true;
            for (File child : children) {
                if (!hasFullReadPermission(child.getAbsolutePath(),
                        noFullPermission)) {
                    noFullPermission.add(child.getAbsolutePath());
                    result = false;
                }
            }

            return result;
        } else if (file.isFile() && file.canRead()) {
            return true;
        } else {
            noFullPermission.add(dirPath);
            return false;
        }
    }

    public static boolean hasFullReadWritePermission(String dirPath,
            ArrayList<String> noFullPermission) {
        if (!isExistingFilePath(dirPath)) {
            return false;
        }
        if (noFullPermission == null) {
            noFullPermission = new ArrayList<String>();
        }

        File file = new File(dirPath);
        if (file.isDirectory() && file.canRead() && file.canWrite()) {
            File[] children = file.listFiles();
            boolean result = true;
            for (File child : children) {
                if (!hasFullReadWritePermission(child.getAbsolutePath(),
                        noFullPermission)) {
                    noFullPermission.add(child.getAbsolutePath());
                    result = false;
                }
            }

            return result;
        } else if (file.isFile() && file.canRead() && file.canWrite()) {
            return true;
        } else {
            noFullPermission.add(dirPath);
            return false;
        }
    }

    public static boolean hasReadPermission(String path) {
        if (!isExistingFilePath(path)) {
            return false;
        }

        File file = new File(path);
        return file.canRead() ? true : false;
    }

    public static boolean hasWritePermission(String path) {
        if (!isExistingFilePath(path)) {
            return false;
        }

        File file = new File(path);
        return file.canWrite() ? true : false;
    }

    public static int interruptibleCopy(String srcPath,
            String targetParentDirPath, long position, boolean overwriteExist,
            boolean skipExist, boolean continueOnError,
            CopyCallback copyCallback, boolean copyForMove) throws Exception {
        return interruptibleCopy(srcPath, targetParentDirPath, null, position,
                overwriteExist, skipExist, continueOnError, copyCallback,
                copyForMove);
    }

    public static int interruptibleCopy(String srcPath,
            String targetParentDirPath, String targetName, long position,
            boolean overwriteExist, boolean skipExist, boolean continueOnError,
            CopyCallback copyCallback, boolean copyForMove) throws Exception {
        if (KLOG) {
            Log.i(TAG, "interruptibleCopy");
        }
        if (copyCallback == null || srcPath == null
                || targetParentDirPath == null) {
            throw new Exception("Can't do interruptible copy!");
        }

        if (copyCallback.copyProgressCanceled()) {
            return OP_CANCELED;
        }

        final Object pauseLock = copyCallback.getPauseLock();
        // Wait if paused
        synchronized (pauseLock) {
            while (copyCallback.copyProgressPaused()) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        File srcFile = new File(srcPath);
        File targetDir = new File(targetParentDirPath);

        if (copyForMove && !srcFile.canWrite()) {
            Log.i(TAG, "src file: " + srcFile + " can't move!");
            if (continueOnError) {
                return OP_SKIP;
            } else {
                copyCallback
                        .onCopyError(srcPath, null, ERROR_SOURCE_WRITE_DENY);
                copyCallback.pause();
                // Wait if paused
                synchronized (pauseLock) {
                    while (copyCallback.copyProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Check if we should skip if procedure escape from a pause
                // state
                // caused by copy error and then continue or cancel
                if (copyCallback.skipCopyError()) {
                    return OP_SKIP;
                } else {
                    return OP_CANCELED;
                }
            }
        }

        if (srcFile.isFile() && srcFile.canRead() && targetDir.isDirectory()
                && targetDir.canWrite()) {
            String copyingFileName = (targetName != null) ? targetName
                    : getFileName(srcPath);
            String copyingFilePath = targetParentDirPath + "/"
                    + copyingFileName;
            if (copyingFilePath.equals(srcPath)) {
                copyingFileName = generateCopyName(targetParentDirPath,
                        copyingFileName, copyCallback);
                copyingFilePath = targetParentDirPath + "/" + copyingFileName;
            }

            File copyingFile = new File(copyingFilePath);
            if (copyingFile.exists()) {
                if (skipExist) {
                    return OP_SKIP;
                } else {
                    if (!overwriteExist) {
                        copyCallback.pause();
                        copyCallback.onExistFileDetected(srcPath,
                                copyingFilePath);
                        // Wait if paused
                        synchronized (pauseLock) {
                            while (copyCallback.copyProgressPaused()) {
                                try {
                                    pauseLock.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        // Check if we should skip if procedure escape from a
                        // pause state
                        // caused by exist file detection
                        if (copyCallback.skipExist()) {
                            return OP_SKIP;
                        }
                    }
                }
            }

            FileInputStream inputStream;
            try {
                inputStream = new FileInputStream(srcFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                if (KLOG) {
                    Log.i(TAG, "File not found as an input stream for copying.");
                }
                copyCallback.onCopyError(srcPath, copyingFilePath,
                        ERROR_INVALID_SRC_PATH);
                return ERROR_INVALID_SRC_PATH;
            }
            FileOutputStream outputStream;
            try {
                outputStream = new FileOutputStream(copyingFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                if (KLOG) {
                    Log.i(TAG,
                            "File not available as an output stream for copying.");
                }
                copyCallback.onCopyError(srcPath, copyingFilePath,
                        ERROR_INVALID_DEST_PATH);
                return ERROR_INVALID_DEST_PATH;
            }
            FileChannel inputChannel = inputStream.getChannel();
            FileChannel outputChannel = outputStream.getChannel();

            FileLock inputLock = null;
            try {
                inputLock = inputChannel.tryLock(0, Long.MAX_VALUE, true);
            } catch (IOException e) {
                e.printStackTrace();
                if (KLOG) {
                    Log.i(TAG, "Failed to lock input file: " + srcPath);
                }
            }
            FileLock outputLock = null;
            try {
                outputLock = outputChannel.tryLock();
            } catch (IOException e) {
                e.printStackTrace();
                if (KLOG) {
                    Log.i(TAG,
                            "Failed to lock output file: "
                                    + copyingFile.getPath());
                }
            }

            if (inputLock == null || outputLock == null) {
                closeQuietly(inputStream, inputChannel, outputStream,
                        outputChannel);
                if (inputLock == null) {
                    copyCallback.onCopyError(srcPath, copyingFilePath,
                            ERROR_SRC_LOCKED);
                    return ERROR_SRC_LOCKED;
                } else if (outputLock == null) {
                    copyCallback.onCopyError(srcPath, copyingFilePath,
                            ERROR_DEST_LOCKED);
                    return ERROR_DEST_LOCKED;
                }
            }

            long srcFileSize;
            try {
                srcFileSize = inputChannel.size();
                if (KLOG) {
                    Log.i(TAG, "Size of file: " + srcFile + " is "
                            + srcFileSize);
                }
            } catch (IOException e) {
                e.printStackTrace();
                if (KLOG) {
                    Log.i(TAG, "Failed to get file size of " + srcPath);
                }
                copyCallback.onCopyError(srcPath, copyingFilePath,
                        ERROR_IO_ERROR);
                return ERROR_IO_ERROR;
            }
            if (position > 0) {
                if (position < srcFileSize) {
                    try {
                        inputChannel.position(position);
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (KLOG) {
                            Log.i(TAG, "Failed to seek to position: "
                                    + position + " on input file: " + srcPath);
                        }

                        copyCallback.onCopyError(srcPath, copyingFilePath,
                                ERROR_IO_ERROR);
                        return ERROR_IO_ERROR;
                    }
                } else {
                    releaseFileLock(inputLock, outputLock);
                    closeQuietly(inputStream, inputChannel, outputStream,
                            outputChannel);
                    if (KLOG) {
                        Log.i(TAG, "Position file:" + srcPath + " overflow!");
                    }
                    copyCallback.onCopyError(srcPath, copyingFilePath,
                            ERROR_SEEK_OVERFLOW);
                    return ERROR_SEEK_OVERFLOW;
                }
            }

            ByteBuffer buffer = ByteBuffer.allocate(4096 * 2);
            // ByteBuffer buffer = ByteBuffer.allocate(1024);
            long copied = sliceCopy(inputChannel, outputChannel, buffer);
            while (copied >= 0 && copied < srcFileSize) {
                // Wait if paused
                synchronized (pauseLock) {
                    while (copyCallback.copyProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            Log.e(TAG, "Failed to pause copying thread, "
                                    + "rely on next loop, current src: "
                                    + srcFile + " copied: " + copied);
                        }
                    }
                }

                if (copyCallback.copyProgressCanceled()) {
                    releaseFileLock(inputLock, outputLock);
                    closeQuietly(inputStream, inputChannel, outputStream,
                            outputChannel);
                    if (KLOG) {
                        Log.i(TAG, "Task canceled during copying.");
                    }
                    // Roll back unfinished copying file
                    if (copyingFile.exists()) {
                        copyingFile.delete();
                    }

                    return OP_CANCELED;
                }

                // Callback before next slice copy
                if (KLOG) {
                    Log.i(TAG, "Copied " + copied + " bytes for file: "
                            + srcPath);
                }
                copyCallback.onBytesCopied(srcPath, copied);
                int copiedPercentage = (int) (copied * 100 / srcFileSize);
                copyCallback.onPercentageCopied(srcPath, copiedPercentage);
                copied = sliceCopy(inputChannel, outputChannel, buffer);
            }

            releaseFileLock(inputLock, outputLock);
            closeQuietly(inputStream, inputChannel, outputStream, outputChannel);

            if (copied == srcFileSize) {
                copyCallback.onBytesCopied(srcPath, srcFileSize);
                copyCallback.onPercentageCopied(srcPath, 100);
                copyCallback.onCopyProgressFinished(srcPath,
                        copyingFile.getPath(), copied);
                Log.i(TAG,
                        "Copy file: " + srcPath + " to "
                                + copyingFile.getPath() + " success!");
                return OP_SUCCESS;
            } else {
                if (copied < OP_SUCCESS) {
                    Log.i(TAG, "Copy file: " + srcPath + " failed due to: "
                            + copied);
                    copyCallback.onCopyError(srcPath, copyingFile.getPath(),
                            (int) copied);
                    // Roll back unfinished copying file
                    if (copyingFile.exists()) {
                        copyingFile.delete();
                    }
                    return (int) copied;
                } else {
                    // TODO: need check
                    return ERROR_SEEK_OVERFLOW;
                }
            }
        } else if (srcFile.isDirectory() && srcFile.canRead()
                && targetDir.isDirectory() && targetDir.canWrite()) {
            String copyingDirName = (targetName != null) ? targetName
                    : getFileName(srcPath);
            ;
            String copyingDirPath = targetParentDirPath + "/" + copyingDirName;
            if (copyingDirPath.equals(srcPath)) {
                copyingDirName = generateCopyName(targetParentDirPath,
                        copyingDirName, copyCallback);
                copyingDirPath = targetParentDirPath + "/" + copyingDirName;
            }

            File copyingDir = new File(copyingDirPath);
            boolean isExistDir = false;
            if (copyingDir.exists()) {
                isExistDir = true;
                if (skipExist) {
                    return OP_SKIP;
                } else {
                    if (!overwriteExist) {
                        copyCallback.pause();
                        copyCallback
                                .onExistDirDetected(srcPath, copyingDirPath);
                        // Wait if paused
                        synchronized (pauseLock) {
                            while (copyCallback.copyProgressPaused()) {
                                try {
                                    pauseLock.wait();
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        // Check if we should skip if procedure escape from a
                        // pause state
                        // caused by exist file detection
                        if (copyCallback.skipExist()) {
                            return OP_SKIP;
                        }
                    }
                }
            }

            String files[] = srcFile.list();
            int len = files.length;

            if (!isExistDir && !copyingDir.mkdir()) {
                copyCallback.onCopyError(srcPath, copyingDirPath,
                        ERROR_DIR_EXISTS);
                return ERROR_DIR_EXISTS;
            }

            int copiedFiles = 0;
            int copyResult = OP_SUCCESS;
            for (copiedFiles = 0; copiedFiles < len; copiedFiles++) {
                String subSrcPath = srcPath + "/" + files[copiedFiles];
                int retCode = interruptibleCopy(subSrcPath, copyingDirPath,
                        null, 0, copyCallback.overwriteExist(),
                        copyCallback.skipExist(), copyCallback.skipCopyError(),
                        copyCallback, copyForMove);
                if (retCode < OP_SUCCESS) {
                    // copyCallback.onCopyError(subSrcPath, retCode);
                    if (copiedFiles < len - 1) {
                        copyCallback.saveCopyProgress(srcPath + "/"
                                + files[copiedFiles + 1], 0);
                    }
                    // break;
                }

                copyResult = retCode;
            }

            return copyResult;
            /*
             * if (copiedFiles < len - 1) { copyCallback.onCopyError(srcPath,
             * copyingDirPath, ERROR_COPY_SUBFILES_FAILED); return
             * ERROR_COPY_SUBFILES_FAILED; } else { return copyResult; }
             */

        } else if (!targetDir.canWrite()) {
            copyCallback.onCopyError(srcPath, targetParentDirPath,
                    ERROR_TARGET_PERMISSION_DENY);
            return ERROR_TARGET_PERMISSION_DENY;
        } else if (!srcFile.canRead()) {
            Log.i(TAG, "src file: " + srcFile + " can't read!");
            if (continueOnError) {
                return OP_SKIP;
            } else {
                copyCallback.onCopyError(srcPath, null, ERROR_SOURCE_READ_DENY);
                copyCallback.pause();
                // Wait if paused
                synchronized (pauseLock) {
                    while (copyCallback.copyProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Check if we should skip if procedure escape from a pause
                // state
                // caused by copy error and then continue or cancel
                if (copyCallback.skipCopyError()) {
                    return OP_SKIP;
                } else {
                    // TODO: may implement a feature such as 'retry'
                    return OP_CANCELED;
                }
            }
        } else {
            copyCallback.onCopyError(srcPath, null, ERROR_UNKNOWN);
            return ERROR_UNKNOWN;
        }
    }

    public static boolean isExistingFilePath(String path) {
        if (!path.startsWith("/")) {
            return false;
        }

        File file = new File(path);
        return file.exists();
    }

    public static boolean isReadableDirectory(String path) {
        if (!isExistingFilePath(path)) {
            return false;
        }

        File file = new File(path);
        return (file.canRead() && file.isDirectory()) ? true : false;
    }

    // Inspired by org.apache.commons.io.FileUtils.isSymlink()
    public static boolean isSymlink(File file) throws IOException {
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

    public static boolean isWritableDirectory(String path) {
        if (!isExistingFilePath(path)) {
            return false;
        }

        File file = new File(path);
        return (file.canWrite() && file.isDirectory()) ? true : false;
    }

    public static void releaseFileLock(FileLock... fileLocks) {
        if (fileLocks != null) {
            for (FileLock lock : fileLocks) {
                try {
                    lock.release();
                } catch (IOException e) {
                    Log.i(TAG,
                            "Catch IOException when try to release: "
                                    + lock.toString());
                    e.printStackTrace();
                }
            }
        }
    }

    public static int renameFile(String srcFilePath, String targetFilePath,
            boolean continueOnError, RenameCallback renameCallback) {
        File srcFile = new File(srcFilePath);
        File targetFile = new File(targetFilePath);

        if (renameCallback.renameProgressCanceled()) {
            return OP_CANCELED;
        }

        final Object pauseLock = renameCallback.getPauseLock();
        // Wait if paused
        synchronized (pauseLock) {
            while (renameCallback.renameProgressPaused()) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!srcFile.exists()) {
            if (KLOG) {
                Log.i(TAG, "File:" + srcFilePath
                        + " doesn't exists, can't cut.");
            }

            if (continueOnError) {
                return OP_SKIP;
            } else {
                renameCallback.onRenameError(srcFilePath, targetFilePath,
                        ERROR_INVALID_SRC_PATH);
                renameCallback.pause();
                // Wait if paused
                synchronized (pauseLock) {
                    while (renameCallback.renameProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Check if we should skip if procedure escape from a pause
                // state
                // caused by rename error and then continue or cancel
                if (renameCallback.skipRenameError()) {
                    return OP_SKIP;
                } else {
                    return OP_CANCELED;
                }
            }
        }

        if (!srcFile.canWrite()) {
            if (KLOG) {
                Log.i(TAG, "Permission deny to rename file:" + srcFilePath);
            }

            if (continueOnError) {
                return OP_SKIP;
            } else {
                renameCallback.onRenameError(srcFilePath, targetFilePath,
                        ERROR_SOURCE_WRITE_DENY);
                renameCallback.pause();
                // Wait if paused
                synchronized (pauseLock) {
                    while (renameCallback.renameProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Check if we should skip if procedure escape from a pause
                // state
                // caused by rename error and then continue or cancel
                if (renameCallback.skipRenameError()) {
                    return OP_SKIP;
                } else {
                    return OP_CANCELED;
                }
            }
        }

        if (srcFile.renameTo(targetFile)) {
            renameCallback.onRenameFinished(srcFilePath, targetFilePath);

            return OP_SUCCESS;
        } else {
            if (KLOG) {
                Log.i(TAG, "Failed to rename file:" + srcFilePath + " to "
                        + targetFilePath);
            }

            if (continueOnError) {
                return OP_SKIP;
            } else {
                renameCallback.onRenameError(srcFilePath, targetFilePath,
                        ERROR_RENAME_AS_MOVE_FAILED);
                renameCallback.pause();
                // Wait if paused
                synchronized (pauseLock) {
                    while (renameCallback.renameProgressPaused()) {
                        try {
                            pauseLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                // Check if we should skip if procedure escape from a pause
                // state
                // caused by rename error and then continue or cancel
                if (renameCallback.skipRenameError()) {
                    return OP_SKIP;
                } else {
                    return OP_CANCELED;
                }
            }
        }
    }

    public static int renameFile(String srcFilePath, String targetFilePath,
            SimpleRenameCallback renameCallback) {
        File srcFile = new File(srcFilePath);
        File targetFile = new File(targetFilePath);

        if (!srcFile.exists()) {
            if (KLOG) {
                Log.i(TAG, "File:" + srcFilePath
                        + " doesn't exists, can't rename.");
            }

            return ERROR_INVALID_SRC_PATH;
        }

        if (!srcFile.canWrite()) {
            if (KLOG) {
                Log.i(TAG, "Permission deny to rename file:" + srcFilePath);
            }

            return ERROR_SOURCE_WRITE_DENY;
        }

        if (srcFile.renameTo(targetFile)) {
            if (renameCallback != null) {
                renameCallback.onFileRenamed(srcFilePath, targetFilePath);
            }

            return OP_SUCCESS;
        } else {
            if (KLOG) {
                Log.i(TAG, "Failed to rename file:" + srcFilePath + " to "
                        + targetFilePath);
            }
            return ERROR_RENAME_FAILED;
        }
    }

    public static void searchFilesByStr(String searchDir, String searchStr,
            ArrayList<String> typeList, ArrayList<String> result,
            SearchCallback searchCallback,
            ArrayList<String> reservedSearchDirs, int depth, int recordLimit)
            throws Exception {
        searchFilesByStr(searchStr, searchDir, typeList, result,
                searchCallback, reservedSearchDirs, depth, recordLimit, false);
    }

    // Return offset of copied bytes in the input file
    public static long sliceCopy(FileChannel inputChannel,
            FileChannel outputChannel, ByteBuffer buffer) {
        if (inputChannel == null || outputChannel == null) {
            return ERROR_IO_ERROR;
        }

        try {
            if (inputChannel.read(buffer) != -1) {
                buffer.flip();
                outputChannel.write(buffer);
                buffer.clear();

                return inputChannel.position();
            } else {
                return OP_SUCCESS;
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (e.getMessage().contains(JAVA_ERRNO_NOSPACE)) {
                return ERROR_NO_SPACE;
            } else {
                return ERROR_IO_ERROR;
            }
        }
    }

    private static int searchFilesByStr(String searchDir, String searchStr,
            ArrayList<String> typeList, ArrayList<String> result,
            SearchCallback searchCallback,
            ArrayList<String> reservedSearchDirs, int depth, int recordLimit,
            boolean relay) throws Exception {
        if (searchCallback == null) {
            throw new Exception("Can't do search!");
        }

        if (depth <= 0 || result.size() >= recordLimit) {
            if (!relay) {
                searchCallback.onSearchFinished();
            }
            return OP_SUCCESS;
        } else if (searchCallback.searchProgressCanceled()) {
            if (!relay) {
                searchCallback.onSearchCancelled();
            }
            return OP_CANCELED;
        }
        // Log.i(TAG, "searchFiles - topDir: " + searchDir
        // + " searchStr: " + searchStr);

        File root_dir = new File(searchDir);
        String[] list = root_dir.list();

        if (list != null && root_dir.canRead()) {
            int len = list.length;

            for (int i = 0; i < len; i++) {
                if (depth <= 0 || result.size() >= recordLimit) {
                    if (!relay) {
                        searchCallback.onSearchFinished();
                    }
                    return OP_SUCCESS;
                } else if (searchCallback.searchProgressCanceled()) {
                    if (!relay) {
                        searchCallback.onSearchCancelled();
                    }
                    return OP_CANCELED;
                }

                File check = new File(searchDir + "/" + list[i]);
                // skip hidden file
                if (check.isHidden()) {
                    continue;
                }
                // skip system file
                if (StorageUtil.isAndroidSysFile(check.getPath())) {
                    continue;
                }
                // skip symbol link
                try {
                    if (isSymlink(check)) {
                        continue;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Failed to check symbol link!");
                }

                // Directory
                if (check.isDirectory()) {
                    if (StorageUtil.getExcludeSearchPath().contains(
                            check.getAbsolutePath())
                            || StorageUtil
                                    .isExcludeSearchPath(
                                            check.getAbsolutePath(),
                                            reservedSearchDirs)) {
                        continue;
                    } else {
                        if (typeList == null) {
                            if (searchStr != null
                                    && check.getName().contains(searchStr)) {
                                if (result.size() < recordLimit) {
                                    result.add(check.getPath());
                                } else {
                                    if (!relay) {
                                        searchCallback.onSearchFinished();
                                    }
                                    return OP_SUCCESS;
                                }
                            }
                        }

                        if (check.canRead()) {
                            searchCallback.onDirSearching(searchDir);
                            int ret = searchFilesByStr(check.getPath(),
                                    searchStr, typeList, result,
                                    searchCallback, reservedSearchDirs,
                                    depth - 1, recordLimit, true);
                            if (ret == OP_CANCELED) {
                                if (!relay) {
                                    searchCallback.onSearchCancelled();
                                }
                                return OP_CANCELED;
                            }
                        }
                    }
                } else if (check.isFile()) { // File
                    if (typeList != null) {
                        String extension = DataUtil
                                .getFileExtensionWithoutDot(check.getPath());
                        if (typeList.contains(extension)) {
                            if (FILE_PATTERN_ALL.equals(searchStr)) {
                                if (result.size() < recordLimit) {
                                    result.add(check.getPath());
                                } else {
                                    if (!relay) {
                                        searchCallback.onSearchFinished();
                                    }
                                    return OP_SUCCESS;
                                }
                            }
                        }
                    } else {
                        if (searchStr != null
                                && check.getName().contains(searchStr)) {
                            if (result.size() < recordLimit) {
                                result.add(check.getPath());
                            } else {
                                if (!relay) {
                                    searchCallback.onSearchFinished();
                                }
                                return OP_SUCCESS;
                            }
                        }
                    }
                }
            }

            searchCallback.onDirSearched(searchDir);
            if (!relay) {
                Log.i(TAG, "search finished.");
                searchCallback.onSearchFinished();
            }
            return OP_SUCCESS;
        } else {
            if (!relay) {
                Log.i(TAG, "search finished.");
                searchCallback.onSearchFinished();
            }
            return OP_SKIP;
        }
    }
}

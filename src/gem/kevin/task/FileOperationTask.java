package gem.kevin.task;

import gem.kevin.util.FileUtil;
import gem.kevin.util.FileUtil.CopyCallback;
import gem.kevin.util.FileUtil.DeleteCallback;
import gem.kevin.util.FileUtil.RenameCallback;
import gem.kevin.util.FileUtil.SearchCallback;
import gem.kevin.util.FileUtil.SimpleCopyCallback;
import gem.kevin.util.FileUtil.SimpleDeleteCallback;
import gem.kevin.util.FileUtil.SimpleRenameCallback;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.content.Context;
import android.util.Log;

public class FileOperationTask implements CopyCallback, RenameCallback,
        DeleteCallback, SearchCallback {

    public interface ClipOpTaskListener extends FileOpTaskListener {
        public String localeSensitiveCopyName(String srcName,
                int existDuplicateCount);

        public void onClipOpTaskCanceled(FileOperationTask canceledTask);

        public void onClipOpTaskEvaluating(FileOperationTask evaluatingTask);

        public void onClipOpTaskFinished(FileOperationTask finishedTask);

        public void onClipOpTaskPendingOnCopyError(
                FileOperationTask pendingTask, String srcPath,
                String targetPath, int errorCode);

        public void onClipOpTaskPendingOnDeleteError(
                FileOperationTask pendingTask, String srcPath, int errorCode);

        public void onClipOpTaskPendingOnExists(FileOperationTask pendingTask,
                String srcPath, String existPath);

        public void onClipOpTaskPendingOnRenameError(
                FileOperationTask pendingTask, String srcPath,
                String targetPath, int errorCode);

        public void onClipOpTaskProgressing(FileOperationTask processingTask);

        public void onClipOpTaskReady(FileOperationTask readyTask);

        public void onClipOpTaskRollbacked(FileOperationTask newTask);

        public void onClipOpTaskStarted(FileOperationTask startedTask);

        public void onClipOpTaskStopped(FileOperationTask stoppedTask);

        public void onClipOpTaskUnavailable(FileOperationTask unavailableTask,
                int reason);
    }

    public static class FileOperationOptions {
        public static final String KEY_SEARCH_DIR = "search_dir";
        public static final String KEY_SEARCH_RESEVERD_DIR = "search_reserved_dir";
        public static final String KEY_SEARCH_OUTPUT = "search_output";
        public static final String KEY_SEARCH_SCOPE = "search_scope";
        public static final String KEY_SEARCH_DEPTH = "search_depth";
        public static final String KEY_SEARCH_RECORD_LIMIT = "search_record_limit";

        public int sourceType;
        public int targetType;
        public String targetDirectoryPath;
        public String targetFilePath;
        public String operationType;
        public boolean overwriteAllExist = false;
        public boolean skipAllExist = false;
        public boolean skipAllCopyError = false;
        public boolean skipAllDeleteError = false;
        public boolean skipAllRenameError = false;
        public int searchType = -1;
        public HashMap<String, Object> searchParams;

        public FileOperationOptions(FileOperationOptions options) {
            this.operationType = options.operationType;
            this.sourceType = options.sourceType;
            this.targetType = options.targetType;
            this.targetDirectoryPath = options.targetDirectoryPath;
            this.targetFilePath = options.targetFilePath;
        }

        public FileOperationOptions(String operationType, int sourceType,
                int targetType, String targetDirectoryPath,
                String targetFilePath) {
            this.operationType = operationType;
            this.sourceType = sourceType;
            this.targetType = targetType;
            this.targetDirectoryPath = targetDirectoryPath;
            this.targetFilePath = targetFilePath;
        }

        public void setSearchParams(HashMap<String, Object> searchParams) {
            this.searchParams = searchParams;
        }

        public void setSearchType(int searchType) {
            this.searchType = searchType;
        }
    }

    public interface FileOpTaskListener {
    }

    public interface SearchOpTaskListener extends FileOpTaskListener {
        public void onSearchOpTaskCancelled(FileOperationTask task);

        public void onSearchOpTaskFinished(FileOperationTask task);

        public void onSearchOpTaskProgressing(FileOperationTask task,
                String searchDirPath, boolean dirFinished);
    }

    private static final String TAG = "FileOperationTask";
    private static final boolean KLOG = true;

    public static final String OP_TYPE_NEW_FILE = "new_file";
    public static final String OP_TYPE_COPY_FILE = "copy_file";

    public static final String OP_TYPE_MOVE_FILE = "move_file";
    public static final String OP_TYPE_RENAME_FILE = "rename_file";
    public static final String OP_TYPE_DELETE_FILE = "delete_file";
    public static final String OP_TYPE_SEARCH_FILE = "search_file";
    public static final int OP_AVAILABLE = 0;

    public static final int OP_UNAVAILABLE_SRC_NULL = 0x0001;
    public static final int OP_UNAVAILABLE_DEST_NULL = 0x0010;
    public static final int OP_UNAVAILABLE_SRC_EMPTY = 0x0100;
    public static final int OP_UNAVAILABLE_DEST_NOSPACE = 0x1000;

    public static final int FILE_TYPE_EMPTY = FileUtil.FILE_TYPE_EMPTY;
    public static final int FILE_TYPE_FILE = FileUtil.FILE_TYPE_FILE;
    public static final int FILE_TYPE_DIRECTORY = FileUtil.FILE_TYPE_DIRECTORY;
    public static final int FILE_TYPE_FILESET = FileUtil.FILE_TYPE_FILESET;
    public static final int TASK_NEW = 1;
    public static final int TASK_ROLLBACKED = 2;
    public static final int TASK_EVALUATING = 3;
    public static final int TASK_READY = 4;
    public static final int TASK_STARTED = 5;
    public static final int TASK_FINISHED = 6;
    public static final int TASK_STOPPED = 7;
    public static final int TASK_CANCELING = 8;
    public static final int TASK_CANCELED = 9;
    public static final int TASK_RUNNING = 10;
    public static final int TASK_PAUSED = 11;
    public static final int TASK_RESUMED = 12;
    public static final FileOperationOptions DEFAULT_NEW_OPTIONS = new FileOperationOptions(
            OP_TYPE_NEW_FILE, FILE_TYPE_EMPTY, FILE_TYPE_DIRECTORY, null, null);
    public static final FileOperationOptions DEFAULT_COPY_OPTIONS = new FileOperationOptions(
            OP_TYPE_COPY_FILE, FILE_TYPE_FILESET, FILE_TYPE_FILESET, null, null);
    public static final FileOperationOptions DEFAULT_MOVE_OPTIONS = new FileOperationOptions(
            OP_TYPE_MOVE_FILE, FILE_TYPE_FILESET, FILE_TYPE_FILESET, null, null);

    public static final FileOperationOptions DEFAULT_RENAME_OPTIONS = new FileOperationOptions(
            OP_TYPE_RENAME_FILE, FILE_TYPE_FILESET, FILE_TYPE_FILESET, null,
            null);

    public static final FileOperationOptions DEFAULT_DELETE_OPTIONS = new FileOperationOptions(
            OP_TYPE_DELETE_FILE, FILE_TYPE_FILESET, FILE_TYPE_EMPTY, null, null);

    public static final FileOperationOptions DEFAULT_SEARCH_OPTIONS = new FileOperationOptions(
            OP_TYPE_SEARCH_FILE, FILE_TYPE_FILESET, FILE_TYPE_FILESET, null,
            null);

    public static FileOperationOptions makeDefaultOptions(String operationType) {
        if (operationType.equals(OP_TYPE_NEW_FILE)) {
            return new FileOperationOptions(DEFAULT_NEW_OPTIONS);
        } else if (operationType.equals(OP_TYPE_COPY_FILE)) {
            return new FileOperationOptions(DEFAULT_COPY_OPTIONS);
        } else if (operationType.equals(OP_TYPE_MOVE_FILE)) {
            return new FileOperationOptions(DEFAULT_MOVE_OPTIONS);
        } else if (operationType.equals(OP_TYPE_RENAME_FILE)) {
            return new FileOperationOptions(DEFAULT_RENAME_OPTIONS);
        } else if (operationType.equals(OP_TYPE_DELETE_FILE)) {
            return new FileOperationOptions(DEFAULT_DELETE_OPTIONS);
        } else if (operationType.equals(OP_TYPE_SEARCH_FILE)) {
            return new FileOperationOptions(DEFAULT_SEARCH_OPTIONS);
        } else {
            return null;
        }
    }

    private final String mTaskId;
    private Context mContext;
    private FileOperationOptions mOptions;

    private long mRequiredSpace = UNINTIALIZED;

    private long mAvailableSpace = UNINTIALIZED;

    private ArrayList<String> mSourceFilePaths;

    private int mSubTaskNum = 0;

    private String mProcessingSourceFilePath;

    private int mSucceedSubTaskNum = 0;
    private int mProcessedSubTaskNum = 0;
    private int mSucceedPercentage = 0;

    private int mProcessedPercentage = 0;

    private static final long UNINTIALIZED = 0;
    private long mSourceCopySize = UNINTIALIZED;

    private long mSourceFileCount = UNINTIALIZED;
    private boolean mDeleteSource = false;

    // These fields shouldn't be used outside, they are using for
    // evaluation,
    // which are not represent exact properties of file in the task
    private long mTotalCopiedBytes = UNINTIALIZED;

    private long mTotalDeletedCount = UNINTIALIZED;

    private long mTotalRenamedCount = UNINTIALIZED;

    private int mTaskState;

    private HashSet<FileOpTaskListener> mListeners;
    private Runnable mWorkProcedure;

    private Runnable mEvaluateProcedure;

    private Thread mWorkThread;

    private Thread mEvaluateThread;

    private boolean mCanceled = false;

    private boolean mPaused = false;
    private final Object mPauseLock = new Object();
    private boolean mSkipExistOnce = false;

    private boolean mOverwriteExistOnce = false;
    private boolean mSkipCopyErrorOnce = false;
    private boolean mSkipRenameErrorOnce = false;
    private boolean mSkipDeleteErrorOnce = false;

    public FileOperationTask(Context context, String taskId,
            FileOperationOptions options, ArrayList<String> sourceFilePaths) {
        this(context, taskId, options, sourceFilePaths, null);
    }

    public FileOperationTask(Context context, String taskId,
            FileOperationOptions options, ArrayList<String> sourceFilePaths,
            FileOpTaskListener listener) {
        mContext = context;
        mTaskId = taskId;
        mOptions = options;
        mDeleteSource = (mOptions.operationType.equals(OP_TYPE_MOVE_FILE));
        mSourceFilePaths = sourceFilePaths;
        if (mSourceFilePaths != null) {
            mSubTaskNum = mSourceFilePaths.size();
        }

        mTaskState = TASK_NEW;
        mListeners = new HashSet<FileOpTaskListener>();
        if (listener != null) {
            registerListener(listener);
        }

        if (mSourceFilePaths != null) {
            mSubTaskNum = mSourceFilePaths.size();
        }

        initEvaluateThread();
        initWorkThread();
    }

    public long calculateSourceCount() {
        long totalCount = 0;
        for (String path : mSourceFilePaths) {
            long count = FileUtil.getSubFileCount(path);
            totalCount += (count > 1) ? count : 1;
        }

        return totalCount;
    }

    public long calculateSourceSize() {
        long totalSize = 0;
        for (String path : mSourceFilePaths) {
            long size = FileUtil.getFileSize(path);
            totalSize += (size > 0) ? size : 0;
        }

        return totalSize;
    }

    public void cancel() {
        if (KLOG) {
            Log.i(TAG, "File task of type:" + mOptions.operationType
                    + " canceled.");
        }
        mCanceled = true;
        notify(mTaskState = TASK_CANCELED);

        // Resume working thread so necessary steps can be finished
        synchronized (mPauseLock) {
            mPaused = false;
            mPauseLock.notifyAll();
        }
    }

    @Override
    public boolean copyProgressCanceled() {
        return mCanceled;
    }

    @Override
    public boolean copyProgressPaused() {
        return mPaused;
    }

    @Override
    public boolean deleteProgressCanceled() {
        return mCanceled;
    }

    @Override
    public boolean deleteProgressPaused() {
        return mPaused;
    }

    public void evaluate() {
        notify(mTaskState = TASK_EVALUATING);
        mEvaluateThread.start();
    }

    public int evaluateOpAvailability() {
        int result = OP_AVAILABLE;

        if (mOptions.operationType.equals(OP_TYPE_COPY_FILE)
                || mOptions.operationType.equals(OP_TYPE_MOVE_FILE)) {
            if (mOptions.targetDirectoryPath == null) {
                result |= OP_UNAVAILABLE_DEST_NULL;
            }
        }

        if (mSourceFilePaths == null) {
            result |= OP_UNAVAILABLE_SRC_NULL;
        } else {
            if (mSourceFilePaths.size() == 0) {
                result |= OP_UNAVAILABLE_SRC_EMPTY;
            }
        }

        return result;
    }

    @Override
    public String generateCopyName(String srcName, int existDuplicateCount) {
        // Handle over this staff to FileOperationListener,
        // make FileOperationTask pure non UI/resource code
        String copyName = null;
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    copyName = ((ClipOpTaskListener) listener)
                            .localeSensitiveCopyName(srcName,
                                    existDuplicateCount);
                    if (copyName != null) {
                        return copyName;
                    }
                }
            }
        }

        return copyName;
    }

    public long getAvailableSpace() {
        return mAvailableSpace;
    }

    public String getCurrentProcessingFilePath() {
        return mProcessingSourceFilePath;
    }

    public FileOperationOptions getOptions() {
        return mOptions;
    }

    @Override
    public Object getPauseLock() {
        return mPauseLock;
    }

    public int getProcessedPecentage() {
        return mProcessedPercentage;
    }

    public int getProcessedSubTaskNum() {
        return mProcessedSubTaskNum;
    }

    public long getRequiredSpace() {
        return mRequiredSpace;
    }

    public long getSouceCopySize() {
        return mSourceCopySize;
    }

    public long getSourceFileCount() {
        return mSourceFileCount;
    }

    public int getState() {
        return mTaskState;
    }

    public int getSubTaskNum() {
        return mSubTaskNum;
    }

    public int getSucceedPecentage() {
        return mSucceedPercentage;
    }

    public int getSucceedSubTaskNum() {
        return mSucceedSubTaskNum;
    }

    public boolean hasCanceled() {
        return (mTaskState == TASK_CANCELED);
    }

    public boolean hasFinished() {
        return (mTaskState == TASK_FINISHED);
    }

    public boolean isIdle() {
        return (mTaskState == TASK_NEW) || (mTaskState == TASK_ROLLBACKED)
                || (mTaskState == TASK_READY) || (mTaskState == TASK_FINISHED)
                || (mTaskState == TASK_CANCELED)
                || (mTaskState == TASK_STOPPED);
    }

    public void notify(int taskState) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ClipOpTaskListener clipListener = (ClipOpTaskListener) listener;
                    switch (taskState) {
                    case TASK_EVALUATING:
                        clipListener.onClipOpTaskEvaluating(this);
                        break;
                    case TASK_READY:
                        clipListener.onClipOpTaskReady(this);
                        break;
                    case TASK_STARTED:
                        clipListener.onClipOpTaskStarted(this);
                        break;
                    case TASK_STOPPED:
                        clipListener.onClipOpTaskStopped(this);
                        break;
                    case TASK_FINISHED:
                        clipListener.onClipOpTaskFinished(this);
                        break;
                    case TASK_RUNNING:
                        clipListener.onClipOpTaskProgressing(this);
                        break;
                    case TASK_CANCELED:
                        clipListener.onClipOpTaskCanceled(this);
                        break;
                    case TASK_ROLLBACKED:
                        clipListener.onClipOpTaskRollbacked(this);
                        break;
                    default:
                        return;
                    }
                }
            }
        }
    }

    @Override
    public void onBytesCopied(String srcPath, long copiedBytes) {
        long currentCopiedBytes = mTotalCopiedBytes + copiedBytes;
        if (mSourceCopySize > 0) {
            int evalValue = (int) (currentCopiedBytes * 100 / mSourceCopySize);
            if (evalValue > 100) {
                mSucceedPercentage = 100;
            } else {
                mSucceedPercentage = evalValue;
            }
        } else {
            mSucceedPercentage = 100;
        }

        if (mSucceedPercentage < 100) {
            notify(mTaskState = TASK_RUNNING);
        } else {
            notify(mTaskState = TASK_FINISHED);
        }
    }

    @Override
    public void onCopyError(String srcFilePath, String targetFilePath,
            int errorCode) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener)
                            .onClipOpTaskPendingOnCopyError(this, srcFilePath,
                                    targetFilePath, errorCode);
                }
            }
        }
    }

    @Override
    public void onCopyProgressFinished(String srcFilePath,
            String copiedFilePath, long copied) {
        if (copiedFilePath.endsWith(FileUtil.COPYING_FILE_SUFFIX)) {
            FileUtil.renameFile(
                    copiedFilePath,
                    copiedFilePath.substring(0, copiedFilePath.length()
                            - FileUtil.COPYING_FILE_SUFFIX.length()), null);
        }

        mTotalCopiedBytes += copied;
        if (mSourceCopySize > 0) {
            int evalValue = (int) (mTotalCopiedBytes * 100 / mSourceCopySize);
            if (evalValue > 100) {
                mSucceedPercentage = 100;
            } else {
                mSucceedPercentage = evalValue;
            }
        }

        if (mSucceedPercentage < 100) {
            notify(mTaskState = TASK_RUNNING);
        } else {
            notify(mTaskState = TASK_FINISHED);
        }

        if (mContext != null && mContext instanceof SimpleCopyCallback) {
            ((SimpleCopyCallback) mContext).onFileCopied(srcFilePath,
                    copiedFilePath);
        }
    }

    @Override
    public void onDeleteError(String filePath, int errorCode) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener)
                            .onClipOpTaskPendingOnDeleteError(this, filePath,
                                    errorCode);
                }
            }
        }
    }

    @Override
    public void onDeleteFinished(String filePath) {
        mTotalDeletedCount += 1;
        if (mSourceFileCount > 1) {
            int evalValue = (int) (mTotalDeletedCount * 100 / mSourceFileCount);
            if (evalValue > 100) {
                mSucceedPercentage = 100;
            } else {
                mSucceedPercentage = evalValue;
            }
        } else {
            mSucceedPercentage = 100;
        }

        if (mSucceedPercentage < 100) {
            notify(mTaskState = TASK_RUNNING);
        } else {
            notify(mTaskState = TASK_FINISHED);
        }

        if (mContext != null && mContext instanceof SimpleDeleteCallback) {
            ((SimpleDeleteCallback) mContext).onFileDeleted(filePath);
        }
    }

    @Override
    public void onDirSearched(String searchedDirPath) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof SearchOpTaskListener) {
                    ((SearchOpTaskListener) listener)
                            .onSearchOpTaskProgressing(this, searchedDirPath,
                                    true);
                }
            }
        }
    }

    @Override
    public void onDirSearching(String searchingDirPath) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof SearchOpTaskListener) {
                    ((SearchOpTaskListener) listener)
                            .onSearchOpTaskProgressing(this, searchingDirPath,
                                    false);
                }
            }
        }
    }

    @Override
    public void onExistDirDetected(String srcDirPath, String existDirPath) {
        pause();

        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener)
                            .onClipOpTaskPendingOnExists(this, srcDirPath,
                                    existDirPath);
                }
            }
        }
    }

    @Override
    public void onExistFileDetected(String srcFilePath, String existFilePath) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener)
                            .onClipOpTaskPendingOnExists(this, srcFilePath,
                                    existFilePath);
                }
            }
        }
    }

    public void onOperationUnavailable(int reason) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener).onClipOpTaskUnavailable(
                            this, reason);
                }
            }
        }
    }

    @Override
    public void onPercentageCopied(String srcPath, int copiedPercentage) {
        if (srcPath != null) {
            mProcessingSourceFilePath = srcPath;
        }
    }

    @Override
    public void onRenameError(String srcFilePath, String targetFilePath,
            int errorCode) {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof ClipOpTaskListener) {
                    ((ClipOpTaskListener) listener)
                            .onClipOpTaskPendingOnRenameError(this,
                                    srcFilePath, targetFilePath, errorCode);
                }
            }
        }
    }

    @Override
    public void onRenameFinished(String srcFilePath, String renamedFilePath) {
        mTotalRenamedCount++;
        if (mSourceFileCount > 1) {
            int evalValue = (int) (mTotalRenamedCount * 100 / mSourceFileCount);
            if (evalValue > 100) {
                mSucceedPercentage = 100;
            } else {
                mSucceedPercentage = evalValue;
            }
        } else {
            mSucceedPercentage = 100;
        }

        if (mSucceedPercentage < 100) {
            notify(mTaskState = TASK_RUNNING);
        } else {
            notify(mTaskState = TASK_FINISHED);
        }

        if (mContext != null && mContext instanceof SimpleRenameCallback) {
            ((SimpleRenameCallback) mContext).onFileRenamed(srcFilePath,
                    renamedFilePath);
        }
    }

    @Override
    public void onSearchCancelled() {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof SearchOpTaskListener) {
                    ((SearchOpTaskListener) listener)
                            .onSearchOpTaskCancelled(this);
                }
            }
        }
    }

    @Override
    public void onSearchFinished() {
        synchronized (mListeners) {
            for (FileOpTaskListener listener : mListeners) {
                if (listener instanceof SearchOpTaskListener) {
                    ((SearchOpTaskListener) listener)
                            .onSearchOpTaskFinished(this);
                }
            }
        }
    }

    @Override
    public boolean overwriteExist() {
        if (mOptions.overwriteAllExist) {
            return true;
        }

        if (mOverwriteExistOnce) {
            mOverwriteExistOnce = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void pause() {
        synchronized (mPauseLock) {
            mPaused = true;
        }

        if (KLOG) {
            Log.i(TAG, "File task of type:" + mOptions.operationType
                    + " paused.");
        }
        notify(mTaskState = TASK_PAUSED);
    }

    public void registerListener(FileOpTaskListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }

    @Override
    public boolean renameProgressCanceled() {
        return mCanceled;
    }

    @Override
    public boolean renameProgressPaused() {
        return mPaused;
    }

    public void resume() {
        synchronized (mPauseLock) {
            mPaused = false;
            mPauseLock.notifyAll();
        }

        if (KLOG) {
            Log.i(TAG, "File task of type:" + mOptions.operationType
                    + " resumed.");
        }
        notify(mTaskState = TASK_RESUMED);
    }

    @Override
    public void saveCopyProgress(String srcPath, long position) {
        // TODO Auto-generated method stub
        // If we wan't to make file operation task resumable even after the
        // application quit,
        // We can save task states and serialize necessary info for the
        // resuming.
    }

    @Override
    public boolean searchProgressCanceled() {
        return mCanceled;
    }

    public void setOverwriteExistOnce(boolean overwrite) {
        mOverwriteExistOnce = overwrite;
    }

    public void setSkipCopyErrorOnce(boolean skip) {
        mSkipCopyErrorOnce = skip;
    }

    public void setSkipDeleteErrorOnce(boolean skip) {
        mSkipDeleteErrorOnce = skip;
    }

    public void setSkipExistOnce(boolean skip) {
        mSkipExistOnce = skip;
    }

    public void setSkipRenameErrorOnce(boolean skip) {
        mSkipRenameErrorOnce = skip;
    }

    @Override
    public boolean skipCopyError() {
        if (mOptions.skipAllCopyError) {
            return true;
        }

        if (mSkipCopyErrorOnce) {
            mSkipCopyErrorOnce = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean skipDeleteError() {
        if (mOptions.skipAllDeleteError) {
            return true;
        }

        if (mSkipDeleteErrorOnce) {
            mSkipDeleteErrorOnce = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean skipExist() {
        if (mOptions.skipAllExist) {
            return true;
        }

        if (mSkipExistOnce) {
            mSkipExistOnce = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean skipRenameError() {
        if (mOptions.skipAllRenameError) {
            return true;
        }

        if (mSkipRenameErrorOnce) {
            mSkipRenameErrorOnce = false;
            return true;
        } else {
            return false;
        }
    }

    public void start() {
        // Task can only start on ready state
        if (mTaskState != TASK_READY) {
            evaluate();
        } else {
            mWorkThread.start();
        }

        if (KLOG) {
            Log.i(TAG, "File task of type:" + mOptions.operationType
                    + " started.");
        }
        notify(mTaskState = TASK_STARTED);
    }

    private long evaluateCopySize() {
        if (mSourceFilePaths == null || mSourceFilePaths.size() == 0) {
            return UNINTIALIZED;
        } else {
            if (mSourceCopySize == UNINTIALIZED) {
                mSourceCopySize = calculateSourceSize();
            }

            return mSourceCopySize;
        }
    }

    private long evaluateFileCount() {
        if (mSourceFilePaths == null || mSourceFilePaths.size() == 0) {
            return UNINTIALIZED;
        } else {
            if (mSourceFileCount == UNINTIALIZED) {
                mSourceFileCount = calculateSourceCount();
            }

            return mSourceFileCount;
        }
    }

    private void initEvaluateThread() {
        mEvaluateProcedure = new Runnable() {
            @Override
            public void run() {
                if (KLOG) {
                    Log.i(TAG, "Evaluating thread id: "
                            + Thread.currentThread().getId());
                }

                int availablility = evaluateOpAvailability();
                if (availablility != OP_AVAILABLE) {
                    FileOperationTask.this
                            .onOperationUnavailable(availablility);
                    FileOperationTask.this
                            .notify(FileOperationTask.this.mTaskState = TASK_ROLLBACKED);
                    return;
                }

                if (mOptions.operationType.equals(OP_TYPE_COPY_FILE)
                        || mOptions.operationType.equals(OP_TYPE_MOVE_FILE)) {
                    mRequiredSpace = evaluateCopySize();
                    if (KLOG) {
                        Log.i(TAG, "Space: " + mRequiredSpace
                                + " required by file task: "
                                + FileOperationTask.this.mTaskId);
                    }

                    /*
                     * StorageUtil storageUtil = StorageUtil.getSingleton(null);
                     * if (storageUtil == null) { // If storage utility is
                     * unavailable, skip space status check // and rely on
                     * working thread to detect NOSPACE error Log.i(TAG,
                     * "Skip file system status evaluation due to storage utility unavailable"
                     * ); } else { StorageVolumeStatFs statFs =
                     * storageUtil.statFsContainerFileSystem
                     * (mOptions.targetDirectoryPath); if (statFs != null) {
                     * mAvailableSpace = statFs.getAvailableSize(); } }
                     */
                    // (ToT) API 'File::getFreeSpace()' already added in API
                    // level 9
                    mAvailableSpace = new File(mOptions.targetDirectoryPath)
                            .getFreeSpace();

                    if (KLOG) {
                        Log.i(TAG, "Space: " + mAvailableSpace + " available.");
                    }
                    if (mRequiredSpace > mAvailableSpace) {
                        FileOperationTask.this
                                .onOperationUnavailable(OP_UNAVAILABLE_DEST_NOSPACE);
                        FileOperationTask.this
                                .notify(FileOperationTask.this.mTaskState = TASK_ROLLBACKED);
                        return;
                    }
                } else if (mOptions.operationType.equals(OP_TYPE_DELETE_FILE)) {
                    evaluateFileCount();
                } else if (mOptions.operationType.equals(OP_TYPE_RENAME_FILE)) {
                    evaluateFileCount();
                } else {
                    // ...
                }

                FileOperationTask.this
                        .notify(FileOperationTask.this.mTaskState = TASK_READY);
                FileOperationTask.this.start();
            }
        };
        mEvaluateThread = new Thread(mEvaluateProcedure);
    }

    private void initWorkThread() {
        mWorkProcedure = new Runnable() {
            @Override
            public void run() {
                if (KLOG) {
                    Log.i(TAG, "Working thread id: "
                            + Thread.currentThread().getId());
                }

                for (String srcPath : mSourceFilePaths) {
                    if (mCanceled) {
                        break;
                    }

                    if (mOptions.operationType.equals(OP_TYPE_COPY_FILE)
                            || mOptions.operationType.equals(OP_TYPE_MOVE_FILE)) {
                        if (mOptions.targetDirectoryPath == null) {
                            // TODO: on operation unavailable
                            Log.i(TAG, "Operation unavailable.");
                            return;
                        }

                        int copyResult;
                        try {
                            Log.i(TAG,
                                    "before starting interruptible copy for src path: "
                                            + srcPath);
                            copyResult = FileUtil.interruptibleCopy(srcPath,
                                    mOptions.targetDirectoryPath, 0,
                                    mOptions.overwriteAllExist ? true : false,
                                    mOptions.skipAllExist ? true : false,
                                    mOptions.skipAllCopyError ? true : false,
                                    FileOperationTask.this,
                                    mOptions.operationType
                                            .equals(OP_TYPE_MOVE_FILE));
                        } catch (Exception e) {
                            e.printStackTrace();
                            copyResult = FileUtil.ERROR_OP_UNAVAILABLE;
                        }
                        if (copyResult == FileUtil.OP_SUCCESS) {
                            mSucceedSubTaskNum++;
                            mProcessedSubTaskNum++;
                            if (mDeleteSource) {
                                try {
                                    FileUtil.deleteFile(srcPath, true,
                                            FileOperationTask.this);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if (copyResult == FileUtil.OP_CANCELED) {
                            return;
                        } else if (copyResult == FileUtil.OP_SKIP) {
                            // TODO: make use of skipped info
                            mProcessedSubTaskNum++;
                            continue;
                        } else if (copyResult == FileUtil.ERROR_OP_UNAVAILABLE) {
                            mProcessedSubTaskNum++;
                            return;
                        } else { // Failed, go to next sub task
                            if (KLOG) {
                                Log.i(TAG, "Failed to paste file: " + srcPath
                                        + " in dir: "
                                        + mOptions.targetDirectoryPath
                                        + " due to error:" + copyResult);
                            }
                            mProcessedSubTaskNum++;
                        }
                    } else if (mOptions.operationType
                            .equals(OP_TYPE_RENAME_FILE)) {
                        if (mOptions.targetDirectoryPath == null
                                && mOptions.targetFilePath == null) {
                            if (KLOG) {
                                Log.e(TAG,
                                        "Neither target directory or target file path is specified, can't rename.");
                            }
                            // TODO: on operation unavailable
                            return;
                        }

                        // Directly 'Rename'
                        if (mOptions.targetFilePath != null) {
                            int renameResult = FileUtil
                                    .renameFile(
                                            srcPath,
                                            mOptions.targetFilePath,
                                            (mContext instanceof SimpleRenameCallback) ? (SimpleRenameCallback) mContext
                                                    : null);
                            if (renameResult == FileUtil.OP_SUCCESS) {
                                mSucceedSubTaskNum++;
                                mProcessedSubTaskNum++;
                            } else {
                                // Failed
                                mProcessedSubTaskNum++;
                            }

                            // Should act on single file
                            break;
                        }

                        // 'Rename' for 'Cut' actions (move on same file
                        // system)
                        if (mOptions.targetDirectoryPath != null) {
                            String targetPath = mOptions.targetDirectoryPath
                                    + "/"
                                    + srcPath.substring(
                                            srcPath.lastIndexOf("/") + 1,
                                            srcPath.length());
                            int renameResult;
                            if (mOptions.targetDirectoryPath.equals(FileUtil
                                    .getParentDirPath(srcPath))) {
                                Log.i(TAG, "paste on same path, skip!");
                                renameResult = FileUtil.OP_SUCCESS;
                            } else {
                                renameResult = FileUtil
                                        .renameFile(
                                                srcPath,
                                                targetPath,
                                                mOptions.skipAllRenameError ? true
                                                        : false,
                                                FileOperationTask.this);
                            }

                            if (renameResult == FileUtil.OP_SUCCESS) {
                                if (KLOG) {
                                    Log.i(TAG, "Move file: " + srcPath + " to "
                                            + mOptions.targetDirectoryPath
                                            + " succeed.");
                                }
                                mSucceedSubTaskNum++;
                                mProcessedSubTaskNum++;
                            } else if (renameResult == FileUtil.OP_CANCELED) {
                                return;
                            } else if (renameResult == FileUtil.OP_SKIP) {
                                // TODO: make use of skipped info
                                mProcessedSubTaskNum++;
                                continue;
                            } else if (renameResult == FileUtil.ERROR_OP_UNAVAILABLE) {
                                mProcessedSubTaskNum++;
                                return;
                            } else {
                                mProcessedSubTaskNum++;
                            }
                        }
                    } else if (mOptions.operationType
                            .equals(OP_TYPE_DELETE_FILE)) {
                        int deleteResult = FileUtil.ERROR_OP_UNAVAILABLE;
                        try {
                            deleteResult = FileUtil.deleteFile(srcPath,
                                    mOptions.skipAllDeleteError ? true : false,
                                    FileOperationTask.this);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        if (deleteResult == FileUtil.OP_SUCCESS) {
                            if (KLOG) {
                                Log.i(TAG, "Delete file: " + srcPath
                                        + " succeed.");
                            }
                            mSucceedSubTaskNum++;
                            mProcessedSubTaskNum++;
                        } else {
                            if (KLOG) {
                                Log.i(TAG, "Delete file: " + srcPath
                                        + " FAILED.");
                            }
                            mProcessedSubTaskNum++;
                        }
                    } else if (mOptions.operationType
                            .equals(OP_TYPE_SEARCH_FILE)
                            && mOptions.searchParams != null) {
                        Object objDir = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_DIR);
                        Object objTypeList = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_SCOPE);
                        Object objResult = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_OUTPUT);
                        Object objReserved = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_RESEVERD_DIR);
                        Object objDepth = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_DEPTH);
                        Object objLimit = mOptions.searchParams
                                .get(FileOperationOptions.KEY_SEARCH_RECORD_LIMIT);

                        String searchDir = objDir != null ? (String) objDir
                                : null;
                        @SuppressWarnings("unchecked")
                        ArrayList<String> typeList = objTypeList != null ? (ArrayList<String>) objTypeList
                                : null;
                        @SuppressWarnings("unchecked")
                        ArrayList<String> searchResult = objResult != null ? (ArrayList<String>) objResult
                                : null;
                        @SuppressWarnings("unchecked")
                        ArrayList<String> reservedSearchDirs = objReserved != null ? (ArrayList<String>) objReserved
                                : null;
                        int searchDepth = objDepth != null ? ((Integer) objDepth)
                                .intValue() : -1;
                        int searchRecordLimit = objLimit != null ? ((Integer) objLimit)
                                .intValue() : -1;

                        if (searchDir != null && searchResult != null) {
                            try {
                                FileUtil.searchFilesByStr(srcPath, searchDir,
                                        typeList, searchResult,
                                        FileOperationTask.this,
                                        reservedSearchDirs, searchDepth,
                                        searchRecordLimit);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                if (mSucceedPercentage == 100) {
                    FileOperationTask.this.notify(mTaskState = TASK_FINISHED);
                } else {
                    FileOperationTask.this.notify(mTaskState = TASK_STOPPED);
                }
            }
        };
        mWorkThread = new Thread(mWorkProcedure);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        synchronized (mListeners) {
            mListeners.clear();
        }
    }
}

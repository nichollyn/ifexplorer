package gem.kevin.widget;

import gem.kevin.provider.ClipSourceProvider;
import gem.kevin.task.FileOperationTask;
import gem.kevin.task.FileOperationTask.ClipOpTaskListener;
import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.util.FileUtil.SimpleRenameCallback;
import gem.kevin.util.StorageUtil;

import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.sparseboolean.ifexplorer.FileItem;
import com.sparseboolean.ifexplorer.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Message;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;


public class ClipItemAdapter extends MutualAdapter implements
        ClipOpTaskListener {
    public interface ClipItemManager {
        public String getClipPasteDir();

        public DeleteDelegate getDeleteDelegate();

        public PasteDelegate getPasteDelegate();

        public void onClipItemFinished(ClipItemAdapter adapter);

        public void onClipItemRemoved(ClipItemAdapter adapter);
    }

    public interface DeleteDelegate {
        public void cancelDelete(ClipItemAdapter assignor, boolean rollback);

        public void doDelete(HashSet<FileClip> toDelete,
                ClipItemAdapter assignor);

        public void showDeleteUi();
    }

    public interface PasteDelegate {
        public void cancelPaste(ClipItemAdapter assignor, boolean rollback);

        public void doPaste(HashSet<FileClip> toPaste, String pasteDir,
                ClipItemAdapter assignor);

        public void showPasteUi();
    }

    private static class ClipItemViewHolder {
        TextView nameTextView;
        ImageView iconImageView;
    }

    private static final String TAG = "ClipItemAdapter";
    private static final boolean KLOG = false;
    public static final int MSG_FILE_TASKS_READY = 1;
    public static final int MSG_FILE_TASKS_FINISHED = 2;
    public static final int MSG_FILE_TASKS_EXECUTING = 3;
    public static final int MSG_FILE_TASKS_CANCELING = 4;
    public static final int MSG_FILE_TASKS_CANCELED = 5;
    public static final int MSG_FILE_TASKS_STOPPED = 6;
    public static final int MSG_FILE_TASKS_UNVAILABLE = 7;
    public static final int MSG_FILE_TASKS_EVALUATING = 8;
    public static final int MSG_FILE_TASKS_ROLLBACKED = 9;

    public static final int MSG_FILE_TASKS_PENDING_COPY = 10;
    public static final int MSG_FILE_TASKS_PENDING_DELETE = 11;
    public static final int MSG_FILE_TASKS_PENDING_RENAME = 12;

    public static final int STATUS_NO_CLIP = -1;
    public static final int STATUS_NO_VALID_CLIP = -2;

    public static final int STATUS_CANNOT_HANDLE = -3;
    public static final int STATUS_SHOULD_WAIT = 1;

    public static final int STATUS_SHOULD_SHOW_PROGRESS = 2;

    public static final int STATUS_NO_NEED_PROGRESS = 3;

    public static final long SIZE_THRESHOLD_SHOW_COPY_PROGRESS = 10 * 1024 * 1024; // in

    // size/Bytes
    public static final long COUNT_THRESHOLD_SHOW_DELETE_PROGRESS = 100; // in
                                                                         // counts

    public static final long NOT_SET = -1;

    public static void renameSingleFile(Context context, String srcFilePath,
            String targetFilePath) {
        int ret = FileUtil
                .renameFile(
                        srcFilePath,
                        targetFilePath,
                        (context instanceof SimpleRenameCallback) ? (SimpleRenameCallback) context
                                : null);

        Resources resources = context.getResources();
        String errStr;

        if (ret == FileUtil.OP_SUCCESS) {
            Toast.makeText(
                    context,
                    resources.getString(R.string.rename_succeed,
                            FileUtil.getFileName(srcFilePath),
                            FileUtil.getFileName(targetFilePath)),
                    Toast.LENGTH_SHORT).show();
        } else {
            errStr = FileUtil.getDefaultFileOpErrStr(resources, ret);
            Toast.makeText(
                    context,
                    resources.getString(R.string.rename_failed,
                            FileUtil.getFileName(srcFilePath), errStr),
                    Toast.LENGTH_LONG).show();
        }
    }

    private ClipItemManager mManager;
    private HashSet<FileOperationTask> mOneshotFileOpTasks = null;

    private boolean mClipExecuting = false;

    // private long mTotalBytes = NOT_SET;
    // private long mTotalFileCount = NOT_SET;
    private int mTotalSubTaskNum = 0;
    private int mSucceedSubTaskNum = 0;
    private int mProcessedSubTaskNum = 0;
    private int mSucceedPercentage = 0;
    private int mProcessedPercentage = 0;

    private String mProcessingFilePath = null;
    private String mPendingSrcFilePath = null;

    private String mPendingTargetFilePath = null;

    private int mPendingReason;

    private View mBoundClipContentView;

    private View mBoundOneshotTaskView;

    private TextView mBoundOneshotTaskStatus;

    private ProgressBar mBoundOneshotProgress;

    private String mId;

    private Handler mUiHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_FILE_TASKS_UNVAILABLE:
                confirmOnUnavailabeTask((FileOperationTask) msg.obj,
                        mPendingReason);
                break;
            case MSG_FILE_TASKS_EVALUATING:
                updateInterativeClipUi(true, false, false, false,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage, false,
                        false);
                break;
            case MSG_FILE_TASKS_EXECUTING:
                updateInterativeClipUi(false, true, false, false,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage, false,
                        false);
                break;
            case MSG_FILE_TASKS_CANCELING:
                updateInterativeClipUi(false, false, true, false,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage,
                        (mSucceedSubTaskNum == mTotalSubTaskNum), false);
                break;
            case MSG_FILE_TASKS_STOPPED:
                updateInterativeClipUi(false, false, false, true,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage,
                        (mSucceedSubTaskNum == mTotalSubTaskNum), false);
                break;
            case MSG_FILE_TASKS_CANCELED:
                // Copy clip can be reused
                if (getClipType() == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE) {
                    switchBoundViewMode(false);
                } else {
                    // treat as stopped state
                    updateInterativeClipUi(false, false, false, true,
                            mSucceedSubTaskNum, mSucceedPercentage,
                            mProcessedSubTaskNum, mProcessedPercentage,
                            (mSucceedSubTaskNum == mTotalSubTaskNum), false);
                }
                break;
            case MSG_FILE_TASKS_ROLLBACKED:
                updateInterativeClipUi(false, false, false, false,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage, false, true);
                break;
            case MSG_FILE_TASKS_FINISHED:
                updateInterativeClipUi(false, false, false, false,
                        mSucceedSubTaskNum, mSucceedPercentage,
                        mProcessedSubTaskNum, mProcessedPercentage,
                        (mSucceedSubTaskNum == mTotalSubTaskNum), false);
                break;
            case MSG_FILE_TASKS_READY:
                FileOperationTask task = (FileOperationTask) msg.obj;
                showClipUiIfNecessary(task);
                break;
            case MSG_FILE_TASKS_PENDING_COPY:
                confirmOnPendingCopyTask((FileOperationTask) msg.obj,
                        mPendingReason);
                break;
            case MSG_FILE_TASKS_PENDING_DELETE:
                confirmOnPendingDeleteTask((FileOperationTask) msg.obj,
                        mPendingReason);
                break;
            case MSG_FILE_TASKS_PENDING_RENAME:
                confirmOnPendingRenameTask((FileOperationTask) msg.obj,
                        mPendingReason);
                break;
            default:
                super.handleMessage(msg);
            }
        }
    };

    public static final float CLIP_TYPE_UNBOUND = -1.0f;

    public static final String CLIP_DATA_FIELD_FILE_PATH = "file_path";

    public static final String CLIP_DATA_FIELD_FILE_NAME = "file_name";

    public static final String CLIP_DATA_FIELD_FILE_ICON = "file_icon";

    private int mDisplayItemNum = -1;

    public ClipItemAdapter(Context context,
            List<? extends Map<String, ?>> data, int resource, String[] from,
            int[] to) {
        super(context, null, resource, from, to);
    }

    public void applyOnCopyErrorPolicy(FileOperationTask fileOpTask,
            boolean skip) {
        if (skip) {
            fileOpTask.getOptions().skipAllCopyError = true;
        } else {
            fileOpTask.getOptions().skipAllCopyError = false;
        }
    }

    public void applyOnDeleteErrorPolicy(FileOperationTask fileOpTask,
            boolean skip) {
        if (skip) {
            fileOpTask.getOptions().skipAllDeleteError = true;
        } else {
            fileOpTask.getOptions().skipAllDeleteError = false;
        }
    }

    public void applyOnRenameErrorPolicy(FileOperationTask fileOpTask,
            boolean skip) {
        if (skip) {
            fileOpTask.getOptions().skipAllRenameError = true;
        } else {
            fileOpTask.getOptions().skipAllRenameError = false;
        }
    }

    public void applyOverwritePolicy(FileOperationTask fileOpTask,
            boolean overwrite) {
        if (overwrite) {
            fileOpTask.getOptions().overwriteAllExist = true;
            fileOpTask.getOptions().skipAllExist = false;
        } else {
            fileOpTask.getOptions().skipAllExist = true;
            fileOpTask.getOptions().overwriteAllExist = false;
        }
    }

    public void bindInterativeClipUi(View clipContentView,
            View oneshotTaskView, TextView oneshotStatus,
            ProgressBar oneshotProgress, AbsListView repeatableTaskList) {
        mBoundClipContentView = clipContentView;
        mBoundOneshotTaskView = oneshotTaskView;
        mBoundOneshotTaskStatus = oneshotStatus;
        mBoundOneshotProgress = oneshotProgress;
    }

    public void cancelFileClip() {
        if (mManager == null) {
            return;
        } else {
            float clipType = getClipType();
            if (clipType == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE
                    || clipType == ClipSourceProvider.CLIP_TYPE_CUT_SOURCE) {
                PasteDelegate delegate = mManager.getPasteDelegate();
                if (delegate != null) {
                    delegate.cancelPaste(this, false);
                }
            } else if (clipType == ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE) {
                DeleteDelegate delegate = mManager.getDeleteDelegate();
                if (delegate != null) {
                    delegate.cancelDelete(this, false);
                }
            } else {
                return;
            }
        }
    }

    public void deleteFileClip(HashSet<FileClip> toDelete) {
        if (mManager == null) {
            return;
        } else {
            if (toDelete.size() != 0) {
                DeleteDelegate delegate = mManager.getDeleteDelegate();
                if (delegate != null) {
                    delegate.doDelete(toDelete, this);
                }
            }
        }
    }

    public void executeFileClip(HashSet<FileClip> toExecute) {
        float clipType = getClipType();
        if (clipType == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE
                || clipType == ClipSourceProvider.CLIP_TYPE_CUT_SOURCE) {
            pasteFileClip(toExecute);
        } else if (clipType == ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE) {
            deleteFileClip(toExecute);
        }
    }

    public int fetchFileClips(HashSet<FileClip> result) {
        if (!isBound()) {
            return STATUS_NO_CLIP;
        }

        ClipSourceProvider provider = (ClipSourceProvider) getBoundMutualSourceProvider();
        float clipType = provider.getClipType();
        HashSet<Object> rawData = provider.getMutualSources();
        int total = rawData.size();
        int fetched = 0;

        for (Object item : rawData) {
            if (item instanceof FileItem
                    && FileUtil.isExistingFilePath(((FileItem) item).getPath())) {
                result.add(new FileClip(clipType, ((FileItem) item).getPath()));
                fetched++;
            }
        }

        return total - fetched;
    }

    public float getClipType() {
        return (isBound()) ? ((ClipSourceProvider) getBoundMutualSourceProvider())
                .getClipType() : CLIP_TYPE_UNBOUND;
    }

    public int getDisplayItemNum() {
        return mDisplayItemNum;
    }

    public String getId() {
        return mId;
    }

    public HashSet<FileOperationTask> getOneShotClipTask() {
        return mOneshotFileOpTasks;
    }

    public int getPendingReason() {
        return mPendingReason;
    }

    public String getPendingSrcFilePath() {
        return mPendingSrcFilePath;
    }

    public String getPendingTargetFilePath() {
        return mPendingTargetFilePath;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ClipItemViewHolder viewHolder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(mResource,
                    null);

            viewHolder = new ClipItemViewHolder();
            viewHolder.nameTextView = (TextView) convertView
                    .findViewById(R.id.item_name);
            viewHolder.iconImageView = (ImageView) convertView
                    .findViewById(R.id.item_icon);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ClipItemViewHolder) convertView.getTag();
        }

        if (position == (mDisplayItemNum - 1) && getCount() > mDisplayItemNum) {
            viewHolder.iconImageView.setImageResource(R.drawable.file_stack);
            viewHolder.nameTextView.setText(R.string.otherClips);

            return convertView;
        }

        @SuppressWarnings("rawtypes")
        final Map clipItemData = (Map) getItem(position);
        final String path = (String) clipItemData
                .get(CLIP_DATA_FIELD_FILE_PATH);
        final File file = new File(path);
        if (file != null && file.isFile()) {
            String ext = file.toString();
            String sub_ext = ext.substring(ext.lastIndexOf(".") + 1);

            // if (sub_ext.equalsIgnoreCase("apk")) {
            // Drawable icon = DataUtil.getNonInstalledAppIcon(getContext(),
            // path);
            // viewHolder.iconImageView.setImageDrawable(icon);
            // } else {
            viewHolder.iconImageView.setImageResource(DataUtil
                    .getFileIconResId(sub_ext));
            // }
        }

        if (file != null && file.isDirectory()) {
            viewHolder.iconImageView.setImageResource(R.drawable.folder);
        }

        String fullName = file.getName();
        String displayName;
        if (fullName.length() > 18) {
            displayName = String
                    .format("%s...%s",
                            fullName.substring(0, 6),
                            fullName.substring(fullName.length() - 7,
                                    fullName.length()));
        } else {
            displayName = fullName;
        }
        viewHolder.nameTextView.setText(displayName);

        return convertView;
    }

    public boolean isClipExecuting() {
        return mClipExecuting;
    }

    @Override
    public String localeSensitiveCopyName(String srcName,
            int existDuplicateCount) {
        Log.i(TAG, "srcName is: " + srcName);
        String countStr = (existDuplicateCount > 1) ? String.format(" %d",
                existDuplicateCount) : "";
        return getContext().getResources().getString(R.string.copy_file_name,
                srcName, countStr);
    }

    @Override
    public void onClipOpTaskCanceled(FileOperationTask canceledTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(canceledTask)) {

            Message cancelingMsg = mUiHandler
                    .obtainMessage(MSG_FILE_TASKS_CANCELING);
            mUiHandler.sendMessage(cancelingMsg);

            // unlock correspond mutual sources when all tasks idle
            Log.i(TAG, "unlock mutual sources due to task canceled.");
            unlockMutualSourcesIfAvailable();

            Message canceledMsg = mUiHandler
                    .obtainMessage(MSG_FILE_TASKS_CANCELED);
            mUiHandler.sendMessageDelayed(canceledMsg, 500);
        }
    }

    @Override
    public void onClipOpTaskEvaluating(FileOperationTask evaluatingTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(evaluatingTask)) {

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_EVALUATING);
            msg.obj = evaluatingTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskFinished(FileOperationTask finishedTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(finishedTask)) {
            // on last processing
            onClipOpTaskProgressing(finishedTask);
            mProcessingFilePath = null;

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_FINISHED);

            mUiHandler.sendMessage(msg);

            // unlock correspond mutual sources when all tasks idle
            Log.i(TAG, "unlock mutual sources due to task finished.");
            unlockMutualSourcesIfAvailable();
        }
    }

    @Override
    public void onClipOpTaskPendingOnCopyError(FileOperationTask pendingTask,
            String srcPath, String targetPath, int errorCode) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(pendingTask)) {
            // on last processing
            // onFileOpTaskProgressing(pendingTask);
            mProcessingFilePath = null;
            mPendingSrcFilePath = srcPath;
            mPendingTargetFilePath = targetPath;
            mPendingReason = errorCode;

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_PENDING_COPY);
            msg.obj = pendingTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskPendingOnDeleteError(FileOperationTask pendingTask,
            String srcPath, int errorCode) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(pendingTask)) {
            // on last processing
            // onFileOpTaskProgressing(pendingTask);
            mProcessingFilePath = null;
            mPendingSrcFilePath = srcPath;
            mPendingTargetFilePath = null;
            mPendingReason = errorCode;

            Message msg = mUiHandler
                    .obtainMessage(MSG_FILE_TASKS_PENDING_DELETE);
            msg.obj = pendingTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskPendingOnExists(FileOperationTask pendingTask,
            String srcPath, String existFilePath) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(pendingTask)) {
            // on last processing
            // onFileOpTaskProgressing(pendingTask);
            mProcessingFilePath = null;
            mPendingSrcFilePath = srcPath;
            mPendingTargetFilePath = existFilePath;
            mPendingReason = FileUtil.ERROR_FILE_EXISTS;

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_PENDING_COPY);
            msg.obj = pendingTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskPendingOnRenameError(FileOperationTask pendingTask,
            String srcPath, String targetPath, int errorCode) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(pendingTask)) {
            // on last processing
            // onFileOpTaskProgressing(pendingTask);
            mProcessingFilePath = null;
            mPendingSrcFilePath = srcPath;
            mPendingTargetFilePath = targetPath;
            mPendingReason = errorCode;

            Message msg = mUiHandler
                    .obtainMessage(MSG_FILE_TASKS_PENDING_RENAME);
            msg.obj = pendingTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskProgressing(FileOperationTask processingTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(processingTask)) {
            syncFileOperationTaskStatus();
            mProcessingFilePath = processingTask.getCurrentProcessingFilePath();

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_EXECUTING);

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskReady(FileOperationTask readyTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(readyTask)) {
            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_READY);
            msg.obj = readyTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskRollbacked(FileOperationTask newTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(newTask)) {

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_ROLLBACKED);
            msg.obj = newTask;

            mUiHandler.sendMessage(msg);
        }
    }

    @Override
    public void onClipOpTaskStarted(FileOperationTask startedTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(startedTask)) {

            // lock correspond mutual sources when any task started
            lockMutualSources();
        }
    }

    @Override
    public void onClipOpTaskStopped(FileOperationTask stoppedTask) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(stoppedTask)) {
            onClipOpTaskProgressing(stoppedTask);
            mProcessingFilePath = null;

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_STOPPED);

            mUiHandler.sendMessage(msg);

            // unlock correspond mutual sources when all tasks idle
            Log.i(TAG, "unlock mutual sources due to task stopped.");
            unlockMutualSourcesIfAvailable();
        }
    }

    @Override
    public void onClipOpTaskUnavailable(FileOperationTask unavailableTask,
            int reason) {
        if (mOneshotFileOpTasks != null
                && mOneshotFileOpTasks.contains(unavailableTask)) {
            mPendingReason = reason;

            Message msg = mUiHandler.obtainMessage(MSG_FILE_TASKS_UNVAILABLE);
            msg.obj = unavailableTask;

            mUiHandler.sendMessage(msg);
        }
    }

    public void pasteFileClip(HashSet<FileClip> toPaste) {
        if (mManager == null) {
            return;
        } else {
            String defaultPasteDir = mManager.getClipPasteDir();
            if (defaultPasteDir != null) {
                pasteFileClip(toPaste, defaultPasteDir);
            } else {
                confirmOnInvalidPasteDir();
            }
        }
    }

    public void pasteFileClip(HashSet<FileClip> toPaste, String pasteDir) {
        if (toPaste == null || pasteDir == null) {
            return;
        }

        if (toPaste.size() != 0) {
            PasteDelegate delegate = mManager.getPasteDelegate();
            if (delegate != null) {
                delegate.doPaste(toPaste, pasteDir, this);
            }
        }
    }

    public HashSet<FileClip> prepare() {
        HashSet<FileClip> toHandle = new HashSet<FileClip>();
        int invalid = fetchFileClips(toHandle);
        if (invalid > 0) {
            Context context = getContext();
            Resources resources = context.getResources();
            Toast.makeText(
                    context,
                    resources.getString(R.string.invalid_clip_removed, invalid),
                    Toast.LENGTH_SHORT).show();
        } else if (invalid == ClipItemAdapter.STATUS_NO_CLIP) {
            Context context = getContext();
            Resources resources = context.getResources();
            Toast.makeText(context,
                    resources.getString(R.string.no_available_clip),
                    Toast.LENGTH_SHORT).show();
        }

        return toHandle;
    }

    public void setClipItemManager(ClipItemManager manager) {
        mManager = manager;
    }

    public void setDisplayItemNum(int num) {
        mDisplayItemNum = num;
    }

    public void setId(String id) {
        mId = id;
    }

    public void setOneShotClipTask(HashSet<FileOperationTask> fileOpTasks) {
        mOneshotFileOpTasks = fileOpTasks;
    }

    private void confirmOnCopyPermissionDeny(
            final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String message = resources.getString(R.string.no_permission_read_file,
                getPendingSrcFilePath());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_permission_deny)
                .setPositiveButton(R.string.skip,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.setSkipCopyErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNeutralButton(R.string.skip_all,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                applyOnCopyErrorPolicy(taskNeedConfirm, true);

                                taskNeedConfirm.setSkipCopyErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNegativeButton(R.string.stop_task,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnDeletePermissionDeny(
            final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String message = resources.getString(
                R.string.no_permission_delete_file, getPendingSrcFilePath());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_permission_deny)
                .setPositiveButton(R.string.skip,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.setSkipDeleteErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNeutralButton(R.string.skip_all,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                applyOnDeleteErrorPolicy(taskNeedConfirm, true);

                                taskNeedConfirm.setSkipDeleteErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNegativeButton(R.string.stop_task,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnFileExists(final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        View contentRootView = LayoutInflater.from(context).inflate(
                R.layout.overwrite_confirm_dialog, null);
        final CheckBox applyToAll = (CheckBox) contentRootView
                .findViewById(R.id.applyToAll);
        final TextView srcPathText = (TextView) contentRootView
                .findViewById(R.id.srcPath);
        final TextView srcSizeText = (TextView) contentRootView
                .findViewById(R.id.srcSize);
        final TextView srcModTimeText = (TextView) contentRootView
                .findViewById(R.id.srcModTime);

        final TextView targetPathText = (TextView) contentRootView
                .findViewById(R.id.targetPath);
        final TextView targetSizeText = (TextView) contentRootView
                .findViewById(R.id.targetSize);
        final TextView targetModTimeText = (TextView) contentRootView
                .findViewById(R.id.targetModTime);

        String srcPath = getPendingSrcFilePath();
        String srcPathStr = resources.getString(R.string.source_file) + ":"
                + srcPath;
        String srcSizeStr = resources.getString(R.string.size) + ":"
                + FileUtil.formattedSizeStr(FileUtil.getFileSize(srcPath));
        long srcLastMod = FileUtil.getLastModifyTime(srcPath);
        String srcModTimeStr = resources.getString(R.string.modified)
                + ":"
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(
                        srcLastMod)));

        String targetPath = getPendingTargetFilePath();
        String targetPathStr = resources.getString(R.string.dest_file) + ":"
                + targetPath;
        String targetSizeStr = resources.getString(R.string.size) + ":"
                + FileUtil.formattedSizeStr(FileUtil.getFileSize(targetPath));
        long targetLastMod = FileUtil.getLastModifyTime(targetPath);
        String targetModTimeStr = resources.getString(R.string.modified)
                + ":"
                + (new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(
                        targetLastMod)));

        if (targetLastMod > srcLastMod) {
            targetModTimeStr = targetModTimeStr + " "
                    + resources.getString(R.string.newer);
        } else {
            srcModTimeStr = srcModTimeStr + " "
                    + resources.getString(R.string.newer);
        }

        srcPathText.setText(srcPathStr);
        srcSizeText.setText(srcSizeStr);
        srcModTimeText.setText(srcModTimeStr);
        targetPathText.setText(targetPathStr);
        targetSizeText.setText(targetSizeStr);
        targetModTimeText.setText(targetModTimeStr);

        String message = resources.getString(R.string.overwrite_prompt) + "\n"
                + FileUtil.getFileName(srcPath);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.overwrite)
                .setView(contentRootView)
                .setPositiveButton(R.string.overwrite,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (applyToAll.isChecked()) {
                                    applyOverwritePolicy(taskNeedConfirm, true);
                                }
                                taskNeedConfirm.setSkipExistOnce(false);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNegativeButton(R.string.skip,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                if (applyToAll.isChecked()) {
                                    applyOverwritePolicy(taskNeedConfirm, false);
                                }
                                taskNeedConfirm.setSkipExistOnce(true);
                                taskNeedConfirm.resume();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnInvalidPasteDir() {
        Context context = getContext();
        Resources resources = context.getResources();

        Toast.makeText(context,
                resources.getString(R.string.invalid_paste_dir),
                Toast.LENGTH_SHORT).show();
    }

    private void confirmOnMovePermissionDeny(
            final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String message = resources.getString(R.string.no_permission_move_file,
                getPendingSrcFilePath());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_permission_deny)
                .setPositiveButton(R.string.skip,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.setSkipCopyErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNeutralButton(R.string.skip_all,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                applyOnCopyErrorPolicy(taskNeedConfirm, true);

                                taskNeedConfirm.setSkipCopyErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNegativeButton(R.string.stop_task,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnNoEnoughSpace(final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String requiredSpaceStr = resources.getString(
                R.string.required_space,
                Formatter.formatFileSize(context,
                        taskNeedConfirm.getRequiredSpace()));
        String availableSpaceStr = resources.getString(
                R.string.available_space,
                Formatter.formatFileSize(context,
                        taskNeedConfirm.getAvailableSpace()));

        String message = requiredSpaceStr + "\n\n" + availableSpaceStr;

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_space_insufficient)
                .setPositiveButton(R.string.dlg_ok, null).setMessage(message)
                .create();

        dialog.show();
    }

    private void confirmOnNoSpace(final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String defaultStorageName = resources.getString(R.string.dest_storage);
        String storageName = getStorageName(getPendingTargetFilePath());

        String message = resources.getString(R.string.storage_insufficient,
                storageName != null ? storageName : defaultStorageName);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_space_insufficient)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnPendingCopyTask(
            final FileOperationTask taskNeedConfirm, int errorCode) {
        switch (errorCode) {
        case FileUtil.ERROR_FILE_EXISTS:
            confirmOnFileExists(taskNeedConfirm);
            break;
        case FileUtil.ERROR_SOURCE_READ_DENY:
            confirmOnCopyPermissionDeny(taskNeedConfirm);
            break;
        case FileUtil.ERROR_SOURCE_WRITE_DENY:
            confirmOnMovePermissionDeny(taskNeedConfirm);
            break;
        case FileUtil.ERROR_TARGET_PERMISSION_DENY:
            confirmOnWritePermissionDeny(taskNeedConfirm);
            break;
        case FileUtil.ERROR_NO_SPACE:
            confirmOnNoSpace(taskNeedConfirm);
            break;
        // TOTO: handle more errors
        }
    }

    private void confirmOnPendingDeleteTask(
            final FileOperationTask taskNeedConfirm, int errorCode) {
        switch (errorCode) {
        case FileUtil.ERROR_SOURCE_WRITE_DENY:
            confirmOnDeletePermissionDeny(taskNeedConfirm);
            break;
        }
    }

    private void confirmOnPendingRenameTask(
            final FileOperationTask taskNeedConfirm, int errorCode) {
        switch (errorCode) {
        case FileUtil.ERROR_SOURCE_WRITE_DENY:
            confirmOnRenamePermissionDeny(taskNeedConfirm);
            break;
        }
    }

    private void confirmOnRenamePermissionDeny(
            final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String message = resources.getString(R.string.no_permission_move_file,
                getPendingSrcFilePath());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_permission_deny)
                .setPositiveButton(R.string.skip,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.setSkipRenameErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNeutralButton(R.string.skip_all,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                applyOnRenameErrorPolicy(taskNeedConfirm, true);

                                taskNeedConfirm.setSkipRenameErrorOnce(true);
                                taskNeedConfirm.resume();
                            }
                        })
                .setNegativeButton(R.string.stop_task,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private void confirmOnUnavailabeTask(
            final FileOperationTask taskUnavailable, int reason) {
        switch (reason) {
        case FileOperationTask.OP_UNAVAILABLE_SRC_NULL:
            // TODO
            break;
        case FileOperationTask.OP_UNAVAILABLE_SRC_EMPTY:
            break;
        case FileOperationTask.OP_UNAVAILABLE_DEST_NULL:
            break;
        case FileOperationTask.OP_UNAVAILABLE_DEST_NOSPACE:
            confirmOnNoEnoughSpace(taskUnavailable);
            break;
        }
    }

    private void confirmOnWritePermissionDeny(
            final FileOperationTask taskNeedConfirm) {
        Context context = getContext();
        Resources resources = context.getResources();

        String message = resources.getString(R.string.no_permission_write_file,
                getPendingTargetFilePath());

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.error_permission_deny)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                taskNeedConfirm.cancel();
                            }
                        }).setMessage(message).create();

        dialog.show();
    }

    private String getStorageName(String filePath) {
        if (filePath == null) {
            return null;
        }

        StorageUtil util = StorageUtil.getSingleton(getContext());
        if (util != null) {
            String mountPoint = util.getMountpoint(filePath);
            if (mountPoint != null) {
                return util
                        .getCachedId(mountPoint, StorageUtil.VOLID_TAG_LABEL);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private boolean isAllFileOpTaskIdle() {
        for (FileOperationTask task : mOneshotFileOpTasks) {
            if (!task.isIdle()) {
                return false;
            }
        }

        return true;
    }

    private void lockMutualSources() {
        ClipSourceProvider provider = (ClipSourceProvider) getBoundMutualSourceProvider();
        if (provider != null) {
            Log.i(TAG, "Lock mutual sources due to purpose: " + getClipType());
            provider.lockMutualSources(getClipType());
        }

        mClipExecuting = true;
    }

    private void showClipUiIfNecessary(final FileOperationTask task) {
        long evalCopyBytes = task.getSouceCopySize();
        long evalDeleteCount = task.getSourceFileCount();
        if (evalCopyBytes > SIZE_THRESHOLD_SHOW_COPY_PROGRESS) {
            PasteDelegate delegate = mManager.getPasteDelegate();
            if (delegate != null) {
                delegate.showPasteUi();
            }
        }
        if (evalDeleteCount > COUNT_THRESHOLD_SHOW_DELETE_PROGRESS) {
            DeleteDelegate delegate = mManager.getDeleteDelegate();
            if (delegate != null) {
                delegate.showDeleteUi();
            }
        }
    }

    private void switchBoundViewMode(boolean showTaskView) {
        if (mBoundClipContentView != null) {
            mBoundClipContentView.setVisibility(showTaskView ? View.GONE
                    : View.VISIBLE);
        }

        if (mBoundOneshotTaskView != null) {
            mBoundOneshotTaskView.setVisibility(showTaskView ? View.VISIBLE
                    : View.GONE);
        }
    }

    private void syncFileOperationTaskStatus() {
        if (mOneshotFileOpTasks == null) {
            return;
        }

        synchronized (mOneshotFileOpTasks) {
            mTotalSubTaskNum = 0;
            mSucceedSubTaskNum = 0;
            mProcessedSubTaskNum = 0;
            mSucceedPercentage = 0;
            mProcessedPercentage = 0;
            for (FileOperationTask task : mOneshotFileOpTasks) {
                mTotalSubTaskNum += task.getSubTaskNum();
                mSucceedSubTaskNum += task.getSucceedSubTaskNum();
                mProcessedSubTaskNum += task.getProcessedSubTaskNum();
                mSucceedPercentage += task.getSucceedPecentage();
                mProcessedPercentage += task.getProcessedPecentage();
            }
        }

        if (KLOG) {
            Log.i(TAG, "syncFileOperationTaskStatus --- totalSubTaskNum:"
                    + mTotalSubTaskNum + " succeedSubTaskNum: "
                    + mSucceedSubTaskNum + " succeedPercentage: "
                    + mSucceedPercentage + " processedSubTaskNum: "
                    + mProcessedSubTaskNum + " processedPercentage: "
                    + mProcessedPercentage);
        }
    }

    private void unlockMutualSourcesIfAvailable() {
        if (isAllFileOpTaskIdle()) {
            ClipSourceProvider provider = (ClipSourceProvider) getBoundMutualSourceProvider();
            if (provider != null) {
                Log.i(TAG, "Unock mutual sources");
                provider.unLockMutualSources();
            }

            mClipExecuting = false;
        }
    }

    private void updateInterativeClipUi(boolean clipEvaluating,
            boolean clipExecuting, boolean clipCanceling, boolean clipStopped,
            int succeedCount, int succeedPercentage, int processedCount,
            int processedPercentage, boolean clipFinished,
            boolean clipRollbacked) {
        if (clipFinished || clipStopped) {
            switchBoundViewMode(false);
            if (mManager != null) {
                mManager.onClipItemFinished(this);
            }
            return;
        } else {
            if (clipRollbacked) {
                switchBoundViewMode(false);
                return;
            }
        }

        switchBoundViewMode(clipEvaluating || clipExecuting || clipCanceling);

        if (mBoundOneshotTaskStatus != null) {
            String info = "";
            Context context = getContext();
            Resources resources = context.getResources();
            String extraInfo = (this.getClipType() == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE) ? resources
                    .getString(R.string.copying_) : resources
                    .getString(R.string.moving_);

            if (clipCanceling) {
                info = resources.getString(R.string.canceling);
            } else if (clipExecuting) {
                if (mProcessingFilePath != null) {
                    String filePath;
                    try {
                        filePath = new String(mProcessingFilePath);
                    } catch (NullPointerException e) {
                        filePath = "";
                    }
                    int pathLength = filePath.length();
                    if (pathLength < 48) {
                        extraInfo = extraInfo + filePath;
                    } else {
                        extraInfo = extraInfo
                                + filePath.substring(0, 36)
                                + "...."
                                + filePath
                                        .substring(pathLength - 8, pathLength);
                    }

                    info = String.format("%d/%d - %d%%  %s",
                            (succeedPercentage < 100) ? succeedCount + 1
                                    : succeedCount, ClipItemAdapter.this
                                    .getCount(), succeedPercentage, extraInfo);

                } else {
                    info = String.format("%d/%d - %d%%",
                            (succeedPercentage < 100) ? succeedCount + 1
                                    : succeedCount, ClipItemAdapter.this
                                    .getCount(), succeedPercentage);
                }
            } else if (clipEvaluating) {
                info = resources.getString(R.string.calculating);
            }

            mBoundOneshotTaskStatus.setText(info);
        }

        if (mBoundOneshotProgress != null) {
            mBoundOneshotProgress.setProgress(succeedPercentage);
            // mBoundOneshotProgress.setSecondaryProgress(succeedPercentage +
            // 10);
        }
    }
}

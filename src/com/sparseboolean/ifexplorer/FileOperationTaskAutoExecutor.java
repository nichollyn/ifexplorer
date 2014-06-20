package com.sparseboolean.ifexplorer;

import gem.kevin.task.FileOperationTask;
import gem.kevin.task.FileOperationTask.ClipOpTaskListener;

import java.util.ArrayList;

public class FileOperationTaskAutoExecutor implements ClipOpTaskListener {
    public static final int EXECUTOR_IDLE = 0x00000001;
    public static final int EXECUTOR_BUSY = 0x00000002;
    public static final int EXECUTOR_FULL_LOAD = 0x00000006; // should cover
                                                             // flag STATUS_BUSY

    private final ArrayList<FileOperationTask> mTasks;
    public static final int MAX_EXECUTING_LIMIT = 2;

    public FileOperationTaskAutoExecutor() {
        mTasks = new ArrayList<FileOperationTask>();
    }

    public void dequeueFileOpTask(FileOperationTask task) {
        if (task != null) {
            task.cancel();
            mTasks.remove(task);
            manage();
        }
    }

    public void enqueueFileOpTask(FileOperationTask task) {
        task.registerListener(this);
        mTasks.add(task);
        manage();
    }

    public int evaluateFileOpTask(FileOperationTask task) {
        return 0;
    }

    @Override
    public String localeSensitiveCopyName(String srcName,
            int existDuplicateCount) {
        return null;
    }

    public void manage() {
        int running = 0;
        for (FileOperationTask task : mTasks) {
            if (running > MAX_EXECUTING_LIMIT) {
                break;
            }

            switch (task.getState()) {
            case FileOperationTask.TASK_STARTED:
            case FileOperationTask.TASK_RUNNING:
                running++;
                break;
            case FileOperationTask.TASK_NEW:
                task.start();
                break;
            default:
                continue;
            }
        }
    }

    @Override
    public void onClipOpTaskCanceled(FileOperationTask canceledTask) {
        mTasks.remove(canceledTask);
        manage();
    }

    @Override
    public void onClipOpTaskEvaluating(FileOperationTask evaluatingTask) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClipOpTaskFinished(FileOperationTask finishedTask) {
        mTasks.remove(finishedTask);
        manage();
    }

    @Override
    public void onClipOpTaskPendingOnCopyError(FileOperationTask pendingTask,
            String srcPath, String targetPath, int errorCode) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClipOpTaskPendingOnDeleteError(FileOperationTask pendingTask,
            String srcPath, int errorCode) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClipOpTaskPendingOnExists(FileOperationTask pendingTask,
            String srcPath, String existPath) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClipOpTaskPendingOnRenameError(FileOperationTask pendingTask,
            String srcPath, String targetPath, int errorCode) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onClipOpTaskProgressing(FileOperationTask processingTask) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onClipOpTaskReady(FileOperationTask readyTask) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onClipOpTaskRollbacked(FileOperationTask newTask) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onClipOpTaskStarted(FileOperationTask startedTask) {
        manage();
    }

    @Override
    public void onClipOpTaskStopped(FileOperationTask stoppedTask) {
        mTasks.remove(stoppedTask);
        manage();
    }

    @Override
    public void onClipOpTaskUnavailable(FileOperationTask unavailableTask,
            int reason) {
        mTasks.remove(unavailableTask);
        manage();
    }
}

package com.sparseboolean.ifexplorer.ui;

import gem.kevin.provider.ClipSourceProvider;
import gem.kevin.provider.ClipSourceProvider.ClipSourceCallback;
import gem.kevin.provider.MutualSourceProvider;
import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.widget.ClipItemAdapter;
import gem.kevin.widget.ClipItemAdapter.ClipItemManager;
import gem.kevin.widget.ClipItemAdapter.DeleteDelegate;
import gem.kevin.widget.ClipItemAdapter.PasteDelegate;
import gem.kevin.widget.FileClip;
import gem.kevin.widget.MutualAdapter;
import gem.kevin.widget.MutualSourceProcessor;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.sparseboolean.ifexplorer.IfExplorer.MultiChoiceConsumeOptions;
import com.sparseboolean.ifexplorer.IfExplorer.MultiChoiceConsumer;
import com.sparseboolean.ifexplorer.FileItem;
import com.sparseboolean.ifexplorer.R;


public class ClipHistoryAdapter extends ArrayAdapter<ClipItemAdapter> implements
        MultiChoiceConsumer, ClipSourceCallback, ClipItemManager {
    public class ClipGenerator implements MutualSourceProcessor {

        @Override
        public List<? extends Map<String, ?>> buildMutualAdapterData(
                HashSet<Object> rawSource) {
            ArrayList<HashMap<String, Object>> adaptableData = new ArrayList<HashMap<String, Object>>();

            for (Object source : rawSource) {
                if (source instanceof FileItem) {
                    String filePath = ((FileItem) source).getPath();
                    File file = new File(filePath);
                    if (!file.exists()) {
                        continue;
                    }

                    HashMap<String, Object> item = new HashMap<String, Object>();
                    item.put(ClipItemAdapter.CLIP_DATA_FIELD_FILE_NAME,
                            file.getName());
                    item.put(ClipItemAdapter.CLIP_DATA_FIELD_FILE_PATH,
                            file.getPath());
                    item.put(ClipItemAdapter.CLIP_DATA_FIELD_FILE_ICON,
                            DataUtil.getFileIconResId(file));

                    adaptableData.add(item);
                }
            }

            return adaptableData;
        }
    }

    public interface ClipHistoryCallback {
        public String getClipPasteDir();

        public void onClipHistoryChanged(ClipHistoryAdapter adapter);
    }

    private static class ClipGroupViewHolder {
        ImageView indicatorImageView;
        TextView titleTextView;
        GridView clipListView;

        View clipContentView;
        ImageButton executeButton;
        ImageButton removeButton;

        View oneshotTaskView;
        TextView oneshotStatus;
        ProgressBar oneshotProgress;
        ImageButton cancelButton;

        ListView repeatableTasksList;
    }

    private static final String TAG = "IF-ClipHistoryAdapter";
    public static final String CLIP_COPY = "copy";
    public static final String CLIP_CUT = "cut";

    public static final String CLIP_DELETE = "delete";

    public static final String CLIP_RENAME_SINGLE = "rename_single";

    private ClipHistoryCallback mClipHistoryCallback;

    private Dialog mCurrentClearPrompt = null;

    private ClipGenerator mClipGenerator = new ClipGenerator();
    private ArrayList<ClipItemAdapter> mClipItemAdapters;

    public ClipHistoryAdapter(Context context, int resource,
                              int textViewResourceId, List<ClipItemAdapter> objects) {
        super(context, resource, textViewResourceId, objects);
        mClipItemAdapters = (ArrayList<ClipItemAdapter>) objects;
    }

    public void cancelAllClipRecords() {
        for (int index = 0; index < getCount(); index++) {
            ClipItemAdapter adapter = getItem(index);
            if (adapter.isClipExecuting()) {
                adapter.cancelFileClip();
            }
        }
    }

    public void cancelClipRecord(int position) {
        ClipItemAdapter adapter = getItem(position);
        if (adapter == null) {
            return;
        }

        adapter.cancelFileClip();
    }

    public void clearClipRecords() {
        boolean needPrompt = false;
        for (int index = 0; index < getCount(); index++) {
            ClipItemAdapter adapter = getItem(index);
            if (adapter.isClipExecuting()) {
                needPrompt = true;
                break;
            }
        }

        if (needPrompt) {
            confirmClearClipRecords();
        } else {
            removeAllClipRecords();
        }
    }

    public void confirmAppendClipRecord(final ClipSourceProvider provider,
                                        final boolean executeAtOnce) {
        if (provider == null) {
            return;
        }

        // TBD
        // Execute without confirm
        appendClipRecord(provider, executeAtOnce);
        if (true) {
            return;
        }

        HashSet<Object> sources = provider.getMutualSources();

        if (sources != null && sources.size() > 0) {
            boolean directAppend = true;
            boolean directDiscard = false;
            int failedCount = 0;
            ArrayList<String> noFullPermission = new ArrayList<String>();
            int actionType = ClipSourceProvider
                    .getActionTypeFromClipType(provider.getClipType());
            boolean doWrite = (actionType == MutualSourceProvider.ACTION_TYPE_WRITE_SOURCE);
            boolean fullPermission = true;
            for (Object source : sources) {
                if (source instanceof FileItem) {
                    if (doWrite) {
                        fullPermission = FileUtil
                                .hasFullReadWritePermission(
                                        ((FileItem) source).getPath(),
                                        noFullPermission);
                        if (!fullPermission) {
                            directAppend = false;
                            File file = new File(((FileItem) source).getPath());
                            failedCount += (file.canRead() && file.canWrite()) ? 0
                                    : 1;
                        }
                    } else {
                        fullPermission = FileUtil
                                .hasFullReadPermission(
                                        ((FileItem) source).getPath(),
                                        noFullPermission);
                        if (!fullPermission) {
                            directAppend = false;
                            File file = new File(((FileItem) source).getPath());
                            failedCount += file.canRead() ? 0 : 1;
                        }
                    }
                }
            }

            directDiscard = (failedCount == sources.size());

            if (directAppend) {
                appendClipRecord(provider, executeAtOnce);
            } else if (directDiscard) {
                Toast.makeText(getContext(), R.string.permission_insufficient,
                        Toast.LENGTH_SHORT).show();
            } else {
                float clipType = provider.getClipType();
                // Just show toast for single file clip
                if (clipType == ClipSourceProvider.CLIP_TYPE_RENAME_SINGLE_SOURCE) {
                    Toast.makeText(getContext(),
                            R.string.cannot_rename_single_prompt,
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                // Show dialog for multiple file clip
                Resources resources = getContext().getResources();
                int size = noFullPermission.size();
                String firstNoPermission = (size > 0) ? noFullPermission.get(0)
                        : null;
                String noPermissions;
                String pronoun;
                if (size > 1) {
                    noPermissions = String.format("%s, ...(%s %d %s)",
                            firstNoPermission,
                            resources.getString(R.string.total), size,
                            resources.getString(R.string.items));
                    pronoun = resources.getString(R.string.theseFiles);
                } else {
                    noPermissions = firstNoPermission;
                    pronoun = resources.getString(R.string.theFile);
                }

                String clipTypeStr = null;
                String promptMessage = null;
                if (clipType == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE) {
                    clipTypeStr = resources.getString(R.string.copy);
                    promptMessage = resources
                            .getString(R.string.cannot_copy_prompt,
                                    noPermissions, pronoun);
                } else if (clipType == ClipSourceProvider.CLIP_TYPE_CUT_SOURCE) {
                    clipTypeStr = resources.getString(R.string.cut);
                    promptMessage = resources
                            .getString(R.string.cannot_move_prompt,
                                    noPermissions, pronoun);
                } else if (clipType == ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE) {
                    clipTypeStr = resources.getString(R.string.delete);
                    promptMessage = resources.getString(
                            R.string.cannot_delete_prompt, noPermissions,
                            pronoun);
                }

                AlertDialog dialog = new AlertDialog.Builder(getContext())
                        .setTitle(clipTypeStr)
                        .setPositiveButton(R.string.continue_op,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        appendClipRecord(provider,
                                                executeAtOnce);
                                    }
                                }).setNegativeButton(R.string.cancel, null)
                        .setMessage(promptMessage).create();
                dialog.show();
            }
        }
    }

    @Override
    public boolean consumeChoice(HashSet<Object> choices,
                                 MultiChoiceConsumeOptions options) {
        if (choices == null || options == null) {
            return false;
        }

        float clipType;
        if (options.consumeDescription.equals(CLIP_CUT)) {
            clipType = ClipSourceProvider.CLIP_TYPE_CUT_SOURCE;
        } else if (options.consumeDescription.equals(CLIP_COPY)) {
            clipType = ClipSourceProvider.CLIP_TYPE_COPY_SOURCE;
        } else if (options.consumeDescription.equals(CLIP_DELETE)) {
            clipType = ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE;
        } else if (options.consumeDescription.equals(CLIP_RENAME_SINGLE)) {
            clipType = ClipSourceProvider.CLIP_TYPE_RENAME_SINGLE_SOURCE;
        } else {
            clipType = ClipSourceProvider.CLIP_TYPE_UNKNOWN;
        }

        final ClipSourceProvider provider = new ClipSourceProvider(clipType);
        HashMap<Object, Float> conflictions = new HashMap<Object, Float>();
        provider.setMutualSources(choices, conflictions);
        if (conflictions.size() == choices.size()) {
            Log.i(TAG, "No source is available.");
            Toast.makeText(
                    getContext(),
                    getContext().getResources()
                            .getString(R.string.target_inUse),
                    Toast.LENGTH_SHORT).show();
        } else {
            if (conflictions.isEmpty()) {
                Log.i(TAG, "successfully to set all mutual source!");
                if (clipType == ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE) {
                    String lastFileName = "";
                    int count = 0;
                    for (Object object : choices) {
                        if (object instanceof FileItem) {
                            count += 1;
                            lastFileName = FileUtil
                                    .getFileName(((FileItem) object).getPath());
                        }
                    }
                    Resources resources = getContext().getResources();
                    String target = (count > 1) ? String.format("%s...(%d %s)",
                            lastFileName, count,
                            resources.getString(R.string.items)) : lastFileName;
                    String deleteMessage = resources.getString(
                            R.string.delete_prompt, target);
                    confirmDeleteOperation(provider, deleteMessage);
                } else if (clipType == ClipSourceProvider.CLIP_TYPE_RENAME_SINGLE_SOURCE) {
                    int count = provider.getMutualSourceCount();
                    final String srcFilePath;
                    if (count > 0) {
                        Object[] sources = provider.getMutualSources()
                                .toArray();
                        Object srcSource = sources[0];
                        if (srcSource instanceof FileItem) {
                            srcFilePath = ((FileItem) srcSource).getPath();
                            Dialog renameDialog = createRenameSingleDialog(srcFilePath);
                            renameDialog.show();
                        }
                    }
                } else {
                    confirmAppendClipRecord(provider, false);
                }
            } else {
                String failedFiles = "";
                for (Object object : conflictions.keySet()) {
                    if (object instanceof FileItem) {
                        String fileName = FileUtil
                                .getFileName(((FileItem) object).getPath());
                        failedFiles += (failedFiles.equals("")) ? fileName
                                : (", " + fileName);
                    }
                }

                String conflictionMessage = getContext().getResources()
                        .getString(R.string.detect_file_inUse, failedFiles);

                confirmConfliction(provider, conflictionMessage);
            }
        }

        return true;
    }

    public void executeClipRecord(int position) {
        ClipItemAdapter adapter = getItem(position);
        if (adapter == null) {
            return;
        }

        HashSet<FileClip> toExecute = adapter.prepare();
        adapter.executeFileClip(toExecute);
    }

    public ArrayList<ClipItemAdapter> getClipItemAdapters() {
        return mClipItemAdapters;
    }

    @Override
    public String getClipPasteDir() {
        if (mClipHistoryCallback != null) {
            return mClipHistoryCallback.getClipPasteDir();
        } else {
            return null;
        }
    }

    @Override
    public DeleteDelegate getDeleteDelegate() {
        if (mClipHistoryCallback != null
                && (mClipHistoryCallback instanceof DeleteDelegate)) {
            return ((DeleteDelegate) mClipHistoryCallback);
        } else {
            return null;
        }
    }

    @Override
    public PasteDelegate getPasteDelegate() {
        if (mClipHistoryCallback != null
                && (mClipHistoryCallback instanceof PasteDelegate)) {
            return ((PasteDelegate) mClipHistoryCallback);
        } else {
            return null;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ClipGroupViewHolder viewHolder;

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.clip_group, null);

            viewHolder = new ClipGroupViewHolder();
            viewHolder.indicatorImageView = (ImageView) convertView
                    .findViewById(R.id.clip_indicator);
            viewHolder.titleTextView = (TextView) convertView
                    .findViewById(R.id.clip_title);
            viewHolder.oneshotTaskView = convertView
                    .findViewById(R.id.clip_oneshot_task);
            viewHolder.clipContentView = convertView
                    .findViewById(R.id.clip_content);
            viewHolder.oneshotStatus = (TextView) convertView
                    .findViewById(R.id.oneshot_status);
            viewHolder.oneshotProgress = (ProgressBar) convertView
                    .findViewById(R.id.oneshot_progress);

            viewHolder.clipListView = (GridView) convertView
                    .findViewById(R.id.clip_list);
            viewHolder.executeButton = (ImageButton) convertView
                    .findViewById(R.id.executeClip);
            viewHolder.removeButton = (ImageButton) convertView
                    .findViewById(R.id.removeClip);
            viewHolder.cancelButton = (ImageButton) convertView
                    .findViewById(R.id.cancelTask);

            viewHolder.repeatableTasksList = (ListView) convertView
                    .findViewById(R.id.repeatTasks);

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ClipGroupViewHolder) convertView.getTag();
        }

        final ClipItemAdapter clipItemAdapter = getItem(position);
        clipItemAdapter.bindInterativeClipUi(viewHolder.clipContentView,
                viewHolder.oneshotTaskView, viewHolder.oneshotStatus,
                viewHolder.oneshotProgress, viewHolder.repeatableTasksList);

        viewHolder.clipListView.setAdapter(clipItemAdapter);
        int displayItemNum = clipItemAdapter.getDisplayItemNum();
        viewHolder.clipListView
                .setNumColumns(displayItemNum > 0 ? displayItemNum : 6);
        viewHolder.clipListView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    return true;
                }
                return false;
            }

        });
        final int pos = position;
        viewHolder.executeButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                executeClipRecord(pos);
            }

        });
        viewHolder.removeButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                removeClipRecord(pos);
            }

        });
        viewHolder.cancelButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                cancelClipRecord(pos);
            }

        });

        Resources resouces = getContext().getResources();
        float clipType = clipItemAdapter.getClipType();
        String clipTypeStr = null;
        int clipTypeIcon = -1;
        if (clipType == ClipSourceProvider.CLIP_TYPE_COPY_SOURCE) {
            clipTypeStr = resouces.getString(R.string._copyItem);
            clipTypeIcon = R.drawable.copy_indicator;
        } else if (clipType == ClipSourceProvider.CLIP_TYPE_CUT_SOURCE) {
            clipTypeStr = resouces.getString(R.string._cutItem);
            clipTypeIcon = R.drawable.cut_indicator;
        } else if (clipType == ClipSourceProvider.CLIP_TYPE_DELETE_SOURCE) {
            clipTypeStr = resouces.getString(R.string._deleteItem);
            clipTypeIcon = R.drawable.delete_indicator;
        }

        viewHolder.indicatorImageView.setImageResource(clipTypeIcon);
        String clipTitle = String.format("%d%s   %s",
                clipItemAdapter.getCount(), clipTypeStr,
                clipItemAdapter.getId());
        viewHolder.titleTextView.setText(clipTitle);

        return convertView;
    }

    public void onClipHistoryChanged() {
        if (mCurrentClearPrompt != null && getCount() == 0) {
            mCurrentClearPrompt.dismiss();
        }

        if (mClipHistoryCallback != null) {
            mClipHistoryCallback.onClipHistoryChanged(this);
        }
    }

    @Override
    public void onClipItemFinished(ClipItemAdapter adapter) {
        if (adapter != null) {
            this.remove(adapter);
            this.onClipHistoryChanged();
        }
    }

    @Override
    public void onClipItemRemoved(ClipItemAdapter adapter) {
        if (adapter != null) {
            this.remove(adapter);
            this.onClipHistoryChanged();
        }
    }

    @Override
    public void onClipSourceChanged(ClipSourceProvider provider) {
        if (provider == null) {
            return;
        }

        if (provider.getMutualSourceCount() == 0) {
            HashSet<MutualAdapter> adapters = provider.getBoundMutualAdapters();
            if (adapters.size() > 0) {
                for (MutualAdapter adapter : adapters) {
                    if (adapter instanceof ClipItemAdapter) {
                        this.remove((ClipItemAdapter) adapter);
                        this.onClipHistoryChanged();
                    }
                }
            }
        }
    }

    public void setClipHistoryCallback(ClipHistoryCallback callback) {
        mClipHistoryCallback = callback;
    }

    private void appendClipRecord(ClipSourceProvider provider,
                                  boolean executeAtOnce) {
        if (provider == null) {
            return;
        }

        Time time = new Time();
        time.setToNow();

        String clipItemId = String.format("%02d:%02d:%02d", time.hour,
                time.minute, time.second);
        ClipItemAdapter adapter = new ClipItemAdapter(getContext(), null,
                R.layout.clip_item, null, null);
        adapter.setId(clipItemId);
        adapter.setClipItemManager(this);
        adapter.setDisplayItemNum(6);
        adapter.setMutualSourceProcessor(mClipGenerator);

        // register this as the callback for the given source provider
        provider.registerClipSourceCallbacks(this);
        // bind adapter to the given source provider
        provider.bindMutualAdapter(adapter);

        this.add(adapter);
        this.onClipHistoryChanged();

        if (executeAtOnce) {
            HashSet<FileClip> toExecute = adapter.prepare();
            adapter.executeFileClip(toExecute);
        }
    }

    private void confirmClearClipRecords() {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.clear)
                .setPositiveButton(R.string.execute_background,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                ClipHistoryAdapter.this.removeAllClipRecords();
                            }
                        })
                .setNegativeButton(R.string.cancelAndClear,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                ClipHistoryAdapter.this.cancelAllClipRecords();
                                ClipHistoryAdapter.this.removeAllClipRecords();
                            }
                        }).setMessage(R.string.confirm_clear_clips).create();

        mCurrentClearPrompt = dialog;
        dialog.show();
    }

    private void confirmConfliction(final ClipSourceProvider provider,
                                    String conflictionMessage) {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.conflict)
                .setPositiveButton(R.string.skipAndContinue,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                confirmAppendClipRecord(provider, false);
                            }
                        }).setNegativeButton(R.string.cancel, null)
                .setMessage(conflictionMessage).create();

        dialog.show();
    }

    private void confirmDeleteOperation(final ClipSourceProvider provider,
                                        String deleteMessage) {
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                confirmAppendClipRecord(provider, true);
                            }
                        }).setNegativeButton(R.string.cancel, null)
                .setMessage(deleteMessage).create();

        dialog.show();
    }

    private Dialog createRenameSingleDialog(final String srcFilePath) {
        View contentRootView = LayoutInflater.from(getContext()).inflate(
                R.layout.rename_dialog, null);
        final EditText nameEdit = (EditText) contentRootView
                .findViewById(R.id.name_edit);
        final String oldName = FileUtil.getFileName(srcFilePath);
        nameEdit.setText(oldName);
        nameEdit.selectAll();

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.rename)
                .setView(contentRootView)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog,
                                                int which) {
                                Context context = getContext();
                                Resources resources = context.getResources();
                                String newName = nameEdit.getText().toString();

                                if (oldName.equals(newName)) {
                                    int ret = FileUtil.ERROR_FILE_EXISTS;
                                    String errStr = FileUtil
                                            .getDefaultFileOpErrStr(resources,
                                                    ret);
                                    Toast.makeText(
                                            context,
                                            resources.getString(
                                                    R.string.rename_failed,
                                                    oldName, errStr),
                                            Toast.LENGTH_LONG).show();
                                    dialog.dismiss();
                                    return;
                                }

                                String targetFilePath;
                                int pos = srcFilePath.lastIndexOf(oldName);
                                if (pos > -1) {
                                    targetFilePath = srcFilePath.substring(0,
                                            pos)
                                            + newName
                                            + srcFilePath.substring(pos
                                            + oldName.length(),
                                            srcFilePath.length());
                                } else {
                                    targetFilePath = srcFilePath;
                                }

                                dialog.dismiss();
                                ClipItemAdapter.renameSingleFile(context,
                                        srcFilePath, targetFilePath);
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

    private void removeAllClipRecords() {
        clear();
        onClipHistoryChanged();
    }

    private void removeClipRecord(int pos) {
        ClipItemAdapter adapter = getItem(pos);

        if (adapter != null) {
            remove(adapter);
            onClipHistoryChanged();
        }
    }

}

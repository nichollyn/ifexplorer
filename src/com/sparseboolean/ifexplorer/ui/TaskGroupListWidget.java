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

package com.sparseboolean.ifexplorer.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.sparseboolean.ifexplorer.DeviceItem;
import com.sparseboolean.ifexplorer.R;

public class TaskGroupListWidget extends LinearLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "IfManager-TaskGroupWidget";

    @SuppressWarnings("unused")
    private static final int DLG_CONFIRM_UNMOUNT = 1;
    @SuppressWarnings("unused")
    private static final int DLG_ERROR_UNMOUNT = 2;

    private static final int MSG_FINISH_WAIT_EJECT = 1;

    private static final int DELAY_EJECT_WAIT = 7000;

    private final Context mContext;
    private int mTag = -1;

    private boolean mEjectable = false;
    private boolean mEjecting = false;
    private String mDefaultGroupLabel;

    private View mEjectButton;
    private ViewGroup mGroupHeader;
    private TextView mDisplayGroupLabel;
    private ImageView mFoldIndicator;

    private AbsListView mActivateListView;
    private ListView mTaskListView;
    private GridView mTaskGridView;

    private Handler mUiHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_FINISH_WAIT_EJECT:
                mEjecting = false;
                break;
            default:
                break;
            }
        }
    };

    public TaskGroupListWidget(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TaskGroupListWidget(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);
        mContext = context;

        LayoutInflater layoutInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        layoutInflater.inflate(R.layout.task_group_widget, this);

        initializeViews();
    }

    public GridView activateGridMode() {
        ListAdapter adapter = null;
        if (mActivateListView != null) {
            adapter = mActivateListView.getAdapter();
        }

        mActivateListView = mTaskGridView;
        if (adapter != null) {
            mActivateListView.setAdapter(adapter);
        }

        mTaskGridView.setVisibility(View.VISIBLE);
        mTaskListView.setVisibility(View.GONE);

        return (GridView) mActivateListView;
    }

    public ListView activateListMode() {
        ListAdapter adapter = null;
        if (mActivateListView != null) {
            adapter = mActivateListView.getAdapter();
        }

        mActivateListView = mTaskListView;
        if (adapter != null) {
            mActivateListView.setAdapter(adapter);
        }

        mTaskListView.setVisibility(View.VISIBLE);
        mTaskGridView.setVisibility(View.GONE);

        return (ListView) mActivateListView;
    }

    public String getDefaultGroupLabel() {
        return mDefaultGroupLabel;
    }

    public int getGroupTag() {
        return mTag;
    }

    public void hideHeader() {
        mEjectButton.setVisibility(View.GONE);
        mGroupHeader.setVisibility(View.GONE);
    }

    public void initializeViews() {
        mGroupHeader = (ViewGroup) findViewById(R.id.task_group_header);
        mGroupHeader.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActivateListView.isShown()) {
                    mActivateListView.setVisibility(View.GONE);
                    mFoldIndicator.setImageResource(R.drawable.unfold);
                } else {
                    mActivateListView.setVisibility(View.VISIBLE);
                    mFoldIndicator.setImageResource(R.drawable.fold);
                    ;
                }
            }
        });
        mGroupHeader.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // Remove comment on the follow code if we want fold indicator
                // to be visible
                // only when the header is focused or hover on
                // mFoldIndicator.setVisibility(hasFocus ? View.VISIBLE :
                // View.GONE);
            }
        });

        mDisplayGroupLabel = (TextView) findViewById(R.id.task_group_label);
        mFoldIndicator = (ImageView) findViewById(R.id.fold_indicator);
        mTaskListView = (ListView) findViewById(R.id.task_items_listview);
        mTaskGridView = (GridView) findViewById(R.id.task_items_gridview);
        // default activate list mode
        activateListMode();

        mEjectButton = findViewById(R.id.eject_button);
        mEjectButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mActivateListView.getAdapter() instanceof DeviceDataAdapter) {
                    if (mEjecting) {
                        showEjectingDialog();
                    } else {
                        showEjectConfirmDialog();
                    }
                }
            }
        });
    }

    public void setDefaultGroupLabel(String groupLabel) {
        mDefaultGroupLabel = groupLabel;
        setLabel(mDefaultGroupLabel);
    }

    public void setEjectable(boolean ejectable) {
        mEjectable = ejectable;
        if (mEjectable) {
            mEjectButton.setVisibility(View.VISIBLE);
        } else {
            mEjectButton.setVisibility(View.GONE);
        }
    }

    public void setGroupTag(int tag) {
        mTag = tag;
    }

    public void setLabel(String label) {
        if (label == null) {
            return;
        }

        if (label.length() > 24) {
            String display = label.substring(0, 19) + "...";
            mDisplayGroupLabel.setText(display);
        } else {
            mDisplayGroupLabel.setText(label);
        }
    }

    public void showHeader() {
        if (mEjectable) {
            mEjectButton.setVisibility(View.VISIBLE);
        }

        mGroupHeader.setVisibility(View.VISIBLE);
    }

    private void doUnmountAllEjectableTask() {
        // Show a toast at first
        String inform = String.format("%s %s", mDisplayGroupLabel.getText(),
                mContext.getResources().getString(R.string.eject_inform_text));
        Toast.makeText(mContext, inform, Toast.LENGTH_SHORT).show();

        int result = DeviceItem.UNMOUNT_ERROR;
        for (int i = 0; i < mActivateListView.getCount(); i++) {
            Object taskItem = mActivateListView.getItemAtPosition(i);
            if (taskItem instanceof DeviceItem) {
                result = ((DeviceItem) taskItem).eject();
            }

            if (result != DeviceItem.UNMOUNT_EXECUTED) {
                break;
            }
        }

        if (result != DeviceItem.UNMOUNT_EXECUTED) {
            mEjecting = false;
            showEjectFailedDialog();
        } else {
            mUiHandler.sendEmptyMessageDelayed(MSG_FINISH_WAIT_EJECT,
                    DELAY_EJECT_WAIT);
        }
    }

    private void showEjectConfirmDialog() {
        String title = String.format("%s %s?", mContext.getResources()
                .getString(R.string.eject_), mDisplayGroupLabel.getText());
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog,
                                    int which) {
                                mEjecting = true;
                                doUnmountAllEjectableTask();
                            }
                        }).setNegativeButton(R.string.cancel, null)
                .setMessage(R.string.dlg_confirm_eject_text).create();

        dialog.show();
    }

    private void showEjectFailedDialog() {
        String title = String.format("%s %s %s!", mContext.getResources()
                .getString(R.string.eject_), mDisplayGroupLabel.getText(),
                mContext.getResources().getString(R.string.failed));
        AlertDialog dialog = new AlertDialog.Builder(mContext).setTitle(title)
                .setNeutralButton(R.string.dlg_ok, null)
                .setMessage(R.string.dlg_eject_failed_text).create();

        dialog.show();
    }

    private void showEjectingDialog() {
        String title = String.format("%s %s", mContext.getResources()
                .getString(R.string.ejecting_), mDisplayGroupLabel.getText());
        AlertDialog dialog = new AlertDialog.Builder(mContext).setTitle(title)
                .setNeutralButton(R.string.dlg_ok, null)
                .setMessage(R.string.dlg_prompt_ejecting_text).create();

        dialog.show();
    }
}
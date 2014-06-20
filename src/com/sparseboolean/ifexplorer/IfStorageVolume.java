package com.sparseboolean.ifexplorer;

import android.content.Context;

public class IfStorageVolume {
    private String mDevNode;

    private String mPath;

    private int mDescriptionId;
    private boolean mPrimary;
    private boolean mRemovable;
    private boolean mEmulated;
    private String mFilesystemFormat;

    public IfStorageVolume(String path, boolean primary, boolean removable,
            boolean emulated) {
        mPath = path;
        mPrimary = primary;
        mRemovable = removable;
        mEmulated = emulated;
    }

    public IfStorageVolume(String devNode, String path, int descriptionId,
            boolean primary, boolean removable, boolean emulated,
            String fsFormat) {
        mDevNode = devNode;
        mPath = path;
        mDescriptionId = descriptionId;
        mPrimary = primary;
        mRemovable = removable;
        mEmulated = emulated;
        mFilesystemFormat = fsFormat;
    }

    @Override
    public boolean equals(Object o) {
        IfStorageVolume other = (IfStorageVolume) o;
        return mPath.equals(other.getPath());
    }

    public String getDescription(Context context) {
        return context.getResources().getString(mDescriptionId);
    }

    public int getDescriptionId() {
        return mDescriptionId;
    }

    public String getDevNode() {
        return mDevNode;
    }

    public String getFilesystemFormat() {
        return mFilesystemFormat;
    }

    public String getPath() {
        return mPath;
    }

    @Override
    public int hashCode() {
        return mPath.hashCode();
    }

    public boolean isEmulated() {
        return mEmulated;
    }

    public boolean isPrimary() {
        return mPrimary;
    }

    public boolean isRemovable() {
        return mRemovable;
    }

    public void setDescriptionId(int resId) {
        mDescriptionId = resId;
    }

    public void setDevNode(String devNode) {
        mDevNode = devNode;
    }

    public void setFilesystemFormat(String fsFormat) {
        mFilesystemFormat = fsFormat;
    }

    public void setPath(String path) {
        mPath = path;
    }

    public void setPrimary(boolean primary) {
        mPrimary = primary;
    }

    public void setRemovable(boolean removable) {
        mRemovable = removable;
    }
}

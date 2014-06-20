package com.sparseboolean.ifexplorer;

import gem.kevin.util.DataUtil;
import gem.kevin.util.FileUtil;
import gem.kevin.util.StorageUtil;

import java.io.File;
import java.util.Comparator;

import com.sparseboolean.ifexplorer.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;

public class FileItem {
    @SuppressWarnings("unused")
    private static final String TAG = "IfExplorer-FileItem";

    public static final String FILE_TYPE_FILE_FOLDER = "$file_folder";

    private static int INT_UNINITIALIED = -1;

    private Context mContext;

    private String mPath = null;
    private String mName = null;

    private int mIconResId = INT_UNINITIALIED;
    private Drawable mIconDrawable = null;
    private boolean mIsSystemFile = false;
    private boolean mIsDirectory = false;
    private boolean mReadOnly = false;
    private boolean mReadWriteDeny = false;
    private String mPermissionStr = null;
    private String mFileType;

    private long mSize = INT_UNINITIALIED;

    private View mConvertView;
    private int mPositionInAdapterList = INT_UNINITIALIED;

    private boolean mVisible = true;

    public void setVisible(boolean visible) {
        mVisible = visible;
    }

    public boolean isVisible() {
        return mVisible;
    }

    public FileItem(Context context, String path) {
        this(context, path, null, -1, null);
    }

    public FileItem(Context context, String path, String name, int iconResId,
            Drawable iconDrawable) {
        mContext = context;
        mPath = path;
        mName = name;
        mIconResId = iconResId;
        mIconDrawable = iconDrawable;

        build();
    }

    private void build() {
        File file = new File(mPath);
        Resources resources = mContext.getResources();
        // name
        if (mName == null) {
            mName = FileUtil.getFileName(mPath);
        }

        // type
        mIsDirectory = file.isDirectory();
        if (mIsDirectory) {
            mFileType = FILE_TYPE_FILE_FOLDER;
        } else {
            mFileType = DataUtil.getFileExtensionWithoutDot(mPath);
        }

        // permission
        mIsSystemFile = StorageUtil.isAndroidSysFile(mPath);
        mReadOnly = (file.canRead() && !file.canWrite());
        mReadWriteDeny = (!file.canRead() && !file.canWrite());
        if (mIsSystemFile) {
            mPermissionStr = String.format("(%s)",
                    resources.getString(R.string.system));
        } else {
            if (mReadOnly) {
                mPermissionStr = String.format("(%s)",
                        resources.getString(R.string.readOnly));
            } else if (mReadWriteDeny) {
                mPermissionStr = String.format("(%s)",
                        resources.getString(R.string.no_permission));
            } else {
                mPermissionStr = null;
            }
        }

        // icon and size
        String extension = DataUtil.getFileExtensionWithoutDot(mName);
        if (mIsDirectory) {

            if (mIconDrawable == null && mIconResId == INT_UNINITIALIED) {
                mIconResId = isSystemFile() ? R.drawable.folder_sys
                        : R.drawable.folder;
            }

            String[] list = file.list();
            if (list != null) {
                mSize = list.length;
            }
        } else {
            if (mIconDrawable == null && mIconResId == INT_UNINITIALIED) {
                if (extension.equalsIgnoreCase("apk")) {
                    mIconDrawable = DataUtil.getNonInstalledAppIcon(mContext,
                            mPath);
                } else {
                    mIconResId = DataUtil.getFileIconResId(extension);
                }
            }

            mSize = file.length();
        }
    }

    @Override
    public boolean equals(Object o) {
        FileItem other = (FileItem) o;
        return mPath.equals(other.getPath());
    }

    public View getConvertView() {
        return mConvertView;
    }

    public Drawable getIconDrawable() {
        return mIconDrawable;
    }

    public int getIconResource() {
        return mIconResId;
    }

    public String getName() {
        return mName;
    }

    public String getPath() {
        return mPath;
    }

    public String getPermissionStr() {
        return mPermissionStr;
    }

    public String getFileType() {
        return mFileType;
    }

    public long getSize() {
        return mSize;
    }

    @Override
    public int hashCode() {
        return mPath.hashCode();
    }

    public boolean isDirectory() {
        return mIsDirectory;
    }

    public boolean isReadOnly() {
        return mReadOnly;
    }

    public boolean isReadWriteDeny() {
        return mReadWriteDeny;
    }

    public boolean isSystemFile() {
        return mIsSystemFile;
    }

    public void setConvertView(View convertView) {
        mConvertView = convertView;
    }

    public void setIconDrawable(Drawable drawable) {
        mIconDrawable = drawable;
    }

    public void setPositionInAdapterList(int position) {
        mPositionInAdapterList = position;
    }

    public int getPositionInAdapterList() {
        return mPositionInAdapterList;
    }

    public void setIconResource(int resId) {
        mIconResId = resId;
    }

    public void setName(String name) {
        mName = name;
    }

    public void setPath(String path) {
        mPath = path;
    }
}

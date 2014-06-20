package com.sparseboolean.ifexplorer.ui;

import com.sparseboolean.ifexplorer.R;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ImageView;

public class FileGroupIndex {
    private static final String TAG = "FileGroupIndex";
    private Context mContext;
    private View mView;
    private int mIndexCategory;
    private String mIndexStr;
    private String mIndexSymbol;

    public int CATEGORY_PHONETIC = 1;
    public int CATEGORY_FILE_TYPE = 2;
    public int CATEGORY_FILE_SIZE = 3;

    public FileGroupIndex(Context context) {
        mContext = context;
    }

    public void build(ViewGroup root, int indexCategory, String indexStr,
            String layoutName, String symbolName, String symbolIconName) {
        mIndexCategory = indexCategory;
        mIndexStr = indexStr;

        Resources res = mContext.getResources();
        if (layoutName != null) {
            int layoutResId = res.getIdentifier(layoutName, "layout",
                    mContext.getPackageName());
            if (layoutResId != 0) {
                mView = LayoutInflater.from(mContext)
                        .inflate(layoutResId, root);
            }
        }

        // Inflated from custom layout failed,
        // inflated from default layout.
        if (mView == null) {
            mView = LayoutInflater.from(mContext).inflate(
                    R.layout.scroll_index_default_layout, root);
            TextView symbolText = (TextView) mView
                    .findViewById(R.id.symbol_text);
            ImageView symbolIcon = (ImageView) mView
                    .findViewById(R.id.symbol_icon);

            if (symbolName != null) {
                mIndexSymbol = symbolName;
                symbolText.setText(symbolName);
                symbolText.setVisibility(View.VISIBLE);
                symbolIcon.setVisibility(View.GONE);
            } else {
                if (symbolIconName != null) {
                    int iconResId = res.getIdentifier(layoutName, "drawable",
                            mContext.getPackageName());
                    if (iconResId != 0) {
                        symbolIcon.setImageResource(iconResId);
                        symbolIcon.setVisibility(View.VISIBLE);
                        symbolText.setVisibility(View.GONE);
                    }
                }
            }
        }
    }

    public String getIndexStr() {
        return mIndexStr;
    }

    public String getIndexSymbol() {
        return (mIndexSymbol != null) ? mIndexSymbol : mIndexStr;
    }

    public View getView() {
        return mView;
    }
}

package com.sparseboolean.ifexplorer.ui;

import java.io.InputStream;

import org.json.JSONObject;

import com.sparseboolean.ifexplorer.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class QuickScollBar extends LinearLayout {
    private ViewGroup mIndexContainer;

    public QuickScollBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context).inflate(R.layout.quick_scroll_bar, this);
        mIndexContainer = (ViewGroup) findViewById(R.id.dynamic_indexes);
    }

    public QuickScollBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public void loadIndexesFromJson(int jsonResId) {
        InputStream json = getContext().getResources().openRawResource(
                jsonResId);
        // TODO
        //JSONObject jsonObject = new JSONObject(sdfs);
    }
}

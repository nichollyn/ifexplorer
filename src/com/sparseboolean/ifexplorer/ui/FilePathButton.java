package com.sparseboolean.ifexplorer.ui;

import com.sparseboolean.ifexplorer.R;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;


public class FilePathButton extends FrameLayout {

    public interface FilePathButtonCallback {
        public void onButtonClicked(FilePathButton button);
    }

    String mFilePath;
    TextView mPathLabel;

    ImageButton mPathButton;

    FilePathButtonCallback mCallback;

    public FilePathButton(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FilePathButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater.from(context).inflate(R.layout.file_path_button, this);
        mPathLabel = (TextView) findViewById(R.id.path_label);
        mPathButton = (ImageButton) findViewById(R.id.path_button);
        mPathButton.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mCallback != null) {
                    mCallback.onButtonClicked(FilePathButton.this);
                }
            }
        });
    }

    public FilePathButton(Context context, String path,
            FilePathButtonCallback callback) {
        this(context, null, 0);

        mFilePath = path;
        mCallback = callback;
    }

    public ImageButton getButton() {
        return mPathButton;
    }

    public String getFilePath() {
        return mFilePath;
    }

    public TextView getLabelTextView() {
        return mPathLabel;
    }

    public void setIcon(int resId) {
        mPathButton.setImageResource(resId);
    }

    public void setLabel(String label) {
        mPathLabel.setText(label);
    }

    public void setLabelColor(int color) {
        mPathLabel.setTextColor(color);
    }
}

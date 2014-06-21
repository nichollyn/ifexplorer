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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.sparseboolean.ifexplorer.R;

public class RingColorBar extends LinearLayout {
    private int mFreeSpaceColor;
    private int mOccupyingSpaceColor;

    private Context mContext;

    private float mOccupyingRatio = 0.8f;
    private int mRingWidth = 6;

    final RectF mRectF = new RectF();
    final Paint mPaint = new Paint();

    public RingColorBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        setWillNotDraw(false);

        mFreeSpaceColor = mContext.getResources().getColor(
                R.color.storage_space_free);
    }

    public void setRatios(float occupyingRatio, float freeRatio) {
        mOccupyingRatio = occupyingRatio;
        if (mOccupyingRatio <= 0.7f) {
            mOccupyingSpaceColor = mContext.getResources().getColor(
                    R.color.storage_space_sufficient);
        } else if (mOccupyingRatio > 0.7f && mOccupyingRatio < 0.9f) {
            mOccupyingSpaceColor = mContext.getResources().getColor(
                    R.color.storage_space_warning);
        } else if (mOccupyingRatio >= 0.9f) {
            mOccupyingSpaceColor = mContext.getResources().getColor(
                    R.color.storage_space_run_out);
        }

        invalidate();
    }

    private void updateIndicator() {
        int xoff = getPaddingLeft() - getPaddingRight();
        int yoff = getPaddingTop() - getPaddingBottom();
        if (xoff < 0)
            xoff = 0;
        if (yoff < 0)
            yoff = 0;
        mRectF.left = xoff + mRingWidth / 2;
        mRectF.top = yoff + mRingWidth / 2;
        mRectF.right = getWidth() - mRingWidth;
        mRectF.bottom = getHeight() - mRingWidth;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateIndicator();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float startAngle = -90.0f;
        float sweepAngle = mOccupyingRatio * 360.0f;

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setAntiAlias(true);
        mPaint.setStrokeWidth(mRingWidth);

        mPaint.setColor(mFreeSpaceColor);
        canvas.drawArc(mRectF, startAngle, 360, false, mPaint);

        mPaint.setColor(mOccupyingSpaceColor);
        canvas.drawArc(mRectF, startAngle, sweepAngle, false, mPaint);
    }
}
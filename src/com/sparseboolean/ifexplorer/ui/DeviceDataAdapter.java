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

import gem.kevin.util.StorageUtil;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.sparseboolean.ifexplorer.DeviceItem;
import com.sparseboolean.ifexplorer.R;

/**
 * A class to handle displaying a custom view in the ListView that is used in
 * the device list. If any icons are to be added, they must be implemented in
 * the getView method. This class is instantiated once in Main and has no reason
 * to be instantiated again.
 * 
 * @author Kevin Lin
 */
public class DeviceDataAdapter extends ArrayAdapter<DeviceItem> {
    private static class ViewHolder {
        TextView labelTextView;
        ImageView icon;
    }

    @SuppressWarnings("unused")
    private static final String TAG = "IfManager-DeviceDataAdapter";

    private Context mContext;
    private int mIfTag = -1;

    public DeviceDataAdapter(Context context, ArrayList<DeviceItem> dataSource) {
        super(context, R.layout.device_item, dataSource);
        mContext = context;
    }

    public int getIfTag() {
        return mIfTag;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.device_item, null);

            viewHolder = new ViewHolder();
            viewHolder.labelTextView = (TextView) convertView
                    .findViewById(R.id.device_name);
            viewHolder.icon = (ImageView) convertView
                    .findViewById(R.id.device_icon);

            convertView.setTag(viewHolder);

        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        DeviceItem deviceItem = getItem(position);
        String name = deviceItem.getName();
        int type = deviceItem.getType();

        viewHolder.labelTextView.setText(name);
        switch (type) {
        case StorageUtil.TYPE_UDISK:
            viewHolder.icon.setImageResource(R.drawable.usb_storage);
            break;
        case StorageUtil.TYPE_SDCARD:
            viewHolder.icon.setImageResource(R.drawable.sdcard_storage);
            break;
        case StorageUtil.TYPE_HOME:
            viewHolder.icon.setImageResource(R.drawable.home);
            break;
        case StorageUtil.TYPE_ROOT:
            viewHolder.icon.setImageResource(R.drawable.phone);
            break;
        default:
            viewHolder.icon.setImageResource(R.drawable.usb_storage);
        }

        return convertView;
    }

    public void setIfTag(int ifTag) {
        mIfTag = ifTag;
    }
}

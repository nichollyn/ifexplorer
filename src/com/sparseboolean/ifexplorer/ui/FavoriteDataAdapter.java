/*
    IfExplorer, an open source file manager for the Android system.
    Copyright (C) 2012  Kevin Lin
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

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.sparseboolean.ifexplorer.FavoriteItem;
import com.sparseboolean.ifexplorer.R;

/**
 * A class to handle displaying a custom view in the ListView that is used in
 * the favorite list. If any icons are to be added, they must be implemented in
 * the getView method. This class is instantiated once in Main and has no reason
 * to be instantiated again.
 * 
 * @author Kevin Lin
 */
public class FavoriteDataAdapter extends ArrayAdapter<FavoriteItem> {
    private Context mContext;

    private static class ViewHolder {
        TextView labelTextView;
        ImageView icon;
    }

    public FavoriteDataAdapter(Context context,
            ArrayList<FavoriteItem> dataSource) {
        super(context, R.layout.favorite_item, dataSource);
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.favorite_item, null);

            viewHolder = new ViewHolder();
            viewHolder.labelTextView = (TextView) convertView
                    .findViewById(R.id.favorite_name);
            viewHolder.icon = (ImageView) convertView
                    .findViewById(R.id.favorite_icon);

            convertView.setTag(viewHolder);

        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        FavoriteItem favoriteItem = getItem(position);
        String label = favoriteItem.getName();
        int iconResId = favoriteItem.getIconResource();

        viewHolder.labelTextView.setText(label);
        viewHolder.icon.setImageResource(iconResId);

        return convertView;
    }
}

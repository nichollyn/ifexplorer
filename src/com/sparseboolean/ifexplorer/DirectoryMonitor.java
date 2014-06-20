/*
    IfManager, an open source system manager for the Android system.
    Copyright (C) 2012, 2013  Kevin Lin
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

package com.sparseboolean.ifexplorer;

import android.os.FileObserver;
import android.util.Log;

public class DirectoryMonitor extends FileObserver {
    public interface FileEventHandler {
        public void onAllEvents(String path);
    }

    private static final String TAG = "IfManager-DirectoryMonitor";
    private static final boolean KLOG = false;

    private FileEventHandler mHandler;

    public DirectoryMonitor(String path, FileEventHandler handler) {
        super(path);
        mHandler = handler;
    }

    @Override
    public void onEvent(int event, String path) {
        if (KLOG)
            Log.i(TAG, "Event: " + event + " happened on path: " + path);
        switch (event) {
        case DELETE:
        case CREATE:
        case MOVED_TO:
        case DELETE_SELF:
            // hacking, I've no idea why event 1073742336 is for remove
            // directory, event 1073742080 is for create new directory
            // event 1073741952 is for move directory.
        case 1073742336:
        case 1073742080:
        case 1073741952:
            mHandler.onAllEvents(path);
            break;
        default:
            return;
        }
    }
}

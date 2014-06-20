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

package com.sparseboolean.ifexplorer;

// ifmanager handled intent actions
public enum IfIntentAction {
IF_INTENT_ACTION_NULL(null), IF_ITENT_ACTION_TASK_OVERVIEW(
        "task_overview_ifmanager"), IF_INENT_ACTION_FILE_EXPLORE(
        "file_explore_ifmanager"), IF_INENT_ACTION_MANAGE_PACKAGE_STORAGE(
        "manage_storage_ifmanager");
String _action = null;

IfIntentAction(String action) {
    _action = action;
}

public String getAction() {
    return _action;
}
}

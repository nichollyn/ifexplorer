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

package com.sparseboolean.ifexplorer;

//!! Please also sync update on this source file to IfConfig.java.<specific_product>
public final class IfConfig {
    public static final boolean PRODUCT_IS_MONITOR = false;
    public static final boolean PRODUCT_SUPPORT_KEVIN_VOLD = false;
    public static final boolean SIGNED_WITH_PLATFORM_KEY = false;
    public static final boolean PRODUCT_BUILT_IN_SDCARD = false;
    public static final boolean PRODUCT_ROCK_CHIP = false;
    public static final String PRODUCT_ROCK_CHIP_INTERNAL_DISK = "/mnt/internal_sd";

    public static final boolean FILE_SUPPORT_DRAG = true;

    public static final long PRODUCT_INTERNAL_STORAGE_SIZE = 102 * 1024 * 1024;
    public static final float PRODUCT_INTERNAL_STORAGE_THRESHOLD = 0.1f;

    public static final long PRODUCT_ACCEPTABLE_SDCARD_SIZE = 64 * 1024;

    public static final String MOUNT_PROC_PATH = "/proc/mounts";
    public static final String ANDROID_ASEC_PATH = "/mnt/secure/asec";

    public static final int MAX_SEARCH_DEPTH = 7;
    public static final int MAX_LISTITEM_COUNT = 1000;
}

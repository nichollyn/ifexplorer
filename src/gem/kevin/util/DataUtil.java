package gem.kevin.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;

import com.sparseboolean.ifexplorer.R;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;

public final class DataUtil {
    private static final String TAG = "IfExplorer-DataUtil";

    /* supported media file formats */
    public static ArrayList<String> sSupportedAudios;

    public static ArrayList<String> sSupportedVideos;

    public static ArrayList<String> sSupporteImages;

    public static ArrayList<String> sSupportedAppInstaller;

    /* Reflect class, constructor and methods */
    public static Class<?> sClass_PackageParser = null;
    public static Class<?> sClass_Package = null;
    public static Constructor<?> sConstructor_PackageParser = null;
    public static Constructor<?> sConstructor_AssetManager = null;
    public static Method sMethod_parsePackage = null;
    public static Method sMethod_addAssetPath = null;

    /* Android specific */
    public static final String ANDROID_MANIFEST_FILE = "AndroidManifest.xml";

    /*
     * Utility method to get package information for a given packageURI
     */
    public static ApplicationInfo getApplicationInfo(String archiveFilePath)
            throws FileNotFoundException {
        ApplicationInfo appInfo = null;

        if (sClass_PackageParser == null || sClass_Package == null) {
            try {
                sClass_PackageParser = Class
                        .forName("android.content.pm.PackageParser");
                sClass_Package = Class
                        .forName("android.content.pm.PackageParser$Package");
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (sClass_PackageParser != null && sClass_Package != null) {
            Object pkgInfo = null;

            if (sConstructor_PackageParser == null) {
                try {
                    sConstructor_PackageParser = sClass_PackageParser
                            .getConstructor(String.class);
                } catch (NoSuchMethodException e1) {
                    e1.printStackTrace();
                    return null;
                }
            }

            if (sMethod_parsePackage == null) {
                try {
                    sMethod_parsePackage = sClass_PackageParser.getMethod(
                            "parsePackage", new Class[] { File.class,
                                    String.class, DisplayMetrics.class,
                                    int.class });
                } catch (NoSuchMethodException e2) {
                    e2.printStackTrace();
                }
            }

            if (sMethod_parsePackage != null
                    && sConstructor_PackageParser != null) {
                File sourceFile = new File(archiveFilePath);
                DisplayMetrics metrics = new DisplayMetrics();
                metrics.setToDefaults();

                Object pkgParser = null;

                try {
                    pkgParser = sConstructor_PackageParser
                            .newInstance(archiveFilePath);
                } catch (IllegalArgumentException e1) {
                    e1.printStackTrace();
                } catch (InstantiationException e1) {
                    e1.printStackTrace();
                } catch (IllegalAccessException e1) {
                    e1.printStackTrace();
                } catch (InvocationTargetException e1) {
                    e1.printStackTrace();
                }

                if (pkgParser != null) {
                    try {
                        pkgInfo = sMethod_parsePackage.invoke(pkgParser,
                                sourceFile, archiveFilePath, metrics, 0);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

            }

            if (pkgInfo != null) {
                Field field_applicationInfo = null;
                try {
                    field_applicationInfo = sClass_Package
                            .getField("applicationInfo");
                } catch (NoSuchFieldException e) {
                    e.printStackTrace();
                }

                if (field_applicationInfo != null) {
                    try {
                        appInfo = (ApplicationInfo) field_applicationInfo
                                .get(pkgInfo);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return appInfo;
    }

    /** Returns a bitmap showing a screenshot of the view passed in. */
    public static Bitmap getBitmapFromView(View v) {
        Bitmap bitmap = Bitmap.createBitmap(v.getWidth(), v.getHeight(),
                Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        v.draw(canvas);
        return bitmap;
    }

    /* get file extension */
    public static String getFileExtensionWithoutDot(String uri) {
        int dot = uri.lastIndexOf(".");
        if (dot >= 0) {
            return uri.substring(dot + 1).toLowerCase(Locale.US);
        } else {
            // No extension.
            return "";
        }
    }

    public static int getFileIconResId(File file) {
        if (file != null && file.isFile()) {
            String ext = file.toString();
            String sub_ext = ext.substring(ext.lastIndexOf(".") + 1);

            return getFileIconResId(sub_ext);
        }

        if (file != null && file.isDirectory()) {
            return R.drawable.folder;
        }

        return -1;
    }

    public static int getFileIconResId(String extension) {
        if (extension == null) {
            return R.drawable.unkown_file;
        }

        if (isSupportedAudioFile(extension)) {
            return R.drawable.music;
        } else if (isSupportedImageFile(extension)) {
            return R.drawable.picture;
        } else if (isSupportedVideoFile(extension)) {
            return R.drawable.movie;
        } else if (extension.equalsIgnoreCase("txt")) {
            return R.drawable.textfile;
        } else if (extension.equalsIgnoreCase("apk")) {
            return R.drawable.apk;
        } else {
            return R.drawable.unkown_file;
        }
    }

    public static Locale getLocaleFromContext(Context context) {
        return context.getResources().getConfiguration().locale;
    }

    /* get MIME type from file extension */
    public static String getMimeType(String extension) {
        String mimeType = null;
        if (extension != null) {
            MimeTypeMap map = MimeTypeMap.getSingleton();
            mimeType = map.getMimeTypeFromExtension(extension);
        }

        if (extension.equalsIgnoreCase("flv")) {
            Log.i("tmp", "FLV mime type string: " + mimeType);
        }

        return mimeType;
    }

    /* Utility method to get icon for APK not installed yet */
    public static Drawable getNonInstalledAppIcon(Context pContext,
            String archiveFilePath) {
        Drawable icon = null;

        ApplicationInfo appInfo = null;
        try {
            appInfo = getApplicationInfo(archiveFilePath);
        } catch (FileNotFoundException e1) {
            if (e1.getMessage().contains(ANDROID_MANIFEST_FILE)) {
                Log.w(TAG, "Can't find " + ANDROID_MANIFEST_FILE + " for "
                        + archiveFilePath);
            } else {
                e1.printStackTrace();
            }
        }
        if (appInfo == null) {
            icon = pContext.getPackageManager().getDefaultActivityIcon();
        }

        Resources pRes = pContext.getResources();
        if (sConstructor_AssetManager == null) {
            try {
                sConstructor_AssetManager = AssetManager.class
                        .getConstructor(new Class[] {});
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        if (sConstructor_AssetManager != null) {
            AssetManager assetManager = null;
            try {
                assetManager = (AssetManager) sConstructor_AssetManager
                        .newInstance(new Object[] {});
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }

            if (assetManager != null) {
                if (sMethod_addAssetPath == null) {
                    try {
                        sMethod_addAssetPath = AssetManager.class.getMethod(
                                "addAssetPath", new Class[] { String.class });
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                        return null;
                    }
                }

                if (sMethod_addAssetPath != null) {
                    try {
                        sMethod_addAssetPath.invoke(assetManager,
                                archiveFilePath);
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }

                Resources res = new Resources(assetManager,
                        pRes.getDisplayMetrics(), pRes.getConfiguration());

                if (appInfo != null && appInfo.icon != 0) {
                    try {
                        icon = res.getDrawable(appInfo.icon).getConstantState()
                                .newDrawable();
                    } catch (Resources.NotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        if (icon == null) {
            icon = pContext.getPackageManager().getDefaultActivityIcon();
        }

        return icon;
    }

    public static void getReflectedUtilAPIs() {
        try {
            sClass_PackageParser = Class
                    .forName("android.content.pm.PackageParser");
            sClass_Package = Class
                    .forName("android.content.pm.PackageParser$Package");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        if (sClass_PackageParser != null && sClass_Package != null) {
            try {
                sConstructor_PackageParser = sClass_PackageParser
                        .getConstructor(String.class);
            } catch (NoSuchMethodException e1) {
                e1.printStackTrace();
            }

            try {
                sMethod_parsePackage = sClass_PackageParser.getMethod(
                        "parsePackage", new Class[] { File.class, String.class,
                                DisplayMetrics.class, int.class });
            } catch (NoSuchMethodException e2) {
                e2.printStackTrace();
            }
        }

        try {
            sConstructor_AssetManager = AssetManager.class
                    .getConstructor(new Class[] {});
        } catch (NoSuchMethodException e3) {
            e3.printStackTrace();
        }

        try {
            sMethod_addAssetPath = AssetManager.class.getMethod("addAssetPath",
                    new Class[] { String.class });
        } catch (NoSuchMethodException e4) {
            e4.printStackTrace();
        }
    }

    /* get supported APK file extension */
    public static ArrayList<String> getSupportedAppInstallerFileExtensions() {
        if (sSupportedAppInstaller != null) {
            return sSupportedAppInstaller;
        } else {
            sSupportedAppInstaller = new ArrayList<String>();

            sSupportedAppInstaller.add("apk");

            return sSupportedAppInstaller;
        }
    }

    /* get supported audio file extension */
    public static ArrayList<String> getSupportedAudioFileExtensions() {
        if (sSupportedAudios != null) {
            return sSupportedAudios;
        } else {
            sSupportedAudios = new ArrayList<String>();

            sSupportedAudios.add("mp3");
            sSupportedAudios.add("wav");
            sSupportedAudios.add("wma");
            sSupportedAudios.add("m4a");
            sSupportedAudios.add("aac");
            sSupportedAudios.add("midi");
            sSupportedAudios.add("mid");
            sSupportedAudios.add("ogg");
            sSupportedAudios.add("flac");
            sSupportedAudios.add("amr");

            return sSupportedAudios;
        }
    }

    /* get supported image file extension */
    public static ArrayList<String> getSupportedImageFileExtensions() {
        if (sSupporteImages != null) {
            return sSupporteImages;
        } else {
            sSupporteImages = new ArrayList<String>();

            sSupporteImages.add("jpg");
            sSupporteImages.add("jpeg");
            sSupporteImages.add("gif");
            sSupporteImages.add("bmp");
            sSupporteImages.add("png");
            sSupporteImages.add("tiff");

            return sSupporteImages;
        }
    }

    /* get supported video file extension */
    public static ArrayList<String> getSupportedVideoFileExtensions() {
        if (sSupportedVideos != null) {
            return sSupportedVideos;
        } else {
            sSupportedVideos = new ArrayList<String>();

            sSupportedVideos.add("3gp");
            sSupportedVideos.add("mp4");
            sSupportedVideos.add("mov");
            sSupportedVideos.add("flv");
            sSupportedVideos.add("f4v");
            sSupportedVideos.add("wmv");
            sSupportedVideos.add("avi");
            sSupportedVideos.add("asf");
            sSupportedVideos.add("vob");
            sSupportedVideos.add("mpg");
            sSupportedVideos.add("mpeg");
            sSupportedVideos.add("ts");
            sSupportedVideos.add("m2ts");
            sSupportedVideos.add("tp");
            sSupportedVideos.add("rmvb");
            sSupportedVideos.add("rm");
            sSupportedVideos.add("mkv");

            return sSupportedVideos;
        }
    }

    public static boolean isSupportedAppInstaller(String extension) {
        return getSupportedAppInstallerFileExtensions().contains(
                extension.toLowerCase(Locale.US));
    }

    public static boolean isSupportedAudioFile(String extension) {
        return getSupportedAudioFileExtensions().contains(
                extension.toLowerCase(Locale.US));
    }

    public static boolean isSupportedImageFile(String extension) {
        return getSupportedImageFileExtensions().contains(
                extension.toLowerCase(Locale.US));
    }

    public static boolean isSupportedVideoFile(String extension) {
        return getSupportedVideoFileExtensions().contains(
                extension.toLowerCase(Locale.US));
    }

    public DataUtil(Context context) {
    }

}

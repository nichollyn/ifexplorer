package gem.kevin.provider;

import java.util.HashSet;

public class ClipSourceProvider extends MutualSourceProvider {

    public static final float CLIP_TYPE_CUT_SOURCE = ACTION_TYPE_WRITE_SOURCE + .1f;
    public static final float CLIP_TYPE_DELETE_SOURCE = ACTION_TYPE_WRITE_SOURCE + .2f;
    public static final float CLIP_TYPE_RENAME_SINGLE_SOURCE = ACTION_TYPE_WRITE_SOURCE + .3f;
    public static final float CLIP_TYPE_BATCH_RENAME_SOURCE = ACTION_TYPE_WRITE_SOURCE + .31f;

    public static final float CLIP_TYPE_COPY_SOURCE = ACTION_TYPE_READ_SOURCE + .1f;

    public static final float CLIP_TYPE_UNKNOWN = ACTION_TYPE_UNKNOWN - .1f;

    // Container for mutual clip source providers
    private static final HashSet<MutualSourceProvider> sClipSourceProviderPool = new HashSet<MutualSourceProvider>();

    private float mClipType;

    public static int getActionTypeFromClipType(float clipType) {
        return (int) clipType;
    }

    public interface ClipSourceCallback {
        public void onClipSourceChanged(ClipSourceProvider provider);
    }

    public ClipSourceProvider(float clipType) {
        super(getActionTypeFromClipType(clipType), sClipSourceProviderPool);
        mClipType = clipType;
    }

    public float getClipType() {
        return mClipType;
    }

    private final HashSet<ClipSourceCallback> mClipSourceCallbacks = new HashSet<ClipSourceCallback>();

    @Override
    public float removeMutualSource(Object source) {
        float result = super.removeMutualSource(source);
        onClipSourceChanged();
        return result;
    }

    @Override
    protected void addMutualSource(Object source) {
        super.addMutualSource(source);
        onClipSourceChanged();
    }

    @Override
    public void clearMutualSources() {
        super.clearMutualSources();
        onClipSourceChanged();
    }

    public void registerClipSourceCallbacks(ClipSourceCallback... callbacks) {
        if (!mClipSourceCallbacks.isEmpty())
            mClipSourceCallbacks.clear();

        for (int i = 0; i < callbacks.length; i++) {
            mClipSourceCallbacks.add(callbacks[i]);
        }
    }

    protected void onClipSourceChanged() {
        for (ClipSourceCallback callback : mClipSourceCallbacks) {
            if (callback != null) {
                callback.onClipSourceChanged(this);
            }
        }
    }
}

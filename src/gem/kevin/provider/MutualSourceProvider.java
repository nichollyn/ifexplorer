package gem.kevin.provider;

import gem.kevin.widget.MutualAdapter;

import java.util.HashMap;
import java.util.HashSet;

import android.util.Log;

public class MutualSourceProvider {
    public interface MutualSourceCallback {
        public void onMutualSourceChanged(MutualSourceProvider provider);
    }

    private static final String TAG = "MutualSourceProvider";

    public static final int ACTION_TYPE_UNKNOWN = -1;

    public static final int ACTION_TYPE_WRITE_SOURCE = 1;

    public static final int ACTION_TYPE_READ_SOURCE = 2;
    public static final float OP_SUCCESS = 0;

    public static final float SOURCE_LOCKED = 27f;
    public static final float SOURCE_EMPTY = 37f;
    public static final float DEFAULT_LOCK_PURPOSE = SOURCE_LOCKED;

    public static final void deMutualize(MutualSourceProvider provider,
            HashSet<MutualSourceProvider> mutualProviderPool) {
        mutualProviderPool.remove(provider);
    }

    public static final void mutualize(MutualSourceProvider provider,
            HashSet<MutualSourceProvider> mutualProviderPool) {
        mutualProviderPool.add(provider);
    }

    private HashSet<Object> mMutualSources;
    private boolean mSourcesInUse = false;
    private float mSourcesLockedPurpose = 0f;
    private HashSet<MutualSourceProvider> mMutualProviderPool;
    private HashSet<MutualAdapter> mBoundAdapters = new HashSet<MutualAdapter>();

    private int mActionType;

    private final HashSet<MutualSourceCallback> mMutualSourceCallbacks = new HashSet<MutualSourceCallback>();

    public MutualSourceProvider(int actionType,
            HashSet<MutualSourceProvider> mutualProviderPool) {
        if (mutualProviderPool == null) {
            throw new ExceptionInInitializerError(
                    "Null mutual provider pool found, "
                            + "stop initialize mutual source provider.");
        } else if (actionType != ACTION_TYPE_READ_SOURCE
                && actionType != ACTION_TYPE_WRITE_SOURCE) {
            throw new ExceptionInInitializerError(
                    "Invalid read/write type found, "
                            + "stop initialize mutual source provider.");
        }

        mMutualSources = new HashSet<Object>();
        mActionType = actionType;

        mutualize(this, mutualProviderPool);
        mMutualProviderPool = mutualProviderPool;
    }

    public void bindMutualAdapter(MutualAdapter adapter) {
        if (adapter == null) {
            return;
        }

        if (!mBoundAdapters.contains(adapter)) {
            mBoundAdapters.add(adapter);
            adapter.setMutualSourceProvider(this);
            updateBoundAdapterData(adapter);
        }
    }

    public void clearMutualSources() {
        this.mMutualSources.clear();

        updateBoundAdapterData();
        onMutualSourceChanged();
    }

    public boolean containMutualSource(Object source) {
        return this.mMutualSources.contains(source);
    }

    public int getActionType() {
        return mActionType;
    }

    public HashSet<MutualAdapter> getBoundMutualAdapters() {
        return mBoundAdapters;
    }

    public int getMutualSourceCount() {
        return mMutualSources.size();
    }

    public HashSet<Object> getMutualSources() {
        return mMutualSources;
    }

    public void lockMutualSources() {
        lockMutualSources(DEFAULT_LOCK_PURPOSE);
    }

    public void lockMutualSources(float externalPurposeCode) {
        mSourcesInUse = true;
        mSourcesLockedPurpose = externalPurposeCode;
    }

    public void registerMutualSourceCallback(MutualSourceCallback... callbacks) {
        if (!mMutualSourceCallbacks.isEmpty()) {
            mMutualSourceCallbacks.clear();
        }

        Log.i("ActionSourceProvider", "register callbacks size: "
                + callbacks.length);
        for (int i = 0; i < callbacks.length; i++) {
            mMutualSourceCallbacks.add(callbacks[i]);
        }
    }

    public float removeMutualSource(Object source) {
        if (!mSourcesInUse) {
            Log.i(TAG, "remove source");
            this.mMutualSources.remove(source);

            updateBoundAdapterData();
            onMutualSourceChanged();

            return OP_SUCCESS;
        } else {
            Log.i(TAG, "Can't remove source: " + source.toString());
            return mSourcesLockedPurpose;
        }
    }

    public void setMutualSources(HashSet<Object> sources,
            HashMap<Object, Float> out_failToSet) {
        if (sources == null) {
            return;
        }

        HashSet<MutualSourceProvider> otherProviders = new HashSet<MutualSourceProvider>();
        otherProviders.addAll(mMutualProviderPool);
        otherProviders.remove(this);

        for (Object source : sources) {
            float setResult = takeMutualSource(source, otherProviders);
            if (setResult != OP_SUCCESS && out_failToSet != null) {
                out_failToSet.put(source, Float.valueOf(setResult));
            }
        }
    }

    public float takeMutualSource(Object source,
            HashSet<MutualSourceProvider> mutuals) {
        if (source == null || containMutualSource(source)) {
            return SOURCE_EMPTY;
        }

        boolean canTake = true;
        float takeResult = OP_SUCCESS;
        /*
         * If action will do write operations to the source, try to make the
         * source exclusively owned by current provider or previous provider who
         * already lock the source
         */
        if (mActionType == ACTION_TYPE_WRITE_SOURCE) {
            for (MutualSourceProvider provider : mutuals) {
                if (provider != null && provider.containMutualSource(source)) {
                    float ret = provider.removeMutualSource(source);
                    if (ret != OP_SUCCESS) {
                        canTake = false;
                        takeResult = ret;
                    }
                }
            }
        } else if (mActionType == ACTION_TYPE_READ_SOURCE) {
            /*
             * If action do read operations to the source, try to make previous
             * provider which will do write operations give up the source if not
             * locked
             */
            for (MutualSourceProvider provider : mutuals) {
                if (provider != null
                        && (provider.getActionType() == ACTION_TYPE_WRITE_SOURCE)
                        && provider.containMutualSource(source)) {
                    float ret = provider.removeMutualSource(source);
                    if (ret != OP_SUCCESS) {
                        canTake = false;
                        takeResult = ret;
                    }
                }
            }
        }

        if (canTake) {
            addMutualSource(source);
        }

        return takeResult;
    }

    public void unbindMutualAdapter(MutualAdapter adapter) {
        if (mBoundAdapters.contains(adapter)) {
            mBoundAdapters.remove(adapter);
            adapter.setMutualSourceProvider(null);
        }
    }

    public void unLockMutualSources() {
        mSourcesInUse = false;
        mSourcesLockedPurpose = 0;
    }

    public void updateBoundAdapterData() {
        for (MutualAdapter adapter : mBoundAdapters) {
            adapter.updateData(mMutualSources);
        }
    }

    public void updateBoundAdapterData(MutualAdapter adapter) {
        if (mBoundAdapters.contains(adapter)) {
            adapter.updateData(mMutualSources);
        }
    }

    protected void addMutualSource(Object source) {
        this.mMutualSources.add(source);

        updateBoundAdapterData();
        onMutualSourceChanged();
    }

    @Override
    protected void finalize() {
        unLockMutualSources();
        deMutualize(this, mMutualProviderPool);
    }

    protected void onMutualSourceChanged() {
        for (MutualSourceCallback callback : mMutualSourceCallbacks) {
            callback.onMutualSourceChanged(this);
        }
    }
}
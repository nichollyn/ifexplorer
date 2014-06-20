package gem.kevin.widget;

import gem.kevin.provider.MutualSourceProvider;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Checkable;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

public abstract class MutualAdapter implements ListAdapter {
    public static interface ViewBinder {
        boolean setViewValue(View view, Object data, String textRepresentation);
    }

    private Context mContext;
    // act as an adapter
    private final DataSetObservable mDataSetObservable = new DataSetObservable();
    private int[] mTo = null;
    private String[] mFrom = null;
    private ViewBinder mViewBinder = null;

    private List<? extends Map<String, ?>> mData = null;
    protected int mResource = -1;
    protected int mDropDownResource = -1;

    protected LayoutInflater mInflater = null;

    private MutualSourceProvider mBoundProvider;

    private MutualSourceProcessor mMutualSourceProcessor;

    public MutualAdapter(Context context, List<? extends Map<String, ?>> data,
            int resource, String[] from, int[] to) {
        mContext = context;
        mData = data;
        mResource = mDropDownResource = resource;
        mFrom = from;
        mTo = to;
        mInflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public boolean areAllItemsEnabled() {
        return true;
    }

    public MutualSourceProvider getBoundMutualSourceProvider() {
        return mBoundProvider;
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public int getCount() {
        return (mData == null) ? 0 : mData.size();
    }

    @Override
    public Object getItem(int position) {
        return (mData == null) ? null : mData.get(position);
    }

    /* implements android.widget.ListAdapter and android.widget.Adapter */

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    public ViewBinder getViewBinder() {
        return mViewBinder;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    public boolean isBound() {
        return (mBoundProvider != null);
    }

    @Override
    public boolean isEmpty() {
        return getCount() == 0;
    }

    @Override
    public boolean isEnabled(int position) {
        return true;
    }

    public void notifyDataSetChanged() {
        mDataSetObservable.notifyChanged();
    }

    public void notifyDataSetInvalidated() {
        mDataSetObservable.notifyInvalidated();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }

    public void setMutualSourceProcessor(MutualSourceProcessor processor) {
        mMutualSourceProcessor = processor;
        if (mBoundProvider != null) {
            updateData(mBoundProvider.getMutualSources());
        }
    }

    public void setMutualSourceProvider(MutualSourceProvider provider) {
        mBoundProvider = provider;
    }

    public void setViewBinder(ViewBinder viewBinder) {
        mViewBinder = viewBinder;
    }

    public void setViewImage(ImageView v, int value) {
        v.setImageResource(value);
    }

    public void setViewImage(ImageView v, String value) {
        try {
            v.setImageResource(Integer.parseInt(value));
        } catch (NumberFormatException nfe) {
            v.setImageURI(Uri.parse(value));
        }
    }

    public void setViewText(TextView v, String text) {
        v.setText(text);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }

    public void updateData(HashSet<Object> mutualSource) {
        // If no processor set yet, just do nothing
        if (mMutualSourceProcessor == null) {
            return;
        }

        mData = mMutualSourceProcessor.buildMutualAdapterData(mutualSource);
        notifyDataSetChanged();
    }

    private void bindView(int position, View view) {
        if (mData == null) {
            return;
        }

        @SuppressWarnings("rawtypes")
        final Map dataSet = mData.get(position);
        if (dataSet == null) {
            return;
        }

        final ViewBinder binder = mViewBinder;
        final String[] from = mFrom;
        final int[] to = mTo;
        final int count = to.length;

        for (int i = 0; i < count; i++) {
            final View v = view.findViewById(to[i]);
            if (v != null) {
                final Object data = dataSet.get(from[i]);
                String text = data == null ? "" : data.toString();
                if (text == null) {
                    text = "";
                }

                boolean bound = false;
                if (binder != null) {
                    bound = binder.setViewValue(v, data, text);
                }

                if (!bound) {
                    if (v instanceof Checkable) {
                        if (data instanceof Boolean) {
                            ((Checkable) v).setChecked((Boolean) data);
                        } else if (v instanceof TextView) {
                            setViewText((TextView) v, text);
                        } else {
                            throw new IllegalStateException(v.getClass()
                                    .getName()
                                    + "should be bound to a Boolean, not a "
                                    + (data == null ? "<unknown type>"
                                            : data.getClass()));
                        }
                    } else if (v instanceof TextView) {
                        // Note: keep the instanceof TextView check at the
                        // bottom of these
                        // ifs since a lot of views are TextViews (e.g.
                        // CheckBoxes)/
                        setViewText((TextView) v, text);
                    } else if (v instanceof ImageView) {
                        if (data instanceof Integer) {
                            setViewImage((ImageView) v, (Integer) data);
                        } else {
                            setViewImage((ImageView) v, text);
                        }
                    } else {
                        throw new IllegalStateException(
                                v.getClass().getName()
                                        + " is not a "
                                        + " view that can be bounds by this MutualAdapter");
                    }
                }
            }
        }
    }

    private View createViewFromResource(int position, View convertView,
            ViewGroup parent, int resource) {
        View v;
        if (convertView == null) {
            v = mInflater.inflate(resource, parent, false);
        } else {
            v = convertView;
        }

        bindView(position, v);

        return v;
    }

    protected List<? extends Map<String, ?>> getData() {
        return mData;
    }
}

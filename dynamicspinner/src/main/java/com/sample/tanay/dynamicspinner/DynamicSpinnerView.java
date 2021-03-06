package com.sample.tanay.dynamicspinner;

import android.annotation.TargetApi;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import org.json.JSONObject;

import java.util.ArrayList;

public class DynamicSpinnerView extends LinearLayout {

    public static final String SETUP_COMPLETE = "org.samagra.SETUP_COMPLETE";
    public static final String SETUP_START = "org.samagra.SETUP_START";
    public static final String SETUP_FAIL = "org.samagra.SETUP_FAIL";

    /**
     * Starts a {@link DataProcessor} thread to
     * read from a JSON file in the assets folder and save the information in
     * an SQLite Database.
     * See {@link DataProcessor#setup(String, SetupListener, int)}
     * for internal working
     *
     * @param context       application context
     * @param filename      the JSON file in the assets folder
     * @param setupListener {@link SetupListener}
     * @param version       the database version number. If you want to use a different file as data
     *                      data source from the old file then the version code needs to be incremented
     */
    public static void setup(Context context, String filename, SetupListener setupListener, int version) {
        DataProcessor.newInstance(context.getApplicationContext()).setup(filename, setupListener, version);
    }

    private DynamicSpinnerViewListener mDynamicSpinnerViewListener;

    private ArrayList<SpinnerElement> mSpinnerElements;
    private ArrayList<ViewInfo> viewInfoArrayList = new ArrayList<>();
    private boolean lazyLoadingEnabled;
    private SearchAdapter mSearchAdapter;

    private @StringRes
    int mSearchPlaceHolderStringId = -1;

    public DynamicSpinnerView(Context context) {
        super(context);
        init();
    }

    public DynamicSpinnerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DynamicSpinnerView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(21)
    public DynamicSpinnerView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void setSearchPlaceHolder(int mSearchPlaceHolderStringId) {
        this.mSearchPlaceHolderStringId = mSearchPlaceHolderStringId;
    }

    private void init() {
        setOrientation(VERTICAL);
        lazyLoadingEnabled = true;
    }

    public void setLazyLoadingEnabled(boolean lazyLoadingEnabled) {
        this.lazyLoadingEnabled = lazyLoadingEnabled;
    }

    /**
     * Set the {@link DynamicSpinnerViewListener} to relay the data loading events
     *
     * @param mDynamicSpinnerViewListener
     */

    public void setDynamicSpinnerViewListener(DynamicSpinnerViewListener mDynamicSpinnerViewListener) {
        this.mDynamicSpinnerViewListener = mDynamicSpinnerViewListener;
    }


    public void load(ArrayList<SpinnerElement> spinnerElements) {
        if (SharedPrefHelper.helper(getContext()).isDbSaved()) {
            this.mSpinnerElements = spinnerElements;
            Log.d("time", "step 7 info fetch start");
            SpinnerThread.getInstance(getContext()).load(spinnerElements, new SpinnerThread.Listener() {
                @Override
                public void onLoadStart() {
                    if (mDynamicSpinnerViewListener != null)
                        mDynamicSpinnerViewListener.onLoadStart();
                }

                @Override
                public void onLoadFailed(Exception exception) {
                    if (mDynamicSpinnerViewListener != null) {
                        mDynamicSpinnerViewListener.onLoadFailed(exception);
                    }
                }

                @Override
                public void onLoadSuccess(DataNode rootNode) {
                    Log.d("time", "step 8 info fetch complete");
                    setup(rootNode);
                    Log.d("time", "step 9 view setup complete");
                }

                @Override
                public void onDatabaseNotExist() {
                    mDynamicSpinnerViewListener.onDatabaseNotExist();
                }
            }, lazyLoadingEnabled);
        } else {
            if (mDynamicSpinnerViewListener != null)
                mDynamicSpinnerViewListener.onDatabaseNotExist();
        }
    }

    private void setupAutocomplete(DataNode rootNode) {
        final ArrayList<DataNode> leafNodes = new ArrayList<>();
        DataNode.populateLeafNodes(leafNodes, rootNode, mSpinnerElements.size(), 0);
        mSearchAdapter = new SearchAdapter(getContext(), android.R.layout.simple_list_item_2,
                android.R.id.text1, leafNodes);

        final AutoCompleteTextView autoCompleteTextView = new AutoCompleteTextView(getContext());

        if (mSearchPlaceHolderStringId != -1) {
            autoCompleteTextView.setHint(mSearchPlaceHolderStringId);
        }

        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                DataNode leafNode = leafNodes.get(position);
                int index = viewInfoArrayList.size() - 1;
                while (index >= 0) {
                    viewInfoArrayList.get(index).itemToBeSelected = leafNode;
                    leafNode = leafNode.parent;
                    index--;
                }
                int sizeMinus1 = viewInfoArrayList.size() - 1;
                for (int i = 0; i < viewInfoArrayList.size(); i++) {
                    ViewInfo viewInfo = viewInfoArrayList.get(i);
                    int pos = DataNode.getPosition(viewInfo.itemToBeSelected, viewInfo.dataset);
                    viewInfo.spinner.setSelection(pos);
                    if (i < sizeMinus1) {
                        ViewInfo nextViewInfo = viewInfoArrayList.get(i + 1);
                        nextViewInfo.dataset.clear();
                        nextViewInfo.dataset.addAll(viewInfo.itemToBeSelected.children);
                        nextViewInfo.adapter.notifyDataSetChanged();
                    }
                }
                autoCompleteTextView.setText("");
            }
        });
        autoCompleteTextView.setAdapter(mSearchAdapter);
        autoCompleteTextView.setThreshold(2);
        addView(autoCompleteTextView);

    }

    private void addOnItemSelectedListener(Spinner spinner) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final int pos = (int) parent.getTag();
                ViewInfo viewInfo = viewInfoArrayList.get(pos);
                DataNode selectedDataNode = viewInfo.dataset.get(position);
                if (viewInfo.previouslySelectedDataNode != selectedDataNode) {
                    viewInfo.previouslySelectedDataNode = selectedDataNode;
                    if (pos < viewInfoArrayList.size() - 1) {
                        if (selectedDataNode.children != null && selectedDataNode.children.size() > 0) {
                            loadChildSpinners(selectedDataNode.children, pos);
                        } else {
                            SpinnerThread.getInstance(getContext()).
                                    load(SpinnerElement.getSubset(pos, mSpinnerElements),
                                            new SpinnerThread.Listener() {
                                                @Override
                                                public void onLoadStart() {
                                                    Log.d("time", "step 10 lazy load start");
                                                    if (mDynamicSpinnerViewListener != null) {
                                                        mDynamicSpinnerViewListener.onLoadStart();
                                                    }
                                                }

                                                @Override
                                                public void onLoadFailed(Exception exception) {
                                                    if (mDynamicSpinnerViewListener != null) {
                                                        mDynamicSpinnerViewListener.onLoadFailed(exception);
                                                    }
                                                }

                                                @Override
                                                public void onLoadSuccess(DataNode rootNode) {
                                                    rootNode.setAsParent(pos + 1,
                                                            mSpinnerElements.size());
                                                    if (mSearchAdapter != null) {
                                                        DataNode.populateLeafNodes(mSearchAdapter.getDataset(),
                                                                rootNode, mSpinnerElements.size(),
                                                                pos + 1);
                                                        mSearchAdapter.notifyDataSetChanged();
                                                    }
                                                    loadChildSpinners(rootNode.children, pos);
                                                    if (mDynamicSpinnerViewListener != null) {
                                                        mDynamicSpinnerViewListener.onLoadComplete();
                                                    }
                                                    Log.d("time", "step 11 lazy load complete");
                                                }

                                                @Override
                                                public void onDatabaseNotExist() {

                                                }
                                            }, selectedDataNode);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private void setup(final DataNode rootNode) {
        rootNode.setAsParent(0, mSpinnerElements.size());

        setupAutocomplete(rootNode);

        int index = 0;

        for (SpinnerElement element : mSpinnerElements) {

            final Spinner spinner = new Spinner(getContext());
            final ArrayList<DataNode> dataset = new ArrayList<>();
            int position = 0;
            if (index == 0) {
                dataset.addAll(rootNode.children);
                if (element.valueToBeSelected != null) {
                    position = DataNode.getPosition(element.valueToBeSelected, rootNode.children);
                    element.valueToBeSelected = null;
                }
            }
            ArrayAdapter<DataNode> adapter = new ArrayAdapter<>(getContext(), element.resourceId,
                    element.textViewId, dataset);
            spinner.setAdapter(adapter);
            addOnItemSelectedListener(spinner);
            spinner.setSelection(position);
            spinner.setTag(index);
            spinner.setLayoutParams(element.layoutParams);
            View view = new View(getContext());
            view.setLayoutParams(new ViewGroup.LayoutParams
                    (PixelUtils.dpToPx(getContext(), element.separatorWidthDp),
                            PixelUtils.dpToPx(getContext(), element.separatorHeightDp)));
            addView(spinner);
            addView(view);
            ViewInfo viewInfo = new ViewInfo(dataset, adapter, spinner);
            viewInfoArrayList.add(viewInfo);
            if (index > 0 && element.valueToBeSelected != null) {
                viewInfo.itemToBeSelected = new DataNode(element.valueToBeSelected);
            }
            index++;
        }

        if (mDynamicSpinnerViewListener != null)
            mDynamicSpinnerViewListener.onLoadComplete();
    }

    private void loadChildSpinners(ArrayList<DataNode> dataNodes, int pos) {
        ViewInfo nextViewInfo = viewInfoArrayList.get(pos + 1);
        nextViewInfo.dataset.clear();
        nextViewInfo.dataset.addAll(dataNodes);
        nextViewInfo.spinner.setAdapter(null);
        nextViewInfo.spinner.setAdapter(nextViewInfo.adapter);
        if (nextViewInfo.itemToBeSelected != null) {
            int positionOfNodeToBeSelected = DataNode.getPosition(nextViewInfo.itemToBeSelected, nextViewInfo.dataset);
            if (positionOfNodeToBeSelected != -1) {
                nextViewInfo.spinner.setSelection(positionOfNodeToBeSelected);
            } else {
                nextViewInfo.spinner.setSelection(0);
            }
            nextViewInfo.itemToBeSelected = null;
        } else {
            nextViewInfo.spinner.setSelection(0);
        }
    }

    /*
            Returns the information associated with the DynamicSpinnerView in a JSON
            format.
            Example of the data returned:
            {"State":"STATE 1","District":"District 1","Block":"Block 3","Village":"Village 1","School":"School 4"}
     */

    public String getInfo() {
        JSONObject jsonObject = new JSONObject();
        try {
            if (mSpinnerElements != null && viewInfoArrayList != null
                    && viewInfoArrayList.size() == mSpinnerElements.size()) {
                for (int index = 0; index < viewInfoArrayList.size(); index++) {
                    jsonObject.put(mSpinnerElements.get(index).type, viewInfoArrayList.get(index).getSelectedInfo());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return jsonObject.toString();
    }


    /**
     * Listener to relay information about the data loading process.
     */

    public interface DynamicSpinnerViewListener {

        /**
         * Called when the process to load
         * the data into the dynamic spinner view
         * has been started
         */

        void onLoadStart();

        /**
         * Called when the process to load the data
         * into the dynamic spinner view has been completed.
         */

        void onLoadComplete();

        /**
         * Called if a data load has been requested but the process to
         * save the information from the file in the assets folder into
         * an SQLite Database has not been completed yet.
         * Inside this method body you can register a {@link android.content.BroadcastReceiver}
         * using {@link androidx.localbroadcastmanager.content.LocalBroadcastManager} to listen
         * for the broadcasts
         * 1) {@link DynamicSpinnerView#SETUP_COMPLETE} called when setup process is complete
         * 2) {@link DynamicSpinnerView#SETUP_FAIL} called when setup process has failed due to any reason
         * 3) {@link DynamicSpinnerView#SETUP_START} sent when setup process is about to start.
         */

        void onDatabaseNotExist();


        /**
         * Called whenever the data load process fails for any reason.
         *
         * @param ex Exception instance describing the error
         */
        void onLoadFailed(Exception ex);
    }

    /**
     * Listener which is used to relay updates about the data saving process.
     */

    public interface SetupListener {
        /**
         * Called when the setup has been completed. If the data has already been saved and the
         * {@link DynamicSpinnerView#setup(Context, String, SetupListener, int)} method is called
         * then this method is called right away and {@link SetupListener#onSetupProcessStart()}
         * is not called.
         */
        void onSetupComplete();

        /**
         * Called when the process to save the information from the file corresponding to the
         * file name in the method {@link DynamicSpinnerView#setup(Context, String, SetupListener, int)}
         * has been started.
         */

        void onSetupProcessStart();
    }
}

package se.leap.bitmaskclient.base.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import de.blinkt.openvpn.VpnProfile;
import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.base.utils.ViewHelper;
import se.leap.bitmaskclient.databinding.AllowedApplicationLayoutBinding;
import se.leap.bitmaskclient.databinding.FragmentTorRouteAppsSelectorBinding;
import se.leap.bitmaskclient.databinding.RowTorifiedHeaderItemBinding;

public class TorRouteAppsSelectorFragment extends Fragment {
    private FragmentTorRouteAppsSelectorBinding mBinding;
    private VpnProfile mProfile;
    private PackageAdapter2 mListAdapter;
    private Set<String> excludedApps;
    private Set<String> torRoutedApps;
    private final List<ApplicationInfo> appList = new ArrayList<>();
    private final List<Pair<Integer, ApplicationInfo>> filteredAppList = new ArrayList<>();
    private PackageManager mPm;
    private final int rowTypeHeader1 = 0;
    private final int rowTypeHeader2 = 1;
    private final int rowTypeAppItem = 2;

    //separation between selected and all
    //private int segmentIndex;

    public TorRouteAppsSelectorFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        excludedApps = PreferenceHelper.getExcludedApps(this.getContext());
        torRoutedApps = PreferenceHelper.getTorRoutedApps(this.getContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mBinding = FragmentTorRouteAppsSelectorBinding.inflate(inflater, container, false);
        mListAdapter = new PackageAdapter2();
        mBinding.rvTorifiedList.setAdapter(mListAdapter);
        mBinding.rvTorifiedList.setLayoutManager(new LinearLayoutManager(getActivity()));
        ViewHelper.setActionBarTitle(this, R.string.tor_routed_apps_fragment_title);
        //for filter
        mBinding.editTorAppSelector.addTextChangedListener(new TorAppSelectorTextWatcher());

        mPm = requireActivity().getPackageManager();
        //show loading progressbar
        mBinding.loadingContainer.setVisibility(View.VISIBLE);
        new Thread(() -> prepareList(requireActivity())).start();

        return mBinding.getRoot();
    }

    @Override
    public void onDestroy() {
        PreferenceHelper.setTorRoutedApps(this.getActivity().getApplicationContext(), torRoutedApps);
        super.onDestroy();
    }

    class TorAppSelectorTextWatcher implements TextWatcher {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {

        }

        @Override
        public void afterTextChanged(Editable s) {
            mListAdapter.getFilter().filter(s.toString());
        }
    }

    /**
     * On time preparing list of apps
     * @param activity
     */
    private void prepareList(Activity activity) {
        List<ApplicationInfo> installedPackages = mPm.getInstalledApplications(PackageManager.GET_META_DATA);

        // Remove apps not using Internet
        int androidSystemUid = 0;
        ApplicationInfo system = null;

        try {
            system = mPm.getApplicationInfo("android", PackageManager.GET_META_DATA);
            androidSystemUid = system.uid;
            appList.add(system);
        } catch (PackageManager.NameNotFoundException e) {
        }

        for (ApplicationInfo app : installedPackages) {
            if (mPm.checkPermission(Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED &&
                    app.uid != androidSystemUid) {

                appList.add(app);
            }
        }

        Collections.sort(appList, new ApplicationInfo.DisplayNameComparator(mPm));

        activity.runOnUiThread(() -> {
            //this triggers filter with all results
            mListAdapter.getFilter().filter("");
            mBinding.loadingContainer.setVisibility(View.GONE);
        });
    }


    /*
     *
     * Adapter + 3 Holders.
     *
     * Adapter implemented Filterable to use old code :)
     *
     */
    class PackageAdapter2 extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements Filterable {
        private final LayoutInflater layoutInflater = LayoutInflater.from(getActivity());

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == rowTypeHeader1) {
                RowTorifiedHeaderItemBinding binding = RowTorifiedHeaderItemBinding.inflate(layoutInflater, parent, false);
                return new Header1Holder(binding);
            } else if (viewType == rowTypeHeader2) {
                RowTorifiedHeaderItemBinding binding = RowTorifiedHeaderItemBinding.inflate(layoutInflater, parent, false);
                return new Header2Holder(binding);
            } else {
                AllowedApplicationLayoutBinding binding = AllowedApplicationLayoutBinding.inflate(layoutInflater, parent, false);
                return new PackageHolder(binding);
            }

        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == rowTypeAppItem) {
                ((PackageHolder) holder).bindView(filteredAppList.get(position).second);
            }
        }

        @Override
        public int getItemCount() {
            return filteredAppList.size();
        }


        @Override
        public int getItemViewType(int position) {
            return filteredAppList.get(position).first;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {

                    //If there are 4 torified apps then they are placed as :
                    // header1, 1,2,3,4, header2, ....
                    // so 5th place is header2

                    String filterString = constraint.toString().toLowerCase(Locale.getDefault());
                    FilterResults results = new FilterResults();

                    filteredAppList.clear();
                    List<Pair<Integer, ApplicationInfo>> torTempList = new ArrayList<>();
                    List<Pair<Integer, ApplicationInfo>> excludedTempList = new ArrayList<>();
                    int segmentIndex = 1;


                    for (int i = 0; i < appList.size(); i++) {
                        //note: excluded app check must come first as user may have added torified app to exclusion
                        // so we dont want to show those apps as still torified
                        ApplicationInfo pInfo = appList.get(i);
                        CharSequence appName = pInfo.loadLabel(mPm);

                        if (TextUtils.isEmpty(filterString) || appName.toString().toLowerCase(Locale.getDefault()).contains(filterString)) {
                            if (excludedApps.contains(appList.get(i).packageName)) {
                                excludedTempList.add(new Pair<>(rowTypeAppItem, appList.get(i)));

                            } else if (torRoutedApps.contains(appList.get(i).packageName)) {
                                torTempList.add(new Pair<>(rowTypeAppItem, appList.get(i)));
                                segmentIndex++;

                            } else {
                                filteredAppList.add(new Pair<>(rowTypeAppItem, appList.get(i)));
                            }
                        }
                    }

                    //at this point filteredAppList already has apps that are not torified. So we add torfied app from top
                    filteredAppList.addAll(0, torTempList);
                    //add excluded at the bottom
                    filteredAppList.addAll(filteredAppList.size(), excludedTempList);
                    //add headers at their respected position
                    filteredAppList.add(0, new Pair<>(rowTypeHeader1, null));
                    filteredAppList.add(segmentIndex, new Pair<>(rowTypeHeader2, null));

                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    //not using result but notfying adapter here so its thread safe.
                    mListAdapter.notifyDataSetChanged();
                }
            };
        }

        public void moveItem(int fromPosition, int toPosition) {
            filteredAppList.add(toPosition, filteredAppList.remove(fromPosition));
            notifyItemMoved(fromPosition, toPosition);
        }
    }

    class PackageHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final AllowedApplicationLayoutBinding rowBinding;

        public PackageHolder(@NonNull AllowedApplicationLayoutBinding binding) {
            super(binding.getRoot());
            this.rowBinding = binding;
        }

        public void bindView(ApplicationInfo data) {
            CharSequence appName = data.loadLabel(mPm);
            if (TextUtils.isEmpty(appName))
                appName = data.packageName;
            rowBinding.appName.setText(appName);
            rowBinding.appIcon.setImageDrawable(data.loadIcon(mPm));
            rowBinding.appSelected.setTag(data.packageName);
            rowBinding.appSelected.setChecked(torRoutedApps.contains(data.packageName));
            rowBinding.appSelected.setEnabled(!excludedApps.contains(data.packageName));
            rowBinding.appName.setEnabled(!excludedApps.contains(data.packageName));
            if (!excludedApps.contains(data.packageName)) {
                rowBinding.appSelected.setOnClickListener(this);
            } else {
                rowBinding.appSelected.setOnClickListener(null);
            }
        }

        @Override
        public void onClick(View v) {
            //The checkbox works manually, so when clicked first we toggle the state, then poll the state.
            rowBinding.appSelected.toggle();
            String packageName = (String) v.getTag();
            int header2Pos = 1;//will never be 0
            for (int i = 0; i < filteredAppList.size(); i++) {
                if (filteredAppList.get(i).first == rowTypeHeader2) {
                    header2Pos = i;
                    break;
                }
            }
            //This is little more than what meets to the eye...
            mListAdapter.moveItem(getAdapterPosition(), header2Pos);
            // in both cases (adding to torified apps and removing) we use same moveItem parameters.
            // Shouldn't it be header2Pos in case of adding and header2Pos+1 in case of removing? because when removed we want to place removed item below header2.
            //but that is not the case :), we actually don't need to do that.

            if (rowBinding.appSelected.isChecked()) {
                Log.d("openvpn", "adding to allowed apps" + packageName);
                torRoutedApps.add(packageName);

            } else {
                Log.d("openvpn", "removing from allowed apps" + packageName);
                torRoutedApps.remove(packageName);
            }
        }
    }

    class Header1Holder extends RecyclerView.ViewHolder {

        public Header1Holder(@NonNull RowTorifiedHeaderItemBinding binding) {
            super(binding.getRoot());
            binding.headerTitle.setText(R.string.tor_routed_list_header1);
        }
    }

    class Header2Holder extends RecyclerView.ViewHolder {

        public Header2Holder(@NonNull RowTorifiedHeaderItemBinding binding) {
            super(binding.getRoot());
            binding.headerTitle.setText(R.string.tor_routed_list_header2);

            String part1 = getString(R.string.tor_routed_list_header2_subtitle_part1, excludedApps.size());
            Spannable part2 = new SpannableString(getString(R.string.tor_routed_list_header2_subtitle_part2));
            part2.setSpan(new ForegroundColorSpan(ContextCompat.getColor(getContext(), R.color.tor_route_info_highlight)), 0, part2.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(part1);
            builder.append(" ");
            builder.append(part2);

            binding.headerSubtitle.setText(builder);
        }
    }

}
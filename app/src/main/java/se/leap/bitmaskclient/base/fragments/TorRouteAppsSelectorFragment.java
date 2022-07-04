package se.leap.bitmaskclient.base.fragments;

import android.Manifest;
import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

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
    private List<ApplicationInfo> organisedAppList = new ArrayList<>();
    private List<Pair<Integer, ApplicationInfo>> filteredAppList = new ArrayList<>();
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

        mBinding.editTorAppSelector.addTextChangedListener(new TorAppSelectorTextWatcher());

        mPm = requireActivity().getPackageManager();

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
            if (!TextUtils.isEmpty(s)) {
                mListAdapter.getFilter().filter(s.toString());
            }
        }
    }

    private void prepareList(Activity activity) {
        List<ApplicationInfo> apps = new ArrayList<>();

        List<ApplicationInfo> installedPackages = mPm.getInstalledApplications(PackageManager.GET_META_DATA);

        // Remove apps not using Internet
        int androidSystemUid = 0;
        ApplicationInfo system = null;

        try {
            system = mPm.getApplicationInfo("android", PackageManager.GET_META_DATA);
            androidSystemUid = system.uid;
            apps.add(system);
        } catch (PackageManager.NameNotFoundException e) {
        }

        for (ApplicationInfo app : installedPackages) {
            if (mPm.checkPermission(Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED &&
                    app.uid != androidSystemUid) {

                apps.add(app);
            }
        }

        Collections.sort(apps, new ApplicationInfo.DisplayNameComparator(mPm));

        List<ApplicationInfo> torTempList = new ArrayList<>();
        List<ApplicationInfo> excludedTempList = new ArrayList<>();

        for (int i = 0; i < apps.size(); i++) {
            //note: excluded app check must come first as user may have added torified app to exclusion
            // so we dont want to show those apps as still torified
            if (excludedApps.contains(apps.get(i).packageName)) {
                excludedTempList.add(apps.get(i));

            } else if (torRoutedApps.contains(apps.get(i).packageName)) {
                torTempList.add(apps.get(i));

            } else {
                organisedAppList.add(apps.get(i));
            }
        }

        organisedAppList.addAll(0, torTempList);
        organisedAppList.addAll(organisedAppList.size(), excludedTempList);

        calculateSegment(organisedAppList);
        activity.runOnUiThread(() -> {
            mListAdapter.notifyDataSetChanged();
            mBinding.loadingContainer.setVisibility(View.GONE);
        });
    }

    /**
     * This adds place holders in location of headers
     *
     * @param list organised list(can be filtered but must be organised)
     */
    private void calculateSegment(List<ApplicationInfo> list) {
        //If there are 4 torified apps then they are placed as :
        // header1, 1,2,3,4, header2, ....
        // so 5th place is header2
        filteredAppList.clear();
        filteredAppList.add(0, new Pair<>(rowTypeHeader1, null));
        int segmentIndex = 1;
        for (int i = 0; i < list.size(); i++) {
            filteredAppList.add(new Pair<>(rowTypeAppItem, list.get(i)));
            if (torRoutedApps.contains(list.get(i).packageName)) {
                segmentIndex++;
            }
        }
        filteredAppList.add(segmentIndex, new Pair<>(rowTypeHeader2, null));
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
                    String filterString = constraint.toString().toLowerCase(Locale.getDefault());

                    FilterResults results = new FilterResults();


                    int count = organisedAppList.size();
                    final Vector<ApplicationInfo> nlist = new Vector<>(count);

                    for (int i = 0; i < count; i++) {
                        ApplicationInfo pInfo = organisedAppList.get(i);
                        CharSequence appName = pInfo.loadLabel(mPm);

                        if (TextUtils.isEmpty(appName))
                            appName = pInfo.packageName;

                        if (appName instanceof String) {
                            if (((String) appName).toLowerCase(Locale.getDefault()).contains(filterString))
                                nlist.add(pInfo);
                        } else {
                            if (appName.toString().toLowerCase(Locale.getDefault()).contains(filterString))
                                nlist.add(pInfo);
                        }
                    }
                    results.values = nlist;
                    results.count = nlist.size();

                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    List<ApplicationInfo> list = (List<ApplicationInfo>) results.values;
                    calculateSegment(list);
                    mListAdapter.notifyDataSetChanged();
                }
            };
        }

        public void swapItem(int fromPosition, int toPosition) {
            Collections.swap(filteredAppList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
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
            rowBinding.appSelected.setOnClickListener(this);
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
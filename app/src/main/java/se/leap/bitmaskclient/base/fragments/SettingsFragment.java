package se.leap.bitmaskclient.base.fragments;

import static android.content.Context.MODE_PRIVATE;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.leap.bitmaskclient.R.string.advanced_settings;
import static se.leap.bitmaskclient.base.models.Constants.GATEWAY_PINNING;
import static se.leap.bitmaskclient.base.models.Constants.PREFER_UDP;
import static se.leap.bitmaskclient.base.models.Constants.SHARED_PREFERENCES;
import static se.leap.bitmaskclient.base.models.Constants.USE_BRIDGES;
import static se.leap.bitmaskclient.base.models.Constants.USE_IPv6_FIREWALL;
import static se.leap.bitmaskclient.base.models.Constants.USE_OBFUSCATION_PINNING;
import static se.leap.bitmaskclient.base.utils.ConfigHelper.ObfsVpnHelper.useObfsVpn;
import static se.leap.bitmaskclient.base.utils.ConfigHelper.isCalyxOSWithTetheringSupport;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.allowExperimentalTransports;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getPreferUDP;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getShowAlwaysOnDialog;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getUseBridges;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getUseSnowflake;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.hasSnowflakePrefs;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.preferUDP;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.setAllowExperimentalTransports;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.setUseObfuscationPinning;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.useBridges;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.useObfuscationPinning;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.useSnowflake;
import static se.leap.bitmaskclient.base.utils.ViewHelper.setActionBarSubtitle;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.Set;

import de.blinkt.openvpn.core.VpnStatus;
import se.leap.bitmaskclient.BuildConfig;
import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.FragmentManagerEnhanced;
import se.leap.bitmaskclient.base.MainActivity;
import se.leap.bitmaskclient.base.models.ProviderObservable;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.base.views.IconSwitchEntry;
import se.leap.bitmaskclient.base.views.IconTextEntry;
import se.leap.bitmaskclient.eip.EipCommand;
import se.leap.bitmaskclient.firewall.FirewallManager;

public class SettingsFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private FirewallManager firewallManager;
    private SharedPreferences preferences;

    private IconTextEntry tethering;
    private IconSwitchEntry firewall;
    IconSwitchEntry useUdpEntry;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getContext().getSharedPreferences(SHARED_PREFERENCES, MODE_PRIVATE);
        preferences.registerOnSharedPreferenceChangeListener(this);
        firewallManager = new FirewallManager(getContext().getApplicationContext(), false);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.f_settings, container, false);
        initAlwaysOnVpnEntry(view);
        initExcludeAppsEntry(view);
        initPreferUDPEntry(view);
        initUseBridgesEntry(view);
        initUseSnowflakeEntry(view);
        initFirewallEntry(view);
        initTetheringEntry(view);
        initGatewayPinningEntry(view);
        initExperimentalTransportsEntry(view);
        initObfuscationPinningEntry(view);
        setActionBarSubtitle(this, advanced_settings);
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        preferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    private void initUseBridgesEntry(View rootView) {
        IconSwitchEntry useBridges = rootView.findViewById(R.id.bridges_switch);
        if (ProviderObservable.getInstance().getCurrentProvider().supportsPluggableTransports()) {
            useBridges.setVisibility(VISIBLE);
            useBridges.setChecked(getUseBridges(getContext()));
            useBridges.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!buttonView.isPressed()) {
                    return;
                }
                useBridges(getContext(), isChecked);
                if (VpnStatus.isVPNActive()) {
                    EipCommand.startVPN(getContext(), false);
                    Toast.makeText(getContext(), R.string.reconnecting, Toast.LENGTH_LONG).show();
                }
            });
            //We check the UI state of the useUdpEntry here as well, in order to avoid a situation
            //where both entries are disabled, because both preferences are enabled.
            //bridges can be enabled not only from here but also from error handling
            boolean useUDP = getPreferUDP(getContext()) && useUdpEntry.isEnabled();
            useBridges.setEnabled(!useUDP);
            useBridges.setSubtitle(getString(useUDP ? R.string.disabled_while_udp_on : R.string.nav_drawer_subtitle_obfuscated_connection));
        } else {
            useBridges.setVisibility(GONE);
        }
    }

    private void initUseSnowflakeEntry(View rootView) {
        IconSwitchEntry useSnowflake = rootView.findViewById(R.id.snowflake_switch);
        useSnowflake.setVisibility(VISIBLE);
        useSnowflake.setChecked(hasSnowflakePrefs(getContext()) && getUseSnowflake(getContext()));
        useSnowflake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            useSnowflake(getContext(), isChecked);
        });
    }

    private void initAlwaysOnVpnEntry(View rootView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            IconTextEntry alwaysOnVpn = rootView.findViewById(R.id.always_on_vpn);
            alwaysOnVpn.setVisibility(VISIBLE);
            alwaysOnVpn.setOnClickListener((buttonView) -> {
                if (getShowAlwaysOnDialog(getContext())) {
                    showAlwaysOnDialog();
                } else {
                    Intent intent = new Intent("android.net.vpn.SETTINGS");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
        }
    }

    private void initPreferUDPEntry(View rootView) {
        useUdpEntry = rootView.findViewById(R.id.prefer_udp);
        useUdpEntry.setVisibility(VISIBLE);
        useUdpEntry.setChecked(getPreferUDP(getContext()));
        useUdpEntry.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            preferUDP(getContext(), isChecked);
            if (VpnStatus.isVPNActive()) {
                EipCommand.startVPN(getContext(), false);
                Toast.makeText(getContext(), R.string.reconnecting, Toast.LENGTH_LONG).show();
            }
        });
        boolean bridgesEnabled = getUseBridges(getContext());
        useUdpEntry.setEnabled(!bridgesEnabled);
        useUdpEntry.setSubtitle(getString(bridgesEnabled ? R.string.disabled_while_bridges_on : R.string.prefer_udp_subtitle));
    }

    private void initExcludeAppsEntry(View rootView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            IconTextEntry excludeApps = rootView.findViewById(R.id.exclude_apps);
            excludeApps.setVisibility(VISIBLE);
            Set<String> apps = PreferenceHelper.getExcludedApps(this.getContext());
            if (apps != null) {
                updateExcludeAppsSubtitle(excludeApps, apps.size());
            }
            FragmentManagerEnhanced fragmentManager = new FragmentManagerEnhanced(getActivity().getSupportFragmentManager());
            excludeApps.setOnClickListener((buttonView) -> {
                Fragment fragment = new ExcludeAppsFragment();
                fragmentManager.replace(R.id.main_container, fragment, MainActivity.TAG);
            });
        }
    }

    private void updateExcludeAppsSubtitle(IconTextEntry excludeApps, int number) {
        if (number > 0) {
            excludeApps.setSubtitle(getContext().getResources().getQuantityString(R.plurals.subtitle_exclude_apps, number, number));
            excludeApps.setSubtitleColor(R.color.colorError);
        } else {
            excludeApps.hideSubtitle();
        }
    }

    private void initFirewallEntry(View rootView) {
        firewall = rootView.findViewById(R.id.enableIPv6Firewall);
        firewall.setChecked(PreferenceHelper.useIpv6Firewall(getContext()));
        firewall.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) {
                return;
            }
            PreferenceHelper.setUseIPv6Firewall(getContext(), isChecked);
            if (VpnStatus.isVPNActive()) {
                if (isChecked) {
                    firewallManager.startIPv6Firewall();
                } else {
                    firewallManager.stopIPv6Firewall();
                }
            }
        });
    }

    private void initTetheringEntry(View rootView) {
        tethering = rootView.findViewById(R.id.tethering);

        if (isCalyxOSWithTetheringSupport(this.getContext())) {
            tethering.setVisibility(GONE);
            return;
        }

        tethering.setOnClickListener((buttonView) -> {
            showTetheringAlert();
        });
    }

    private void initGatewayPinningEntry(View rootView) {
        if (!BuildConfig.BUILD_TYPE.equals("debug")) {
            return;
        }
        Context context = this.getContext();
        if (context == null) {
            return;
        }
        IconTextEntry gatewayPinning = rootView.findViewById(R.id.gateway_pinning);
        String pinnedGateway = PreferenceHelper.getPinnedGateway(rootView.getContext());
        gatewayPinning.setSubtitle(pinnedGateway != null ? pinnedGateway : "Connect to a specific Gateway for debugging purposes");

        gatewayPinning.setOnClickListener(v -> {
            EditText gatewayPinningEditText = new EditText(rootView.getContext());
            gatewayPinningEditText.setText(pinnedGateway);
                new AlertDialog.Builder(context)
                    .setTitle("Gateway Pinning")
                    .setMessage("Enter the domain name of the gateway")
                    .setView(gatewayPinningEditText)
                    .setPositiveButton(android.R.string.ok, (dialogInterface, i) -> {
                        if (gatewayPinningEditText.getText() != null) {
                            String editTextInput = gatewayPinningEditText.getText().toString();
                            if (!TextUtils.isEmpty(editTextInput)) {
                                PreferenceHelper.setPreferredCity(context, null);
                                PreferenceHelper.pinGateway(context, editTextInput);
                            } else {
                                PreferenceHelper.pinGateway(context, null);
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create().show();
        });
    }

    public void initObfuscationPinningEntry(View rootView) {
        IconSwitchEntry obfuscationPinning = rootView.findViewById(R.id.obfuscation_proxy_pinning);
        if (useObfsVpn()) {
            obfuscationPinning.setVisibility(VISIBLE);
            boolean useBridges = getUseBridges(getContext());
            obfuscationPinning.setEnabled(useBridges);
            obfuscationPinning.setSubtitle(useBridges ? "Connect to a specific obfuscation proxy for debugging purposes" : "Enable Bridges to use this option");
            obfuscationPinning.setChecked(useObfuscationPinning(getContext()));
            obfuscationPinning.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!buttonView.isPressed()) {
                    return;
                }
                if (!isChecked) {
                    setUseObfuscationPinning(getContext(), false);
                } else {
                    showObfuscationPinningDialog();
                }
            });
            obfuscationPinning.setOnClickListener(v -> {
                if (obfuscationPinning.isChecked()) {
                    showObfuscationPinningDialog();
                }
            });
        } else {
            obfuscationPinning.setVisibility(GONE);
        }
    }

    public void showObfuscationPinningDialog() {
        try {
            FragmentTransaction fragmentTransaction = new FragmentManagerEnhanced(
                    getActivity().getSupportFragmentManager()).removePreviousFragment(
                    ObfuscationProxyDialog.TAG);
            DialogFragment newFragment = new ObfuscationProxyDialog();
            newFragment.setCancelable(false);
            newFragment.show(fragmentTransaction, ObfuscationProxyDialog.TAG);
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void initExperimentalTransportsEntry(View rootView) {
        IconSwitchEntry experimentalTransports = rootView.findViewById(R.id.experimental_transports);
        if (useObfsVpn() && ProviderObservable.getInstance().getCurrentProvider().supportsExperimentalPluggableTransports()) {
            experimentalTransports.setVisibility(VISIBLE);
            experimentalTransports.setChecked(allowExperimentalTransports(this.getContext()));
            experimentalTransports.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (!buttonView.isPressed()) {
                    return;
                }
                setAllowExperimentalTransports(getContext(), isChecked);
            });
        } else {
            experimentalTransports.setVisibility(GONE);
        }

    }

    public void showTetheringAlert() {
        try {

            FragmentTransaction fragmentTransaction = new FragmentManagerEnhanced(
                    getActivity().getSupportFragmentManager()).removePreviousFragment(
                    TetheringDialog.TAG);
            DialogFragment newFragment = new TetheringDialog();
            newFragment.show(fragmentTransaction, TetheringDialog.TAG);
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    public void showAlwaysOnDialog() {
        try {

            FragmentTransaction fragmentTransaction = new FragmentManagerEnhanced(
                    getActivity().getSupportFragmentManager()).removePreviousFragment(
                    AlwaysOnDialog.TAG);
            DialogFragment newFragment = new AlwaysOnDialog();
            newFragment.show(fragmentTransaction, AlwaysOnDialog.TAG);
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        View rootView = getView();
        if (rootView == null)  {
            return;
        }
        if (key.equals(USE_BRIDGES) || key.equals(PREFER_UDP)) {
            initUseBridgesEntry(rootView);
            initPreferUDPEntry(rootView);
        } else if (key.equals(USE_IPv6_FIREWALL)) {
            initFirewallEntry(getView());
        } else if (key.equals(GATEWAY_PINNING)) {
            initGatewayPinningEntry(rootView);
        }

        if (key.equals(USE_OBFUSCATION_PINNING) || key.equals(USE_BRIDGES)) {
            initObfuscationPinningEntry(rootView);
        }
    }

}

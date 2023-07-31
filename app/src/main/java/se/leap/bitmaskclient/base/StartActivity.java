/**
 * Copyright (c) 2018 LEAP Encryption Access Project and contributers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package se.leap.bitmaskclient.base;

import static se.leap.bitmaskclient.base.MainActivity.ACTION_SHOW_MOTD_FRAGMENT;
import static se.leap.bitmaskclient.base.MainActivity.ACTION_SHOW_VPN_FRAGMENT;
import static se.leap.bitmaskclient.base.models.Constants.APP_ACTION_CONFIGURE_ALWAYS_ON_PROFILE;
import static se.leap.bitmaskclient.base.models.Constants.EIP_RESTART_ON_BOOT;
import static se.leap.bitmaskclient.base.models.Constants.EXTRA_MOTD_MSG;
import static se.leap.bitmaskclient.base.models.Constants.PREFERENCES_APP_VERSION;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_EIP_DEFINITION;
import static se.leap.bitmaskclient.base.models.Constants.PROVIDER_KEY;
import static se.leap.bitmaskclient.base.models.Constants.REQUEST_CODE_CONFIGURE_LEAP;
import static se.leap.bitmaskclient.base.utils.ConfigHelper.isDefaultBitmask;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.storeProviderInPreferences;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Set;

import de.blinkt.openvpn.core.VpnStatus;
import motd.IMessage;
import motd.IMessages;
import motd.IStringCollection;
import motd.Motd;
import se.leap.bitmaskclient.BuildConfig;
import se.leap.bitmaskclient.base.models.FeatureVersionCode;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.base.models.ProviderObservable;
import se.leap.bitmaskclient.base.utils.DateHelper;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.eip.EipCommand;
import se.leap.bitmaskclient.providersetup.activities.CustomProviderSetupActivity;
import se.leap.bitmaskclient.providersetup.activities.SetupActivity;

/**
 * Activity shown at startup. Evaluates if App is started for the first time or has been upgraded
 * and acts and calls another activity accordingly.
 *
 */
public class StartActivity extends Activity{
    public static final String TAG = StartActivity.class.getSimpleName();

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({FIRST, NORMAL, UPGRADE})
    private @interface StartupMode {}
    private static final int FIRST = 0;
    private static final int NORMAL = 1;
    private static final int UPGRADE = 2;

    private int versionCode;
    private int previousVersionCode;

    private SharedPreferences preferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = PreferenceHelper.getSharedPreferences(this);

        Log.d(TAG, "Started");

        switch (checkAppStart()) {
            case NORMAL:
                break;

            case FIRST:
                storeAppVersion();
                // TODO start ProfileCreation & replace below code
                // (new Intent(getActivity(), ProviderListActivity.class), Constants.REQUEST_CODE_SWITCH_PROVIDER);
                break;

            case UPGRADE:
                executeUpgrade();
                break;
        }

        // initialize app necessities
        VpnStatus.initLogCache(getApplicationContext().getCacheDir());

        sanitizeStartIntent();
        prepareEIP();

    }

    private void sanitizeStartIntent() {
        Intent intent = new Intent();
        try {
            if (getIntent().hasExtra(EIP_RESTART_ON_BOOT)) {
                intent.putExtra(EIP_RESTART_ON_BOOT, getIntent().getBooleanExtra(EIP_RESTART_ON_BOOT, false));
            }
            if (getIntent().hasExtra(APP_ACTION_CONFIGURE_ALWAYS_ON_PROFILE)) {
                intent.putExtra(APP_ACTION_CONFIGURE_ALWAYS_ON_PROFILE, false);
            }
        } catch (RuntimeException e) {

        }
        this.setIntent(intent);
    }


    /**
     *  check if normal start, first run, up or downgrade
     *  @return @StartupMode
     */
    @StartupMode
    private int checkAppStart() {
        try {
            versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            previousVersionCode = preferences.getInt(PREFERENCES_APP_VERSION, -1);

            // versions do match -> normal start
            if (versionCode == previousVersionCode) {
                Log.d(TAG, "App start was: NORMAL START");
                return NORMAL;
            }

            // no previous app version -> first start
            if (previousVersionCode == -1 ) {
                Log.d(TAG, "FIRST START");
                return FIRST;
            }

            // version has increased -> upgrade
            if (versionCode > previousVersionCode) {
                Log.d(TAG, "UPGRADE");
                return UPGRADE;
            }

        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "Splash screen didn't find any " + getPackageName() + " package");
        }

        return NORMAL;
    }

    /**
     * execute necessary upgrades for version change
     */
    private void executeUpgrade() {
        if (hasNewFeature(FeatureVersionCode.RENAMED_EIP_IN_PREFERENCES)) {
            String eipJson = preferences.getString(PROVIDER_KEY, null);
            if (eipJson != null) {
                preferences.edit().putString(PROVIDER_EIP_DEFINITION, eipJson).
                        remove(PROVIDER_KEY).apply();
            }
        }

        if (hasNewFeature(FeatureVersionCode.GEOIP_SERVICE)) {
            // deletion of current configured provider so that the geoip url will picked out
            // from the preseeded *.url file / geoipUrl buildconfigfield (build.gradle) during
            // next setup
            Provider provider = ProviderObservable.getInstance().getCurrentProvider();
            if (provider != null && !provider.isDefault()) {
                PreferenceHelper.deleteProviderDetailsFromPreferences(preferences, provider.getDomain());
                ProviderObservable.getInstance().updateProvider(new Provider());
            }
        }

        if ((hasNewFeature(FeatureVersionCode.CALYX_PROVIDER_LILYPAD_UPDATE) && (
                getPackageName().equals("org.calyxinstitute.vpn") ||
                        ProviderObservable.getInstance().getCurrentProvider().getDomain().equals("calyx.net"))) ||
            hasNewFeature(FeatureVersionCode.RISEUP_PROVIDER_LILYPAD_UPDATE) && (
                    getPackageName().equals("se.leap.riseupvpn") ||
                            ProviderObservable.getInstance().getCurrentProvider().getDomain().equals("riseup.net"))) {
            // deletion of current configured provider so that a new provider setup is triggered
            Provider provider = ProviderObservable.getInstance().getCurrentProvider();
            if (provider != null && !provider.isDefault()) {
                PreferenceHelper.deleteProviderDetailsFromPreferences(preferences, provider.getDomain());
                PreferenceHelper.deleteCurrentProviderDetailsFromPreferences(preferences);
                ProviderObservable.getInstance().updateProvider(new Provider());
            }
        }

        if (hasNewFeature(FeatureVersionCode.ENCRYPTED_SHARED_PREFS)) {
            PreferenceHelper.migrateToEncryptedPrefs(this);
            preferences = PreferenceHelper.getSharedPreferences(this);
        }

        // always check if manual gateway selection feature switch has been disabled
        if (!BuildConfig.allow_manual_gateway_selection && PreferenceHelper.getPreferredCity(this) != null) {
            PreferenceHelper.setPreferredCity(this, null);
        }

        // ensure all upgrades have passed before storing new information
        storeAppVersion();
    }

    /**
     * check if an upgrade passed or moved to given milestone
     * @param featureVersionCode Version code of the Milestone FeatureVersionCode.MILE_STONE
     * @return true if milestone is reached - false otherwise
     */
    private boolean hasNewFeature(int featureVersionCode) {
        return previousVersionCode < featureVersionCode && versionCode >= featureVersionCode;
    }

    private void storeAppVersion() {
        preferences.edit().putInt(PREFERENCES_APP_VERSION, versionCode).apply();
    }

    private void prepareEIP() {
        Provider provider =  ProviderObservable.getInstance().getCurrentProvider();
        if (provider.isConfigured()) {
            Log.d(TAG, "vpn provider is configured");
            if (getIntent() != null && getIntent().getBooleanExtra(EIP_RESTART_ON_BOOT, false)) {
                EipCommand.startVPN(this, true);
                finish();
            } else if (PreferenceHelper.getRestartOnUpdate(this.getApplicationContext())) {
                // This is relevant for web build flavor apks
                PreferenceHelper.restartOnUpdate(this.getApplicationContext(), false);
                EipCommand.startVPN(this, false);
                showNextActivity(provider);
                finish();
            } else {
                showNextActivity(provider);
            }
        } else {
            configureLeapProvider();
        }
    }

    private void configureLeapProvider() {
        if (getIntent().hasExtra(APP_ACTION_CONFIGURE_ALWAYS_ON_PROFILE)) {
            getIntent().removeExtra(APP_ACTION_CONFIGURE_ALWAYS_ON_PROFILE);
        }
        if (isDefaultBitmask()) {
            startActivityForResult(new Intent(this, SetupActivity.class), REQUEST_CODE_CONFIGURE_LEAP);
        } else { // custom branded app
            startActivityForResult(new Intent(this, CustomProviderSetupActivity.class), REQUEST_CODE_CONFIGURE_LEAP);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == REQUEST_CODE_CONFIGURE_LEAP) {
            if (resultCode == RESULT_OK && data != null && data.hasExtra(Provider.KEY)) {
                Provider provider = data.getParcelableExtra(Provider.KEY);
                storeProviderInPreferences(preferences, provider);
                ProviderObservable.getInstance().updateProvider(provider);
                EipCommand.startVPN(this, false);
                showNextActivity(provider);
            } else if (resultCode == RESULT_CANCELED) {
                finish();
            }
        }
    }

    private void showNextActivity(Provider provider) {
        if (provider.shouldShowMotdSeen()) {
            try {
                IMessages messages = Motd.newMessages(provider.getMotdJsonString());

                IStringCollection stringCollection =  Motd.newStringCollection(); //provider.getMotdLastSeenHashCollection();
                String formattedDate = DateHelper.getFormattedDateWithTimezone(provider.getLastMotdSeen());
                IMessage message = messages.getFirstMessage(formattedDate, stringCollection);
                if (message != null) {
                    IMessage imessage = Motd.newMessage(message.toJson());
                    showMotd(provider, imessage);
                    return;
                }
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Couldn't show Motd. Invalid timestamp.");
            }
        }
        showVPNFragment();
    }

    private void showMotd(Provider p, IMessage message) {
        if (message.mType().equals(Motd.MESSAGE_TYPE_ONCE)) {
            Set<String> lastSeenHashes = p.getMotdLastSeenHashes();
            String hash = message.hash();
            lastSeenHashes.add(hash);
            p.setMotdLastSeenHashes(lastSeenHashes);
            p.setLastMotdSeen(System.currentTimeMillis());
            PreferenceHelper.persistProviderAsync(this, p);
            ProviderObservable.getInstance().updateProvider(p);
        }
        showMotdFragment(message);
    }

    private void showVPNFragment() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(ACTION_SHOW_VPN_FRAGMENT);
        startActivity(intent);
        finish();
    }

    private void showMotdFragment(IMessage message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(ACTION_SHOW_MOTD_FRAGMENT);
        intent.putExtra(EXTRA_MOTD_MSG, message.toJson());
        startActivity(intent);
        finish();
    }
}

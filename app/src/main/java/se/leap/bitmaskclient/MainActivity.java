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
package se.leap.bitmaskclient;


import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Observable;
import java.util.Observer;

import se.leap.bitmaskclient.drawer.NavigationDrawerFragment;
import se.leap.bitmaskclient.eip.EIP;
import se.leap.bitmaskclient.eip.EipCommand;
import se.leap.bitmaskclient.fragments.ExcludeAppsFragment;
import se.leap.bitmaskclient.fragments.LogFragment;
import se.leap.bitmaskclient.utils.PreferenceHelper;

import static se.leap.bitmaskclient.Constants.ASK_TO_CANCEL_VPN;
import static se.leap.bitmaskclient.Constants.BROADCAST_RESULT_CODE;
import static se.leap.bitmaskclient.Constants.BROADCAST_RESULT_KEY;
import static se.leap.bitmaskclient.Constants.EIP_ACTION_PREPARE_VPN;
import static se.leap.bitmaskclient.Constants.EIP_ACTION_START;
import static se.leap.bitmaskclient.Constants.EIP_REQUEST;
import static se.leap.bitmaskclient.Constants.PROVIDER_KEY;
import static se.leap.bitmaskclient.Constants.REQUEST_CODE_CONFIGURE_LEAP;
import static se.leap.bitmaskclient.Constants.REQUEST_CODE_LOG_IN;
import static se.leap.bitmaskclient.Constants.REQUEST_CODE_SWITCH_PROVIDER;
import static se.leap.bitmaskclient.Constants.SHARED_PREFERENCES;
import static se.leap.bitmaskclient.ProviderAPI.ERRORID;
import static se.leap.bitmaskclient.ProviderAPI.ERRORS;
import static se.leap.bitmaskclient.ProviderAPI.INCORRECTLY_DOWNLOADED_EIP_SERVICE;
import static se.leap.bitmaskclient.ProviderAPI.INCORRECTLY_UPDATED_INVALID_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.ProviderAPI.USER_MESSAGE;
import static se.leap.bitmaskclient.R.string.downloading_vpn_certificate_failed;
import static se.leap.bitmaskclient.R.string.vpn_certificate_user_message;
import static se.leap.bitmaskclient.eip.EIP.EIPErrors.ERROR_INVALID_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.eip.EIP.EIPErrors.ERROR_VPN_PREPARE;
import static se.leap.bitmaskclient.utils.PreferenceHelper.storeProviderInPreferences;


public class MainActivity extends AppCompatActivity implements EipSetupListener, Observer, ExcludeAppsFragment.ExcludedAppsCallback {

    public final static String TAG = MainActivity.class.getSimpleName();

    private Provider provider;
    private SharedPreferences preferences;
    private NavigationDrawerFragment navigationDrawerFragment;

    public final static String ACTION_SHOW_VPN_FRAGMENT = "action_show_vpn_fragment";
    public final static String ACTION_SHOW_LOG_FRAGMENT = "action_show_log_fragment";

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.a_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        navigationDrawerFragment = (NavigationDrawerFragment)
                getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);

        preferences = getSharedPreferences(SHARED_PREFERENCES, MODE_PRIVATE);
        provider = ProviderObservable.getInstance().getCurrentProvider();

        EipSetupObserver.addListener(this);
        // Set up the drawer.
        navigationDrawerFragment.setUp(R.id.navigation_drawer, findViewById(R.id.drawer_layout));
        handleIntentAction(getIntent());
    }

    @Override
    public void onBackPressed() {
        FragmentManagerEnhanced fragmentManagerEnhanced = new FragmentManagerEnhanced(getSupportFragmentManager());
        Fragment fragment = fragmentManagerEnhanced.findFragmentByTag(MainActivity.TAG);
        if (fragment == null || !(fragment instanceof EipFragment)) {
            Fragment eipFragment = new EipFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelable(PROVIDER_KEY, provider);
            eipFragment.setArguments(bundle);
            fragmentManagerEnhanced.replace(R.id.main_container, eipFragment, MainActivity.TAG);
            hideActionBarSubTitle();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentAction(intent);
    }

    private void handleIntentAction(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Fragment fragment = null;
        switch (intent.getAction()) {
            case ACTION_SHOW_VPN_FRAGMENT:
                fragment = new EipFragment();
                Bundle bundle = new Bundle();
                if (intent.hasExtra(ASK_TO_CANCEL_VPN)) {
                    bundle.putBoolean(ASK_TO_CANCEL_VPN, true);
                }
                bundle.putParcelable(PROVIDER_KEY, provider);
                fragment.setArguments(bundle);
                hideActionBarSubTitle();
                break;
            case ACTION_SHOW_LOG_FRAGMENT:
                fragment = new LogFragment();
                setActionBarTitle(R.string.log_fragment_title);
                break;
            default:
                break;
        }
        // on layout change / recreation of the activity, we don't want create new Fragments
        // instead the fragments themselves care about recreation and state restoration
        intent.setAction(null);

        if (fragment != null) {
            new FragmentManagerEnhanced(getSupportFragmentManager())
                    .replace(R.id.main_container, fragment, MainActivity.TAG);
        }
    }

    private void hideActionBarSubTitle() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(null);
        }
    }
    private void setActionBarTitle(@StringRes int stringId) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(stringId);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null) {
            return;
        }

        if (resultCode == RESULT_OK && data.hasExtra(Provider.KEY)) {
            provider = data.getParcelableExtra(Provider.KEY);

            if (provider == null) {
                return;
            }

            storeProviderInPreferences(preferences, provider);
            ProviderObservable.getInstance().updateProvider(provider);
            if (!provider.supportsPluggableTransports()) {
                PreferenceHelper.usePluggableTransports(this, false);
            }
            navigationDrawerFragment.refresh();

            switch (requestCode) {
                case REQUEST_CODE_SWITCH_PROVIDER:
                    EipCommand.stopVPN(this.getApplicationContext());
                    break;
                case REQUEST_CODE_CONFIGURE_LEAP:
                    Log.d(TAG, "REQUEST_CODE_CONFIGURE_LEAP - onActivityResult - MainActivity");
                    break;
                case REQUEST_CODE_LOG_IN:
                    EipCommand.startVPN(this.getApplicationContext(), true);
                    break;
            }
        }

        // on switch provider we need to set the EIP fragment
        Fragment fragment = new EipFragment();
        Bundle arguments = new Bundle();
        arguments.putParcelable(PROVIDER_KEY, provider);
        fragment.setArguments(arguments);
        new FragmentManagerEnhanced(getSupportFragmentManager())
                .replace(R.id.main_container, fragment, MainActivity.TAG);
        hideActionBarSubTitle();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EipSetupObserver.removeListener(this);
    }

    @Override
    public void handleEipEvent(Intent intent) {
        int resultCode = intent.getIntExtra(BROADCAST_RESULT_CODE, RESULT_CANCELED);
        Bundle resultData = intent.getParcelableExtra(BROADCAST_RESULT_KEY);
        if (resultData == null) {
            resultData = Bundle.EMPTY;
        }
        String request = resultData.getString(EIP_REQUEST);

        if (request == null) {
            return;
        }

        switch (request) {
            case EIP_ACTION_START:
                if (resultCode == RESULT_CANCELED) {
                    String error = resultData.getString(ERRORS);
                    if (isInternalErrorHandling(error)) {
                        return;
                    }

                    if (LeapSRPSession.loggedIn() || provider.allowsAnonymous()) {
                        showMainActivityErrorDialog(error);
                    } else if (isInvalidCertificateForLoginOnlyProvider(error)) {
                        askUserToLogIn(getString(vpn_certificate_user_message));
                    }
                }
                break;
            case EIP_ACTION_PREPARE_VPN:
                if (resultCode == RESULT_CANCELED) {
                    showMainActivityErrorDialog(getString(R.string.vpn_error_establish), ERROR_VPN_PREPARE);
                }
                break;
        }
    }

    @Override
    public void handleProviderApiEvent(Intent intent) {
        int resultCode = intent.getIntExtra(BROADCAST_RESULT_CODE, RESULT_CANCELED);

        switch (resultCode) {
            case INCORRECTLY_DOWNLOADED_EIP_SERVICE:
                // TODO CATCH ME IF YOU CAN - WHAT DO WE WANT TO DO?
                break;
            case INCORRECTLY_UPDATED_INVALID_VPN_CERTIFICATE:
                if (LeapSRPSession.loggedIn() || provider.allowsAnonymous()) {
                    showMainActivityErrorDialog(getString(downloading_vpn_certificate_failed));
                } else {
                    askUserToLogIn(getString(vpn_certificate_user_message));
                }
                break;
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if (o instanceof ProviderObservable) {
            this.provider = ((ProviderObservable) o).getCurrentProvider();
        }
    }

    /**
     * Shows an error dialog
     */
    public void showMainActivityErrorDialog(String reasonToFail) {
        try {

            FragmentTransaction fragmentTransaction = new FragmentManagerEnhanced(
                    this.getSupportFragmentManager()).removePreviousFragment(
                            MainActivityErrorDialog.TAG);
            DialogFragment newFragment;
            try {
                JSONObject errorJson = new JSONObject(reasonToFail);
                newFragment = MainActivityErrorDialog.newInstance(provider, errorJson);
            } catch (JSONException e) {
                e.printStackTrace();
                newFragment = MainActivityErrorDialog.newInstance(provider, reasonToFail);
            }
            newFragment.show(fragmentTransaction, MainActivityErrorDialog.TAG);
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
            Log.w(TAG, "error dialog leaked!");
        }
    }

    /**
     * Shows an error dialog
     */
    public void showMainActivityErrorDialog(String reasonToFail, EIP.EIPErrors error) {
        try {
            FragmentTransaction fragmentTransaction = new FragmentManagerEnhanced(
                    this.getSupportFragmentManager()).removePreviousFragment(
                    MainActivityErrorDialog.TAG);
            DialogFragment newFragment = MainActivityErrorDialog.newInstance(provider, reasonToFail, error);
            newFragment.show(fragmentTransaction, MainActivityErrorDialog.TAG);
        } catch (IllegalStateException | NullPointerException e) {
            e.printStackTrace();
            Log.w(TAG, "error dialog leaked!");
        }
    }

    /**
     *
     * @param errorJsonString
     * @return true if errorJson is a valid json and contains only ERRORID but
     * not an ERRORS field containing an error message
     */
    public boolean isInternalErrorHandling(String errorJsonString) {
        try {
            JSONObject errorJson = new JSONObject(errorJsonString);
            return !errorJson.has(ERRORS) && errorJson.has(ERRORID);
        } catch (JSONException | NullPointerException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean isInvalidCertificateForLoginOnlyProvider(String errorJsonString) {
        try {
            JSONObject errorJson = new JSONObject(errorJsonString);
            return  ERROR_INVALID_VPN_CERTIFICATE.toString().equals(errorJson.getString(ERRORID)) &&
                    !LeapSRPSession.loggedIn() &&
                    !provider.allowsAnonymous();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void askUserToLogIn(String userMessage) {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.putExtra(PROVIDER_KEY, provider);
        if (userMessage != null) {
            intent.putExtra(USER_MESSAGE, userMessage);
        }
        startActivityForResult(intent, REQUEST_CODE_LOG_IN);
    }


    @Override
    public void onAppsExcluded(int number) {
        navigationDrawerFragment.onAppsExcluded(number);
    }
}

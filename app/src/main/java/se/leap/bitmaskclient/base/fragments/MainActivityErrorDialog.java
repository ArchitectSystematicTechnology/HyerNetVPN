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
package se.leap.bitmaskclient.base.fragments;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.MainActivity;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.eip.EIP;
import se.leap.bitmaskclient.eip.EipCommand;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.providersetup.ProviderAPICommand;

import static se.leap.bitmaskclient.base.MainActivity.ACTION_SHOW_DIALOG_FRAGMENT;
import static se.leap.bitmaskclient.base.MainActivity.ACTION_SHOW_VPN_FRAGMENT;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getPreferredCity;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.setPreferredCity;
import static se.leap.bitmaskclient.providersetup.ProviderAPI.UPDATE_INVALID_VPN_CERTIFICATE;
import static se.leap.bitmaskclient.R.string.warning_option_try_ovpn;
import static se.leap.bitmaskclient.R.string.warning_option_try_pt;
import static se.leap.bitmaskclient.eip.EIP.EIPErrors.UNKNOWN;
import static se.leap.bitmaskclient.eip.EIP.EIPErrors.valueOf;
import static se.leap.bitmaskclient.eip.EIP.ERRORS;
import static se.leap.bitmaskclient.eip.EIP.ERRORID;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.getUseBridges;
import static se.leap.bitmaskclient.base.utils.PreferenceHelper.useBridges;

/**
 * Implements an error dialog for the main activity.
 *
 * @author fupduck
 * @author cyberta
 */
public class MainActivityErrorDialog extends DialogFragment {

    final public static String TAG = "downloaded_failed_dialog";
    final private static String KEY_REASON_TO_FAIL = "key reason to fail";
    final private static String KEY_PROVIDER = "key provider";
    private String reasonToFail;
    private String[] args;
    private EIP.EIPErrors downloadError = UNKNOWN;

    private Provider provider;

    /**
     * @return a new instance of this DialogFragment.
     */
    public static DialogFragment newInstance(Provider provider, String reasonToFail) {
        return newInstance(provider, reasonToFail, UNKNOWN);
    }

    /**
     * @return a new instance of this DialogFragment.
     */
    public static DialogFragment newInstance(Provider provider, String reasonToFail, EIP.EIPErrors error, String... args) {
        MainActivityErrorDialog dialogFragment = new MainActivityErrorDialog();
        dialogFragment.reasonToFail = reasonToFail;
        dialogFragment.provider = provider;
        dialogFragment.downloadError = error;
        dialogFragment.args = args;
        return dialogFragment;
    }

    /**
     * @return a new instance of this DialogFragment.
     */
    public static DialogFragment newInstance(Provider provider, JSONObject errorJson) {
        MainActivityErrorDialog dialogFragment = new MainActivityErrorDialog();
        dialogFragment.provider = provider;
        try {
            if (errorJson.has(ERRORS)) {
                dialogFragment.reasonToFail = errorJson.getString(ERRORS);
            } else {
                //default error msg
                dialogFragment.reasonToFail = dialogFragment.getString(R.string.error_io_exception_user_message);
            }

            if (errorJson.has(ERRORID)) {
                dialogFragment.downloadError = valueOf(errorJson.getString(ERRORID));
            }
        } catch (Exception e) {
            e.printStackTrace();
            dialogFragment.reasonToFail = dialogFragment.getString(R.string.error_io_exception_user_message);
        }
        return dialogFragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        restoreFromSavedInstance(savedInstanceState);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        Context applicationContext = getContext().getApplicationContext();
        builder.setMessage(reasonToFail);
        switch (downloadError) {
            case ERROR_INVALID_VPN_CERTIFICATE:
                builder.setPositiveButton(R.string.update_certificate, (dialog, which) ->
                        ProviderAPICommand.execute(getContext(), UPDATE_INVALID_VPN_CERTIFICATE, provider));
                builder.setNegativeButton(R.string.cancel, (dialog, id) -> {});
                break;
            case NO_MORE_GATEWAYS:
                builder.setNegativeButton(R.string.cancel, (dialog, id) -> {});
                if (getPreferredCity(applicationContext) != null) {
                    builder.setPositiveButton(R.string.warning_option_try_best, (dialog, which) -> {
                        new Thread(() -> {
                            setPreferredCity(applicationContext, null);
                            EipCommand.startVPN(applicationContext, false);
                        }).start();
                    });
                } else if (provider.supportsPluggableTransports()) {
                    if (getUseBridges(applicationContext)) {
                        builder.setPositiveButton(warning_option_try_ovpn, ((dialog, which) -> {
                            useBridges(applicationContext, false);
                            EipCommand.startVPN(applicationContext, false);
                        }));
                    } else {
                        builder.setPositiveButton(warning_option_try_pt, ((dialog, which) -> {
                            useBridges(applicationContext, true);
                            EipCommand.startVPN(applicationContext, false);
                        }));
                    }
                } else {
                    builder.setPositiveButton(R.string.retry, (dialog, which) -> {
                        EipCommand.startVPN(applicationContext, false);
                    });
                }
                break;
            case ERROR_VPN_PREPARE:
                builder.setPositiveButton(android.R.string.ok, (dialog, which) -> { });
                break;
            case TRANSPORT_NOT_SUPPORTED:

                builder.setPositiveButton(R.string.option_different_location, (dialog, which) -> { });
                builder.setNegativeButton(R.string.option_disable_bridges, (dialog, which) -> {
                    PreferenceHelper.useBridges(applicationContext, false);
                    PreferenceHelper.setPreferredCity(applicationContext, args[0]);

                    EipCommand.startVPN(applicationContext, false);
                    // at this point the gateway selection dialog is shown, let's switch to the main view
                    Intent intent = new Intent(getContext(), MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.setAction(ACTION_SHOW_VPN_FRAGMENT);
                    startActivity(intent);
                });
                break;
            default:
                break;
        }

        // Create the AlertDialog object and return it
        return builder.create();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_REASON_TO_FAIL, reasonToFail);
        outState.putParcelable(KEY_PROVIDER, provider);
    }

    private void restoreFromSavedInstance(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            return;
        }
        if (savedInstanceState.containsKey(KEY_PROVIDER)) {
            this.provider = savedInstanceState.getParcelable(KEY_PROVIDER);
        }
        if (savedInstanceState.containsKey(KEY_REASON_TO_FAIL)) {
            this.reasonToFail = savedInstanceState.getString(KEY_REASON_TO_FAIL);
        }
    }

}

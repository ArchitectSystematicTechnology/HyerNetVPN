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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.ref.WeakReference;

import se.leap.bitmaskclient.ProviderSetupInterface.ProviderConfigState;

/**
 * Broadcast receiver that handles callback intents of ProviderApi during provider setup.
 * It is used by CustomProviderSetupActivity for custom branded apps and ProviderListActivity
 * for 'normal' Bitmask.
 *
 * Created by cyberta on 17.08.18.
 */

public class ProviderApiSetupBroadcastReceiver extends BroadcastReceiver {
    private WeakReference<ProviderSetupInterface> setupInterfaceRef;

    public ProviderApiSetupBroadcastReceiver(ProviderSetupInterface setupInterface) {
        this.setupInterfaceRef = new WeakReference<>(setupInterface);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(ProviderListBaseActivity.TAG, "received Broadcast");
        ProviderSetupInterface setupInterface = setupInterfaceRef.get();
        String action = intent.getAction();
        if (action == null || !action.equalsIgnoreCase(Constants.BROADCAST_PROVIDER_API_EVENT) || setupInterface == null) {
            return;
        }

        if (setupInterface.getConfigState() != null &&
                setupInterface.getConfigState() == ProviderConfigState.SETTING_UP_PROVIDER) {
            int resultCode = intent.getIntExtra(Constants.BROADCAST_RESULT_CODE, ProviderListBaseActivity.RESULT_CANCELED);
            Log.d(ProviderListBaseActivity.TAG, "Broadcast resultCode: " + resultCode);

            Bundle resultData = intent.getParcelableExtra(Constants.BROADCAST_RESULT_KEY);
            Provider handledProvider = resultData.getParcelable(Constants.PROVIDER_KEY);

            if (handledProvider != null && setupInterface.getProvider() != null &&
                    handledProvider.getDomain().equalsIgnoreCase(setupInterface.getProvider().getDomain())) {
                switch (resultCode) {
                    case ProviderAPI.PROVIDER_OK:
                        setupInterface.handleProviderSetUp(handledProvider);
                        break;
                    case ProviderAPI.PROVIDER_NOK:
                        setupInterface.handleProviderSetupFailed(resultData);
                        break;
                    case ProviderAPI.CORRECTLY_DOWNLOADED_VPN_CERTIFICATE:
                        setupInterface.handleCorrectlyDownloadedCertificate(handledProvider);
                        break;
                    case ProviderAPI.INCORRECTLY_DOWNLOADED_VPN_CERTIFICATE:
                        setupInterface.handleIncorrectlyDownloadedCertificate();
                        break;
                }
            }
        }
    }

}
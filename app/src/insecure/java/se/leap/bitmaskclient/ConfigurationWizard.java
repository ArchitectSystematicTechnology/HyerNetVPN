/**
 * Copyright (c) 2013 LEAP Encryption Access Project and contributors
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

import android.app.DialogFragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import java.net.MalformedURLException;
import java.net.URL;

import se.leap.bitmaskclient.ProviderListContent.ProviderItem;

/**
 * Activity that builds and shows the list of known available providers.
 * <p/>
 * It also allows the user to enter custom providers with a button.
 *
 * @author parmegv
 * @author cyberta
 */
public class ConfigurationWizard extends BaseConfigurationWizard {

    @Override
    protected void onItemSelectedLogic() {
        boolean danger_on = preferences.getBoolean(ProviderItem.DANGER_ON, true);
        setUpProvider(danger_on);
    }

    @Override
    public void cancelSettingUpProvider() {
        super.cancelSettingUpProvider();
        preferences.edit().remove(ProviderItem.DANGER_ON).apply();
    }

    /**
     * Open the new provider dialog with data
     */
    public void addAndSelectNewProvider(String main_url, boolean danger_on) {
        FragmentTransaction fragment_transaction = fragment_manager.removePreviousFragment(NewProviderDialog.TAG);

        DialogFragment newFragment = new NewProviderDialog();
        Bundle data = new Bundle();
        data.putString(Provider.MAIN_URL, main_url);
        data.putBoolean(ProviderItem.DANGER_ON, danger_on);
        newFragment.setArguments(data);
        newFragment.show(fragment_transaction, NewProviderDialog.TAG);
    }

    public void showAndSelectProvider(String provider_main_url, boolean danger_on) {
        try {
            selected_provider = new Provider(new URL((provider_main_url)));
            adapter.add(selected_provider);
            adapter.saveProviders();
            autoSelectProvider(selected_provider, danger_on);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    private void autoSelectProvider(Provider provider, boolean danger_on) {
        preferences.edit().putBoolean(ProviderItem.DANGER_ON, danger_on).apply();
        selected_provider = provider;
        onItemSelectedLogic();
        onItemSelectedUi();
    }

    /**
     * Asks ProviderAPI to download a new provider.json file
     *
     * @param danger_on tells if HTTPS client should bypass certificate errors
     */
    public void setUpProvider(boolean danger_on) {
        mConfigState.setAction(SETTING_UP_PROVIDER);
        Intent provider_API_command = new Intent(this, ProviderAPI.class);
        Bundle parameters = new Bundle();
        parameters.putString(Provider.MAIN_URL, selected_provider.getMainUrl().toString());
        parameters.putBoolean(ProviderItem.DANGER_ON, danger_on);
        if (selected_provider.hasCertificatePin()){
            parameters.putString(Provider.CA_CERT_FINGERPRINT, selected_provider.certificatePin());
        }
        if (selected_provider.hasCaCert()) {
            parameters.putString(Provider.CA_CERT, selected_provider.getCaCert());
        }
        if (selected_provider.hasDefinition()) {
            parameters.putString(Provider.KEY, selected_provider.getDefinition().toString());
        }

        provider_API_command.setAction(ProviderAPI.SET_UP_PROVIDER);
        provider_API_command.putExtra(ProviderAPI.PARAMETERS, parameters);
        provider_API_command.putExtra(ProviderAPI.RECEIVER_KEY, providerAPI_result_receiver);

        startService(provider_API_command);
    }

    /**
     * Retrys setup of last used provider, allows bypassing ca certificate validation.
     */
    @Override
    public void retrySetUpProvider() {
        cancelSettingUpProvider();
        if (!ProviderAPI.caCertDownloaded()) {
            addAndSelectNewProvider(ProviderAPI.lastProviderMainUrl(), ProviderAPI.lastDangerOn());
        } else {
            showProgressBar();
            adapter.hideAllBut(adapter.indexOf(selected_provider));

            Intent provider_API_command = new Intent(this, ProviderAPI.class);

            provider_API_command.setAction(ProviderAPI.SET_UP_PROVIDER);
            provider_API_command.putExtra(ProviderAPI.RECEIVER_KEY, providerAPI_result_receiver);
            Bundle parameters = new Bundle();
            parameters.putString(Provider.MAIN_URL, selected_provider.getMainUrl().toString());
            provider_API_command.putExtra(ProviderAPI.PARAMETERS, parameters);

            startService(provider_API_command);
        }
    }

}

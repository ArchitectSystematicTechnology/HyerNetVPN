/**
 * Copyright (c) 2013 LEAP Encryption Access Project and contributers
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

import androidx.annotation.NonNull;

import static se.leap.bitmaskclient.ProviderAPI.SET_UP_PROVIDER;
import static se.leap.bitmaskclient.ProviderSetupInterface.ProviderConfigState.SETTING_UP_PROVIDER;

/**
 * Activity that builds and shows the list of known available providers.
 * <p/>
 * It also allows the user to enter custom providers with a button.
 *
 * @author parmegv
 * @author cyberta
 */
public class ProviderListActivity extends ProviderListBaseActivity {


    @Override
    protected void onItemSelectedLogic() {
        setUpProvider();
    }

    /**
     * Asks ProviderAPI to download a new provider.json file
     *
     */
    public void setUpProvider() {
        providerConfigState = SETTING_UP_PROVIDER;
        ProviderAPICommand.execute(this, SET_UP_PROVIDER, provider);
    }

    @Override
    public void retrySetUpProvider(@NonNull Provider provider) {
        providerConfigState = SETTING_UP_PROVIDER;
        ProviderAPICommand.execute(this, SET_UP_PROVIDER, provider);
    }

}

package se.leap.bitmaskclient.providersetup.fragments.viewmodel;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.InputType;
import android.util.Patterns;
import android.view.View;
import android.webkit.URLUtil;

import androidx.lifecycle.ViewModel;

import java.util.List;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.models.Introducer;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.providersetup.ProviderManager;

public class ProviderSelectionViewModel extends ViewModel {
    private final ProviderManager providerManager;
    public static int ADD_PROVIDER = 100100100;
    public static int INVITE_CODE_PROVIDER = 200100100;

    private int selected = 0;
    private String customUrl;

    public ProviderSelectionViewModel(AssetManager assetManager) {
        providerManager = ProviderManager.getInstance(assetManager);
        providerManager.setAddDummyEntry(false);
    }

    public int size() {
        return providerManager.size();
    }

    public List<Provider> providers() {
        return providerManager.providers();
    }

   public Provider getProvider(int pos) {
        return providerManager.get(pos);
   }

    public void setSelected(int checkedId) {
        selected = checkedId;
    }

    public int getSelected() {
        return selected;
    }

    public boolean isValidConfig() {
        if (selected == ADD_PROVIDER) {
            return customUrl != null && (Patterns.DOMAIN_NAME.matcher(customUrl).matches() || (URLUtil.isNetworkUrl(customUrl) && Patterns.WEB_URL.matcher(customUrl).matches()));
        }
        if (selected == INVITE_CODE_PROVIDER) {
            try {
                Introducer introducer = Introducer.fromUrl(customUrl);
                return introducer.validate();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public boolean isCustomProviderSelected() {
        return selected == ADD_PROVIDER || selected == INVITE_CODE_PROVIDER;
    }

    public CharSequence getProviderDescription(Context context) {
        if (selected == ADD_PROVIDER) {
            return context.getText(R.string.add_provider_description);
        }
        if (selected == INVITE_CODE_PROVIDER) {
            return context.getText(R.string.invite_code_provider_description);
        }
        Provider provider = getProvider(selected);
        if ("riseup.net".equals(provider.getDomain())) {
            return context.getText(R.string.provider_description_riseup);
        }
        if ("calyx.net".equals(provider.getDomain())) {
            return context.getText(R.string.provider_description_calyx);
        }
        return provider.getDescription();
    }

    public int getEditProviderVisibility() {
        if (selected == ADD_PROVIDER) {
            return View.VISIBLE;
        } else if (selected == INVITE_CODE_PROVIDER) {
            return View.VISIBLE;
        }
        return View.GONE;
    }
    public int getEditInputType() {
        if (selected == INVITE_CODE_PROVIDER) {
            return InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        return InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT;
    }

    public int getEditInputLines() {
        if (selected == INVITE_CODE_PROVIDER) {
            return 3;
        }
        return 2;
    }


    public void setCustomUrl(String url) {
        customUrl = url;
    }

    public String getCustomUrl() {
        if (customUrl != null && Patterns.DOMAIN_NAME.matcher(customUrl).matches()) {
            return "https://" + customUrl;
        }
        return customUrl;
    }


    public String getProviderName(int pos) {
        String domain = getProvider(pos).getDomain();
        if ("riseup.net".equals(domain)) {
            return "Riseup";
        }
        if ("calyx.net".equals(domain)) {
            return "The Calyx Institute";
        }
        return domain;
    }

    public CharSequence getHint(Context context) {
        if (selected == ADD_PROVIDER) {
            return context.getText(R.string.add_provider_prompt);
        }
        if (selected == INVITE_CODE_PROVIDER) {
            return context.getText(R.string.invite_code_provider_prompt);
        }
        return "";
    }
}
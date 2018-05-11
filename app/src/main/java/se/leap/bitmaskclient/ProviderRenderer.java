package se.leap.bitmaskclient;

import android.content.*;
import android.view.*;
import android.widget.*;

import com.pedrogomez.renderers.*;

import butterknife.*;

/**
 * Created by parmegv on 4/12/14.
 */
public class ProviderRenderer extends Renderer<Provider> {
    private final Context context;

    @InjectView(R.id.provider_name)
    TextView name;
    @InjectView(R.id.provider_domain)
    TextView domain;

    public ProviderRenderer(Context context) {
        this.context = context;
    }

    @Override
    protected View inflate(LayoutInflater inflater, ViewGroup parent) {
        View view = inflater.inflate(R.layout.v_provider_list_item, parent, false);
        ButterKnife.inject(this, view);
        return view;
    }

    @Override
    protected void setUpView(View rootView) {
        /*
         * Empty implementation substituted with the usage of ButterKnife library by Jake Wharton.
         */
    }

    @Override
    protected void hookListeners(View rootView) {
        //Empty
    }

    @Override
    public void render() {
        Provider provider = getContent();
        if (!provider.isDefault()) {
            name.setText(provider.getName());
            domain.setText(provider.getDomain());
        } else {
            domain.setText(R.string.add_provider);
        }
    }
}

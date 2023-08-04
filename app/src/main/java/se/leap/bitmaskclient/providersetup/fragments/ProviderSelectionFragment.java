package se.leap.bitmaskclient.providersetup.fragments;

import static se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModel.ADD_PROVIDER;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.models.Provider;
import se.leap.bitmaskclient.base.models.ProviderObservable;
import se.leap.bitmaskclient.base.utils.ViewHelper;
import se.leap.bitmaskclient.databinding.FProviderSelectionBinding;
import se.leap.bitmaskclient.providersetup.activities.CancelCallback;
import se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModel;
import se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModelFactory;

public class ProviderSelectionFragment extends BaseSetupFragment implements CancelCallback {

    private ProviderSelectionViewModel viewModel;
    private ArrayList<RadioButton> radioButtons;

    private FProviderSelectionBinding binding;

    private ProviderSelectionFragment(int position) {
        super(position);
    }

    public static ProviderSelectionFragment newInstance(int position) {
        return new ProviderSelectionFragment(position);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this,
                new ProviderSelectionViewModelFactory(
                        getContext().getApplicationContext().getAssets(),
                        getContext().getExternalFilesDir(null))).
                get(ProviderSelectionViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FProviderSelectionBinding.inflate(inflater, container, false);

        radioButtons = new ArrayList<>();
        for (int i = 0; i < viewModel.size(); i++) {
            RadioButton radioButton = new RadioButton(binding.getRoot().getContext());
            radioButton.setText(viewModel.getProviderName(i));
            radioButton.setId(i);
            binding.providerRadioGroup.addView(radioButton);
            radioButtons.add(radioButton);
        }
        RadioButton radioButton = new RadioButton(binding.getRoot().getContext());
        radioButton.setText(getText(R.string.add_provider));
        radioButton.setId(ADD_PROVIDER);
        binding.providerRadioGroup.addView(radioButton);
        radioButtons.add(radioButton);

        binding.editCustomProvider.setVisibility(viewModel.getEditProviderVisibility());
        binding.providerRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            viewModel.setSelected(checkedId);
            for (RadioButton rb : radioButtons) {
                rb.setTypeface(Typeface.DEFAULT, rb.getId() == checkedId ? Typeface.BOLD : Typeface.NORMAL);
            }
            binding.providerDescription.setText(viewModel.getProviderDescription(getContext()));
            binding.editCustomProvider.setVisibility(viewModel.getEditProviderVisibility());
            setupActivityCallback.onSetupStepValidationChanged(viewModel.isValidConfig());
            if (checkedId != ADD_PROVIDER) {
                setupActivityCallback.onProviderSelected(viewModel.getProvider(checkedId));
            } else if (viewModel.isValidConfig()) {
                setupActivityCallback.onProviderSelected(new Provider(binding.editCustomProvider.getText().toString()));
            }
        });
        binding.providerRadioGroup.check(viewModel.getSelected());

        binding.editCustomProvider.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setCustomUrl(s.toString());
                setupActivityCallback.onSetupStepValidationChanged(viewModel.isValidConfig());
                if (viewModel.isValidConfig()) {
                    setupActivityCallback.onProviderSelected(new Provider(s.toString()));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        binding.editCustomProvider.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                ViewHelper.hideKeyboardFrom(getContext(), v);
            }
        });
        return binding.getRoot();
    }

    @Override
    public void onFragmentSelected() {
        super.onFragmentSelected();
        setupActivityCallback.setCancelButtonHidden(!ProviderObservable.getInstance().getCurrentProvider().isConfigured());
        setupActivityCallback.setNavigationButtonHidden(false);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        setupActivityCallback.registerCancelCallback(this);
    }

    @Override
    public void onDetach() {
        setupActivityCallback.removeCancelCallback(this);
        super.onDetach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        radioButtons = null;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        setupActivityCallback.onSetupStepValidationChanged(viewModel.isValidConfig());
    }

    @Override
    public void onCanceled() {
        binding.providerRadioGroup.check(0);
    }
}
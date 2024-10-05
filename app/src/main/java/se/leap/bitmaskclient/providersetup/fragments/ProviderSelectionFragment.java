package se.leap.bitmaskclient.providersetup.fragments;

import static se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModel.ADD_PROVIDER;
import static se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModel.INVITE_CODE_PROVIDER;

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
import se.leap.bitmaskclient.base.utils.ViewHelper;
import se.leap.bitmaskclient.databinding.FProviderSelectionBinding;
import se.leap.bitmaskclient.providersetup.activities.CancelCallback;
import se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModel;
import se.leap.bitmaskclient.providersetup.fragments.viewmodel.ProviderSelectionViewModelFactory;

public class ProviderSelectionFragment extends BaseSetupFragment implements CancelCallback {

    private ProviderSelectionViewModel viewModel;
    private ArrayList<RadioButton> radioButtons;

    private FProviderSelectionBinding binding;

    public static ProviderSelectionFragment newInstance(int position) {
        ProviderSelectionFragment fragment = new ProviderSelectionFragment();
        fragment.setArguments(initBundle(position));
        return fragment;
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this,
                new ProviderSelectionViewModelFactory(
                        getContext().getApplicationContext().getAssets())).
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
        RadioButton inviteCodeRadioButton = new RadioButton(binding.getRoot().getContext());
        inviteCodeRadioButton.setText(R.string.enter_invite_code);
        inviteCodeRadioButton.setId(INVITE_CODE_PROVIDER);
        binding.providerRadioGroup.addView(inviteCodeRadioButton);
        radioButtons.add(inviteCodeRadioButton);

        RadioButton addProviderRadioButton = new RadioButton(binding.getRoot().getContext());
        addProviderRadioButton.setText(getText(R.string.add_provider));
        addProviderRadioButton.setId(ADD_PROVIDER);
        binding.providerRadioGroup.addView(addProviderRadioButton);
        radioButtons.add(addProviderRadioButton);

        binding.editCustomProvider.setVisibility(viewModel.getEditProviderVisibility());
        binding.syntaxCheck.setVisibility(viewModel.getEditProviderVisibility());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupActivityCallback.registerCancelCallback(this);
    }

    @Override
    public void onFragmentSelected() {
        super.onFragmentSelected();
        setupActivityCallback.setCancelButtonHidden(true);
        setupActivityCallback.setNavigationButtonHidden(false);
        binding.providerRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            viewModel.setSelected(checkedId);
            for (RadioButton rb : radioButtons) {
                rb.setTypeface(Typeface.DEFAULT, rb.getId() == checkedId ? Typeface.BOLD : Typeface.NORMAL);
            }
            binding.providerDescription.setText(viewModel.getProviderDescription(getContext()));
            binding.editCustomProvider.setVisibility(viewModel.getEditProviderVisibility());
            binding.syntaxCheck.setVisibility(viewModel.getEditProviderVisibility());
            if (viewModel.getCustomUrl() == null || viewModel.getCustomUrl().isEmpty()) {
                binding.syntaxCheckResult.setText("");
                binding.syntaxCheckResult.setTextColor(getResources().getColor(R.color.color_font_btn));
                binding.editCustomProvider.setHint(viewModel.getHint(getContext()));
            } else {
                binding.editCustomProvider.setText("");
            }
            binding.editCustomProvider.setRawInputType(viewModel.getEditInputType());
            setupActivityCallback.onSetupStepValidationChanged(viewModel.isValidConfig());
            if (checkedId != ADD_PROVIDER && checkedId != INVITE_CODE_PROVIDER) {
                setupActivityCallback.onProviderSelected(viewModel.getProvider(checkedId));
            } else if (viewModel.isValidConfig()) {
                setupActivityCallback.onProviderSelected(new Provider(binding.editCustomProvider.getText().toString(),checkedId));
            }
        });

        binding.editCustomProvider.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                viewModel.setCustomUrl(s.toString());
                if (viewModel.isCustomProviderSelected()) {
                    setupActivityCallback.onSetupStepValidationChanged(viewModel.isValidConfig());
                    if (viewModel.isValidConfig()) {
                        setupActivityCallback.onProviderSelected(new Provider(viewModel.getCustomUrl(),viewModel.getSelected()));
                        binding.syntaxCheckResult.setText(getString(R.string.validation_status_success));
                        binding.syntaxCheckResult.setTextColor(getResources().getColor(R.color.green200));
                    } else {
                        binding.syntaxCheckResult.setText(getString(R.string.validation_status_failure));
                        binding.syntaxCheckResult.setTextColor(getResources().getColor(R.color.red200));
                    }
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

        binding.getRoot().getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if(ViewHelper.isKeyboardShown(getContext())) {
                binding.getRoot().smoothScrollTo(binding.editCustomProvider.getLeft(), binding.getRoot().getBottom());
            }
        });
        binding.providerRadioGroup.check(viewModel.getSelected());
    }

    @Override
    public void onDestroyView() {
        setupActivityCallback.removeCancelCallback(this);
        binding = null;
        radioButtons = null;
        super.onDestroyView();
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
package se.leap.bitmaskclient.base.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;

import se.leap.bitmaskclient.base.utils.ObfsVpnHelper;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.base.views.IconSwitchEntry;
import se.leap.bitmaskclient.databinding.DObfuscationProxyBinding;
import se.leap.bitmaskclient.eip.GatewaysManager;

public class ObfuscationProxyDialog extends AppCompatDialogFragment {
    public static final String TAG = ObfuscationProxyDialog.class.getSimpleName();
    DObfuscationProxyBinding binding;
    AppCompatEditText ipField;
    AppCompatEditText portField;
    AppCompatEditText certificateField;
    AppCompatButton saveButton;
    AppCompatButton useDefaultsButton;
    AppCompatButton cancelButton;
    IconSwitchEntry kcpSwitch;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DObfuscationProxyBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        ipField = binding.ipField;
        portField = binding.portField;
        certificateField = binding.certField;
        saveButton = binding.buttonSave;
        useDefaultsButton = binding.buttonDefaults;
        cancelButton = binding.buttonCancel;
        kcpSwitch = binding.kcpSwitch;

        ipField.setText(PreferenceHelper.getObfuscationPinningIP());
        portField.setText(PreferenceHelper.getObfuscationPinningPort());
        certificateField.setText(PreferenceHelper.getObfuscationPinningCert());
        kcpSwitch.setChecked(PreferenceHelper.getObfuscationPinningKCP());

        GatewaysManager gatewaysManager = new GatewaysManager(getContext());

        saveButton.setOnClickListener(v -> {
            String ip = TextUtils.isEmpty(ipField.getText()) ? null : ipField.getText().toString();
            PreferenceHelper.setObfuscationPinningIP(ip);
            String port = TextUtils.isEmpty(portField.getText()) ? null : portField.getText().toString();
            PreferenceHelper.setObfuscationPinningPort(port);
            String cert = TextUtils.isEmpty(certificateField.getText()) ? null : certificateField.getText().toString();
            PreferenceHelper.setObfuscationPinningCert(cert);
            PreferenceHelper.setObfuscationPinningKCP(kcpSwitch.isChecked());
            PreferenceHelper.setUseObfuscationPinning(ip != null && port != null && cert != null);
            PreferenceHelper.setObfuscationPinningGatewayLocation(gatewaysManager.getLocationNameForIP(ip, v.getContext()));
            dismiss();
        });

        useDefaultsButton.setVisibility(ObfsVpnHelper.hasObfuscationPinningDefaults() ? VISIBLE : GONE);
        useDefaultsButton.setOnClickListener(v -> {
           ipField.setText(ObfsVpnHelper.obfsvpnIP());
           portField.setText(ObfsVpnHelper.obfsvpnPort());
           certificateField.setText(ObfsVpnHelper.obfsvpnCert());
           kcpSwitch.setChecked(ObfsVpnHelper.useKcp());
        });

        cancelButton.setOnClickListener(v -> {
            boolean allowPinning = !TextUtils.isEmpty(ipField.getText()) && !TextUtils.isEmpty(portField.getText()) && !TextUtils.isEmpty(certificateField.getText());
            PreferenceHelper.setUseObfuscationPinning(allowPinning);
            dismiss();
        });

        return builder.create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

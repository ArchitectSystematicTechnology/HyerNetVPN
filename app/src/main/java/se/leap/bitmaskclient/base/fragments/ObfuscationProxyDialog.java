package se.leap.bitmaskclient.base.fragments;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static se.leap.bitmaskclient.base.utils.ConfigHelper.hasPTAllowedProtocol;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.appcompat.widget.AppCompatButton;
import androidx.appcompat.widget.AppCompatEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import de.blinkt.openvpn.core.connection.Connection;
import se.leap.bitmaskclient.base.models.Transport;
import se.leap.bitmaskclient.base.utils.ConfigHelper;
import se.leap.bitmaskclient.base.utils.ConfigHelper.ObfsVpnHelper;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;
import se.leap.bitmaskclient.databinding.DObfuscationProxyBinding;
import se.leap.bitmaskclient.eip.GatewaysManager;

public class ObfuscationProxyDialog extends AppCompatDialogFragment {
    public static final String TAG = ObfuscationProxyDialog.class.getSimpleName();
    DObfuscationProxyBinding binding;
    AppCompatEditText bridgeConfig;
    TextView validityCheck;
    AppCompatButton saveButton;
    AppCompatButton useDefaultsButton;
    AppCompatButton cancelButton;
    ArrayList<String> errors;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DObfuscationProxyBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(view);
        bridgeConfig = binding.bridgeConfig;
        validityCheck = binding.validityCheckHint;
        saveButton = binding.buttonSave;
        useDefaultsButton = binding.buttonDefaults;
        cancelButton = binding.buttonCancel;
        errors = new ArrayList<>();


        GatewaysManager gatewaysManager = new GatewaysManager(getContext());

        bridgeConfig.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    errors.clear();
                    JSONObject jsonObject = new JSONObject(s.toString());
                    Transport transport = Transport.fromJson(jsonObject);
                    if (transport.getOptions() == null) {
                        errors.add("Missing Bridge Options");
                    } else {
                        if (transport.getOptions().getEndpoints() == null ||
                                transport.getOptions().getEndpoints().length == 0) {
                            errors.add("Cert and IP is missing");
                        }
                        int iatMode;
                        try {
                            if (transport.getOptions().getIatMode() == null ||
                                    transport.getOptions().getIatMode().isEmpty()) {
                                errors.add("iat mode is missing");
                            } else if ((iatMode = Integer.parseInt(transport.getOptions().getIatMode())) < 0 || (iatMode > 1)) {
                                errors.add("invalid iat mode (0 or 1)");
                            }
                        } catch (NumberFormatException nfe) {
                            errors.add("invalid iat mode (0 or 1)");
                        }
                        if (transport.getOptions().getEndpoints() != null) {
                            for (Transport.Endpoint endpoint : transport.getOptions().getEndpoints()) {
                                if (endpoint.getIp() == null || endpoint.getIp().isEmpty()) {
                                    errors.add("IP is missing");
                                } else if (!ConfigHelper.isIPv4(endpoint.getIp())) {
                                    errors.add("Invalid IPv4 address");
                                }
                                if (endpoint.getCert() == null || endpoint.getCert().isEmpty()) {
                                    errors.add("Cert is missing");
                                }
                            }
                        }
                    }

                    if (transport.getProtocols() == null || transport.getProtocols().length == 0) {
                        errors.add("missing protocols");
                    }
                    boolean hasValidTransportType = false;
                    try {
                        if (transport.getTransportType().isPluggableTransport()) {
                            hasValidTransportType = true;
                        }
                    } catch (NullPointerException | IllegalArgumentException e) {}
                    if (!hasValidTransportType) {
                        errors.add("invalid bridge transport type");
                    } else {
                        if (!hasPTAllowedProtocol(transport)) {
                            errors.add("invalid protocol for transport " + transport.getType());
                        }
                        if (transport.getTransportType() != Connection.TransportType.OBFS4_HOP &&
                                (transport.getPorts() == null || transport.getPorts().length == 0)) {
                            errors.add("ports are missing");
                        } else {
                            for (String port: transport.getPorts()) {
                                try {
                                    int portNumber = Integer.parseInt(port);
                                    if (portNumber < 1 || portNumber > 65535) {
                                        errors.add("invalid port number (1-65535)");
                                    }
                                } catch (NumberFormatException e) {
                                    if (port.isEmpty()) {
                                        errors.add("bridge port value cannot be empty");
                                    } else {
                                        errors.add(port + " is an invalid value for bridge ports");
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.add("invalid json format");
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (int i = 0; i < errors.size(); i++) {
                    stringBuilder.append(errors.get(i)).append("\n");
                    int diff = 0;
                    if (i == 2 && (diff = errors.size() - 3) > 0) {
                        stringBuilder.append(diff + " more...");
                        break;
                    }
                }
                validityCheck.setText(stringBuilder.toString());
                saveButton.setEnabled(errors.size() == 0);
            }
        });

        Transport transport = PreferenceHelper.getObfuscationPinningTransport(getContext(), false);
        if (transport != null) {
            bridgeConfig.setText(transport.toPrettyPrint());
        }

        saveButton.setOnClickListener(v -> {
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(bridgeConfig.getText().toString());
                Transport t = Transport.fromJson(jsonObject);
                PreferenceHelper.setObfuscationPinningGatewayLocation(v.getContext(), gatewaysManager.getLocationNameForIP(t.getIPFromEndpoints(), v.getContext()));
                PreferenceHelper.setObfuscationPinningTransport(v.getContext(), t);
            } catch (JSONException | NullPointerException | ArrayIndexOutOfBoundsException e) {}

            dismiss();
        });

        useDefaultsButton.setVisibility(ObfsVpnHelper.hasObfuscationPinningDefaults() ? VISIBLE : GONE);
        useDefaultsButton.setOnClickListener(v -> {
           //TODO: implement me
        });

        cancelButton.setOnClickListener(v -> {
            JSONObject jsonObject = null;
            boolean validJson = true;
            try {
                jsonObject = new JSONObject(bridgeConfig.getText().toString());
                Transport.fromJson(jsonObject);
            } catch (JSONException | NullPointerException e) {
                validJson = false;
            }

            PreferenceHelper.setUseObfuscationPinning(
                    v.getContext(), validJson && errors.size() == 0);
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

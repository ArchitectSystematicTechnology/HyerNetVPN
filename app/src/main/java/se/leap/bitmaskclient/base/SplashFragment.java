package se.leap.bitmaskclient.base;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.databinding.FragmentSplashBinding;

public class SplashFragment extends Fragment {
    public SplashFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        FragmentSplashBinding mBinding = FragmentSplashBinding.inflate(inflater, container, false);
        mBinding.ivAction.setOnClickListener(v -> {
            if (getActivity() != null && getActivity() instanceof StartActivity) {
                StartActivity startActivity = (StartActivity) getActivity();
                startActivity.prepareEIP();
            }
        });

        return mBinding.getRoot();
    }
}
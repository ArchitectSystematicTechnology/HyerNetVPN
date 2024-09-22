package se.leap.bitmaskclient.base.fragments;

import static se.leap.bitmaskclient.base.utils.ViewHelper.setActionBarSubtitle;

import android.app.LocaleManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.views.SimpleCheckBox;
import se.leap.bitmaskclient.databinding.FLanguageSelectionBinding;
import se.leap.bitmaskclient.databinding.VSelectTextListItemBinding;

public class LanguageSelectionFragment extends BottomSheetDialogFragment {
    static final String TAG = LanguageSelectionFragment.class.getSimpleName();
    static final String SYSTEM_LOCALE = "systemLocale";
    private FLanguageSelectionBinding binding;

    public static LanguageSelectionFragment newInstance(Locale defaultLocale) {
        LanguageSelectionFragment fragment = new LanguageSelectionFragment();
        Bundle args = new Bundle();
        args.putString(SYSTEM_LOCALE, defaultLocale.toLanguageTag());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FLanguageSelectionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setActionBarSubtitle(this, R.string.select_language);

        initDefaultSelection();
        initRecyclerView();
    }

    private void initDefaultSelection() {
        customizeSelectionItem(binding.defaultLanguage);
        if (!getSupportedLanguages(getResources()).containsKey(getCurrentLocale().toLanguageTag())) {
            binding.defaultLanguage.selected.setChecked(true);
        }
        binding.defaultLanguage.location.setText(R.string.system_language);
        binding.defaultLanguage.getRoot().setOnClickListener(v -> {
            updateLocale("");
        });
    }

    private static void customizeSelectionItem(VSelectTextListItemBinding binding) {
        binding.title.setVisibility(View.GONE);
        binding.bridgeImage.setVisibility(View.GONE);
        binding.quality.setVisibility(View.GONE);
    }

    private void initRecyclerView() {
        List<Language> languageList = getLanguages();
        Locale defaultLocale = AppCompatDelegate.getApplicationLocales().get(0);
        if (defaultLocale == null) {
            defaultLocale = LocaleListCompat.getDefault().get(0);
        }
        binding.languages.setAdapter(
                new LanguageSelectionAdapter(languageList, language -> {
                    updateLocale(language.tag);
                }, defaultLocale.toLanguageTag())
        );
        binding.languages.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    /**
     * Get the list of supported languages from the resources.
     *
     * @return list of supported languages
     */
    private @NonNull List<Language> getLanguages() {
        Map<String, String> languageMap = getSupportedLanguages(getResources());

        List<Language> languageList = new ArrayList<>();
        for (Map.Entry<String, String> entry : languageMap.entrySet()) {
            languageList.add(new Language(entry.getValue(), entry.getKey()));
        }
        return languageList;
    }

    public static Locale getCurrentLocale() {
        Locale defaultLocale = AppCompatDelegate.getApplicationLocales().get(0);
        if (defaultLocale == null) {
            defaultLocale = LocaleListCompat.getDefault().get(0);
        }
        return defaultLocale;
    }

    public static Map<String, String> getSupportedLanguages(Resources resources) {
        String[] supportedLanguages = resources.getStringArray(R.array.supported_languages);
        String[] supportedLanguageNames = resources.getStringArray(R.array.supported_language_names);

        Map<String, String> languageMap = new LinkedHashMap<>();
        for (int i = 0; i < supportedLanguages.length; i++) {
            languageMap.put(supportedLanguages[i], supportedLanguageNames[i]);
        }

        return languageMap;
    }

    /**
     * Update the locale of the application
     *
     * @param languageTag the language tag to set the locale to
     */
    private void updateLocale(String languageTag) {
        if (languageTag.isEmpty()) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag));
        }
    }

    /**
     * Language record to hold the language name and tag
     *
     * @param name
     * @param tag
     */
    record Language(String name, String tag) {
    }


    /**
     * Adapter for the language selection recycler view.
     */
    static class LanguageSelectionAdapter extends RecyclerView.Adapter<LanguageSelectionAdapter.LanguageViewHolder> {

        private final List<Language> languages;
        private final LanguageClickListener clickListener;
        private final String selectedLocale;

        public LanguageSelectionAdapter(List<Language> languages, LanguageClickListener clickListener, String defaultLocale) {
            this.languages = languages;
            this.clickListener = clickListener;
            this.selectedLocale = defaultLocale;
        }

        @NonNull
        @Override
        public LanguageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            VSelectTextListItemBinding binding = VSelectTextListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
            return new LanguageViewHolder(binding);
        }

        @Override
        public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
            Language language = languages.get(position);
            holder.languageName.setText(language.name());
            holder.selected.setChecked(language.tag.equals(selectedLocale));
            holder.itemView.setOnClickListener(v -> clickListener.onLanguageClick(language));
        }

        @Override
        public int getItemCount() {
            return languages.size();
        }

        /**
         * View holder for the language item
         */
        static class LanguageViewHolder extends RecyclerView.ViewHolder {
            TextView languageName;
            SimpleCheckBox selected;

            public LanguageViewHolder(@NonNull VSelectTextListItemBinding binding) {
                super(binding.getRoot());
                languageName = binding.location;
                selected = binding.selected;
                customizeSelectionItem(binding);
            }
        }
    }


    /**
     * Interface for the language click listener
     */
    interface LanguageClickListener {
        void onLanguageClick(Language language);
    }
}

package se.leap.bitmaskclient.base.fragments;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import se.leap.bitmaskclient.R;

public class LanguageSelectionFragment extends BottomSheetDialogFragment {
    static final String TAG = LanguageSelectionFragment.class.getSimpleName();
    static final String DEFAULT_LOCALE = "defaultLocale";

    private LanguageSelectionFragment() {
    }

    public static LanguageSelectionFragment newInstance(Locale defaultLocale) {
        LanguageSelectionFragment fragment = new LanguageSelectionFragment();
        Bundle args = new Bundle();
        args.putString(DEFAULT_LOCALE, defaultLocale.toLanguageTag());
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.f_language_selection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String defaultLocale = "en-US";
        if (getArguments() != null) {
            defaultLocale = getArguments().getString(DEFAULT_LOCALE);
        }

        view.findViewById(R.id.close).setOnClickListener(v -> dismiss());

        RecyclerView languageRecyclerView = view.findViewById(R.id.languages);

        List<Language> languageList = getLanguages();

        languageRecyclerView.setAdapter(
                new LanguageSelectionAdapter(languageList, language -> {
                    updateLocale(language.tag);
                    dismiss();
                }, defaultLocale)
        );
        languageRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
    }

    /**
     * Get the list of supported languages from the resources.
     *
     * @return list of supported languages
     */
    private @NonNull List<Language> getLanguages() {
        Set<String> supportedLanguages = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.supported_languages)));
        Locale[] locales = Locale.getAvailableLocales();

        List<Language> languageList = new ArrayList<>();
        languageList.add(new Language("System Language", ""));
        for (Locale locale : locales) {
            String languageTag = locale.toLanguageTag();
            if (supportedLanguages.contains(languageTag)) {
                languageList.add(new Language(locale.getDisplayName(), languageTag));
            }
        }
        return languageList;
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
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new LanguageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
            Language language = languages.get(position);
            holder.languageName.setText(language.name());
            if (language.tag.equals(selectedLocale)) {
                holder.languageName.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.check_bold, 0);
            } else {
                holder.languageName.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            }
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

            public LanguageViewHolder(@NonNull View itemView) {
                super(itemView);
                languageName = itemView.findViewById(android.R.id.text1);
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

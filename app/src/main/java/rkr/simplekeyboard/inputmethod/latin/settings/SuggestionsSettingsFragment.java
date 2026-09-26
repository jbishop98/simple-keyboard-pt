/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rkr.simplekeyboard.inputmethod.latin.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import rkr.simplekeyboard.inputmethod.R;
import rkr.simplekeyboard.inputmethod.latin.RichInputMethodManager;
import rkr.simplekeyboard.inputmethod.latin.Subtype;
import rkr.simplekeyboard.inputmethod.latin.common.LocaleUtils;
import rkr.simplekeyboard.inputmethod.latin.suggestions.Dictionary;
import rkr.simplekeyboard.inputmethod.latin.suggestions.SuggestionController;
import rkr.simplekeyboard.inputmethod.latin.utils.LocaleResourceUtils;

/**
 * "Word suggestions" settings sub screen.
 *
 * - Show suggestions
 * - Learn from typing
 * - Import a dictionary for a language
 * - Remove imported dictionaries
 * - Clear learned words
 */
public final class SuggestionsSettingsFragment extends SubScreenFragment {
    private static final String TAG = SuggestionsSettingsFragment.class.getSimpleName();

    private static final String PREF_IMPORT_DICTIONARY = "pref_import_dictionary";
    private static final String PREF_REMOVE_IMPORTED_DICTIONARIES =
            "pref_remove_imported_dictionaries";
    private static final String PREF_CLEAR_LEARNED_WORDS = "pref_clear_learned_words";
    private static final String STATE_IMPORT_LOCALE = "import_locale";
    private static final int REQUEST_IMPORT_DICTIONARY = 1;

    private String mImportLocale;

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_suggestions);
        RichInputMethodManager.init(getActivity());
        if (icicle != null) {
            mImportLocale = icicle.getString(STATE_IMPORT_LOCALE);
        }

        findPreference(PREF_IMPORT_DICTIONARY).setOnPreferenceClickListener(preference -> {
            showImportLanguageDialog();
            return true;
        });
        findPreference(PREF_REMOVE_IMPORTED_DICTIONARIES).setOnPreferenceClickListener(
                preference -> {
                    SuggestionController.removeImportedDictionaries(getActivity());
                    showToast(getString(R.string.imported_dictionaries_removed));
                    updateRemoveImportedDictionaries();
                    return true;
                });
        findPreference(PREF_CLEAR_LEARNED_WORDS).setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.clear_learned_words_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        SuggestionController.clearLearnedWords(getActivity());
                        showToast(getString(R.string.learned_words_cleared));
                    })
                    .show();
            return true;
        });
        updateRemoveImportedDictionaries();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_IMPORT_LOCALE, mImportLocale);
    }

    private void updateRemoveImportedDictionaries() {
        final Preference preference = findPreference(PREF_REMOVE_IMPORTED_DICTIONARIES);
        if (preference != null) {
            preference.setEnabled(SuggestionController.hasImportedDictionaries(getActivity()));
        }
    }

    private void showToast(final String message) {
        final Activity activity = getActivity();
        if (activity != null) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showImportLanguageDialog() {
        final List<String> locales = new ArrayList<>();
        for (final Subtype subtype : RichInputMethodManager.getInstance().getEnabledSubtypes(true)) {
            if (!locales.contains(subtype.getLocale())) {
                locales.add(subtype.getLocale());
            }
        }
        final String[] names = new String[locales.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(locales.get(i));
        }
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.import_dictionary_language)
                .setItems(names, (dialog, which) -> {
                    mImportLocale = locales.get(which);
                    pickDictionaryFile();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pickDictionaryFile() {
        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT_DICTIONARY);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "No file picker available", e);
            showToast(getString(R.string.dictionary_import_failed));
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
            final Intent data) {
        if (requestCode != REQUEST_IMPORT_DICTIONARY || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null || mImportLocale == null) {
            return;
        }
        final Context context = getActivity().getApplicationContext();
        final Uri uri = data.getData();
        final String localeString = mImportLocale;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            final int wordCount = importDictionary(context, uri, localeString);
            mainHandler.post(() -> {
                final String message = wordCount > 0
                        ? context.getString(R.string.dictionary_imported, wordCount,
                                LocaleResourceUtils.getLocaleDisplayNameInSystemLocale(
                                        localeString))
                        : context.getString(R.string.dictionary_import_failed);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                if (isAdded()) {
                    updateRemoveImportedDictionaries();
                }
            });
        }).start();
    }

    /**
     * Copies the picked file into the app's storage, if it holds a usable word list.
     * @return the number of words imported, or 0 on failure.
     */
    private static int importDictionary(final Context context, final Uri uri,
            final String localeString) {
        final File target = SuggestionController.getImportedDictionaryFile(context, localeString);
        final File temp = new File(target.getPath() + ".tmp");
        final File dir = target.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            return 0;
        }
        try {
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                    OutputStream out = new FileOutputStream(temp)) {
                if (in == null) {
                    return 0;
                }
                final byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
            }
            final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
            final Dictionary dictionary;
            try (InputStream in = new FileInputStream(temp)) {
                dictionary = Dictionary.read(in, locale);
            }
            if (dictionary.isEmpty() || !temp.renameTo(target)) {
                return 0;
            }
            SuggestionController.onDictionariesChanged();
            return dictionary.size();
        } catch (IOException | SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Unable to import dictionary", e);
            return 0;
        } finally {
            temp.delete();
        }
    }
}

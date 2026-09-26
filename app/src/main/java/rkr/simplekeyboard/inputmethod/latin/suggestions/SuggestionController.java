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

package rkr.simplekeyboard.inputmethod.latin.suggestions;

import android.content.Context;
import android.os.UserManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads the dictionary and learned words for the current language and answers suggestion
 * queries. Everything stays on the device: dictionaries come from the app's assets or from files
 * the user imported, and learned words are stored in the app's private storage.
 *
 * The keyboard can run before the device is first unlocked. Learned words live in credential
 * protected storage, so until then nothing is learned, loaded or saved. Imported dictionaries are
 * not personal and live in device protected storage so they work on the lock screen too.
 */
public final class SuggestionController {
    private static final String TAG = SuggestionController.class.getSimpleName();

    public static final int MAX_SUGGESTIONS = 3;

    private static final String ASSET_DICTIONARY_DIR = "dicts";
    private static final String IMPORTED_DICTIONARY_DIR = "dicts";
    private static final String DICTIONARY_EXTENSION = ".dict";
    private static final String HISTORY_DIR = "history";
    private static final String HISTORY_EXTENSION = ".txt";

    // Bumped by the settings screen so a running keyboard notices the change.
    private static volatile int sDictionaryGeneration;
    private static volatile int sHistoryGeneration;

    private final Context mContext;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Runnable mOnLoaded;

    private volatile Suggest mSuggest;
    private volatile File mHistoryFile;
    private volatile String mLocaleString;
    private int mDictionaryGeneration = -1;
    private int mHistoryGeneration;
    private boolean mUserUnlocked;

    /**
     * @param context the context used to open assets and files.
     * @param onLoaded run (on a background thread) after a dictionary has finished loading.
     */
    public SuggestionController(final Context context, final Runnable onLoaded) {
        mContext = context.getApplicationContext();
        mOnLoaded = onLoaded;
    }

    /**
     * Makes sure the dictionary for this language is loaded, loading it in the background if
     * needed. Call from the main thread.
     */
    public void setLocale(final String localeString, final Locale locale) {
        final boolean userUnlocked = isUserUnlocked();
        if (localeString.equals(mLocaleString) && mDictionaryGeneration == sDictionaryGeneration
                && userUnlocked == mUserUnlocked) {
            return;
        }
        saveHistory();
        mUserUnlocked = userUnlocked;
        mLocaleString = localeString;
        mDictionaryGeneration = sDictionaryGeneration;
        mHistoryGeneration = sHistoryGeneration;
        mSuggest = null;
        mHistoryFile = null;
        mExecutor.execute(() -> {
            final Dictionary dictionary = loadDictionary(localeString, locale);
            final File historyFile = userUnlocked ? getHistoryFile(mContext, localeString) : null;
            final UserHistory history = new UserHistory(locale);
            if (historyFile != null) {
                history.load(historyFile);
            }
            if (!localeString.equals(mLocaleString)) {
                // The language changed again while loading.
                return;
            }
            mHistoryFile = historyFile;
            mSuggest = new Suggest(dictionary, history);
            mOnLoaded.run();
        });
    }

    private boolean isUserUnlocked() {
        final UserManager userManager = mContext.getSystemService(UserManager.class);
        return userManager == null || userManager.isUserUnlocked();
    }

    private Dictionary loadDictionary(final String localeString, final Locale locale) {
        final String language = locale.getLanguage();
        for (final String name : new String[] { localeString, language }) {
            final File imported = getImportedDictionaryFile(mContext, name);
            if (imported.exists()) {
                try (InputStream in = new FileInputStream(imported)) {
                    return Dictionary.read(in, locale);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to read imported dictionary " + imported, e);
                }
            }
        }
        for (final String name : new String[] { localeString, language }) {
            try (InputStream in = mContext.getAssets().open(
                    ASSET_DICTIONARY_DIR + "/" + name + DICTIONARY_EXTENSION)) {
                return Dictionary.read(in, locale);
            } catch (IOException e) {
                // No bundled dictionary with this name.
            }
        }
        Log.i(TAG, "No dictionary for " + localeString + ", using learned words only");
        return Dictionary.empty(locale);
    }

    private Suggest getSuggest() {
        final Suggest suggest = mSuggest;
        if (suggest != null && mHistoryGeneration != sHistoryGeneration) {
            mHistoryGeneration = sHistoryGeneration;
            suggest.getUserHistory().clear();
        }
        return suggest;
    }

    /**
     * Returns suggestions for the word being typed, best first. Call from the main thread.
     */
    public List<String> getSuggestions(final WordUtils.WordContext context,
            final boolean capitalizeFirst) {
        final Suggest suggest = getSuggest();
        if (suggest == null || context == null) {
            return Collections.emptyList();
        }
        return suggest.getSuggestions(context.mPreviousWord, context.mPartialWord,
                capitalizeFirst, MAX_SUGGESTIONS);
    }

    /**
     * Remembers a word the user typed, and the word before it. Call from the main thread.
     */
    public void learn(final String previousWord, final String word) {
        final Suggest suggest = getSuggest();
        if (suggest != null && mHistoryFile != null) {
            suggest.learn(previousWord, word);
        }
    }

    /**
     * Writes learned words to storage in the background, if anything changed.
     */
    public void saveHistory() {
        final Suggest suggest = getSuggest();
        final File file = mHistoryFile;
        if (suggest == null || file == null || !suggest.getUserHistory().isDirty()) {
            return;
        }
        mExecutor.execute(() -> {
            try {
                suggest.getUserHistory().save(file);
            } catch (IOException e) {
                Log.e(TAG, "Unable to save learned words", e);
            }
        });
    }

    private static File getHistoryFile(final Context context, final String localeString) {
        return new File(new File(context.getFilesDir(), HISTORY_DIR),
                localeString + HISTORY_EXTENSION);
    }

    private static File getImportedDictionaryDir(final Context context) {
        return new File(context.createDeviceProtectedStorageContext().getFilesDir(),
                IMPORTED_DICTIONARY_DIR);
    }

    public static File getImportedDictionaryFile(final Context context,
            final String localeString) {
        return new File(getImportedDictionaryDir(context), localeString + DICTIONARY_EXTENSION);
    }

    private static boolean deleteFiles(final File dir) {
        boolean deleted = false;
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                deleted |= file.delete();
            }
        }
        return deleted;
    }

    public static boolean hasImportedDictionaries(final Context context) {
        final File[] files = getImportedDictionaryDir(context).listFiles();
        return files != null && files.length > 0;
    }

    /**
     * Tells a running keyboard to reload its dictionary, after one was imported or removed.
     */
    public static void onDictionariesChanged() {
        sDictionaryGeneration++;
    }

    public static void removeImportedDictionaries(final Context context) {
        deleteFiles(getImportedDictionaryDir(context));
        onDictionariesChanged();
    }

    /**
     * Deletes all learned words, for every language.
     */
    public static void clearLearnedWords(final Context context) {
        sHistoryGeneration++;
        deleteFiles(new File(context.getFilesDir(), HISTORY_DIR));
    }
}

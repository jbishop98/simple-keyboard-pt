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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Combines the bundled dictionary with the user's own history into a short list of suggestions.
 */
public final class Suggest {
    /** How many dictionary matches to consider before ranking. */
    private static final int DICTIONARY_CANDIDATES = 20;
    /** Words not in the dictionary must be typed this often before they are suggested. */
    static final int MIN_COUNT_FOR_UNKNOWN_WORD = 2;

    private final Dictionary mDictionary;
    private final UserHistory mUserHistory;
    private final Locale mLocale;

    public Suggest(final Dictionary dictionary, final UserHistory userHistory) {
        mDictionary = dictionary;
        mUserHistory = userHistory;
        mLocale = dictionary.getLocale();
    }

    public Locale getLocale() {
        return mLocale;
    }

    public UserHistory getUserHistory() {
        return mUserHistory;
    }

    public boolean hasDictionary() {
        return !mDictionary.isEmpty();
    }

    /** Score for a learned word that is not in the dictionary. */
    private static int historyScore(final int count) {
        return Math.min(Dictionary.MAX_FREQUENCY, 140 + 20 * count);
    }

    /** Extra score for a dictionary word the user has typed before. */
    private static int historyBonus(final int count) {
        return Math.min(60, 15 * count);
    }

    private static int pairBonus(final int count) {
        return count > 0 ? 60 + 20 * Math.min(count, 5) : 0;
    }

    private boolean isKnownWord(final String word, final int historyCount) {
        return historyCount >= MIN_COUNT_FOR_UNKNOWN_WORD || mDictionary.contains(word);
    }

    /**
     * Returns up to {@code maxResults} suggestions, best first.
     *
     * @param previousWord the word before the one being typed, or null.
     * @param typed the letters typed so far. When empty, next-word predictions are returned.
     * @param capitalizeFirst whether to capitalize suggestions when nothing has been typed.
     */
    public List<String> getSuggestions(final String previousWord, final String typed,
            final boolean capitalizeFirst, final int maxResults) {
        final HashMap<String, Integer> scores = new HashMap<>();
        if (typed.isEmpty()) {
            if (previousWord != null) {
                for (final Map.Entry<String, Integer> entry
                        : mUserHistory.getNextWords(previousWord).entrySet()) {
                    final String word = entry.getKey();
                    if (isKnownWord(word, mUserHistory.getWordCount(word))) {
                        scores.put(word, entry.getValue());
                    }
                }
            }
        } else {
            final String prefix = WordUtils.fold(typed, mLocale);
            final List<String> words = new ArrayList<>();
            final List<Integer> frequencies = new ArrayList<>();
            mDictionary.getCompletions(prefix, DICTIONARY_CANDIDATES, words, frequencies);
            for (int i = 0; i < words.size(); i++) {
                scores.put(words.get(i), frequencies.get(i));
            }
            final HashMap<String, Integer> history = new HashMap<>();
            mUserHistory.getCompletions(prefix, history);
            for (final Map.Entry<String, Integer> entry : history.entrySet()) {
                final String word = entry.getKey();
                final int count = entry.getValue();
                final Integer dictionaryScore = scores.get(word);
                if (dictionaryScore != null) {
                    scores.put(word, dictionaryScore + historyBonus(count));
                } else if (isKnownWord(word, count)) {
                    final int frequency = mDictionary.getFrequency(word);
                    scores.put(word, frequency > 0 ? frequency + historyBonus(count)
                            : historyScore(count));
                }
            }
            for (final Map.Entry<String, Integer> entry : scores.entrySet()) {
                entry.setValue(entry.getValue()
                        + pairBonus(mUserHistory.getPairCount(previousWord, entry.getKey())));
            }
        }

        final List<String> ranked = rankByScore(scores);
        final List<String> results = new ArrayList<>(maxResults);
        for (final String word : ranked) {
            if (results.size() >= maxResults) {
                break;
            }
            // Offering exactly what was typed is of no use.
            if (word.equalsIgnoreCase(typed)) {
                continue;
            }
            final String shown = WordUtils.applyCase(word, typed, capitalizeFirst, mLocale);
            if (!results.contains(shown)) {
                results.add(shown);
            }
        }
        return results;
    }

    private static List<String> rankByScore(final Map<String, Integer> scores) {
        final List<String> words = new ArrayList<>(scores.keySet());
        words.sort((a, b) -> {
            final int byScore = Integer.compare(scores.get(b), scores.get(a));
            return byScore != 0 ? byScore : a.compareTo(b);
        });
        return words;
    }

    /**
     * Records a word the user finished typing (or picked), and the word before it.
     */
    public void learn(final String previousWord, final String word) {
        if (!WordUtils.isLearnableWord(word)) {
            return;
        }
        mUserHistory.addWord(WordUtils.isLearnableWord(previousWord) ? previousWord : null,
                normalizeCase(word));
    }

    /**
     * Stores words in their usual form, so "Hello" at the start of a sentence is remembered as
     * "hello" if that is how the dictionary (or the user) normally writes it.
     */
    private String normalizeCase(final String word) {
        if (mDictionary.contains(word)) {
            return word;
        }
        final String lower = word.toLowerCase(mLocale);
        if (!lower.equals(word) && (mDictionary.contains(lower)
                || mUserHistory.getWordCount(lower) > 0)) {
            return lower;
        }
        return word;
    }
}

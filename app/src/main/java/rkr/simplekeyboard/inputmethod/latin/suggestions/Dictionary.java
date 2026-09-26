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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * A read-only word list with frequencies, searchable by prefix.
 *
 * Accepted file formats (plain text or gzip, UTF-8, one entry per line):
 * <ul>
 * <li>{@code word<TAB>frequency} or {@code word frequency} (frequency 0-255)</li>
 * <li>{@code word} on its own (frequency is then based on the line order)</li>
 * <li>AOSP LatinIME wordlists ({@code " word=hello,f=120,..."})</li>
 * </ul>
 * Lines starting with {@code #} are ignored.
 */
public final class Dictionary {
    public static final int MAX_WORDS = 300000;
    public static final int MAX_FREQUENCY = 255;

    private final Locale mLocale;
    /** Folded (lower case, accent free) words, sorted. */
    private final String[] mKeys;
    /** The words as they should be shown, in the same order as mKeys. */
    private final String[] mWords;
    private final byte[] mFrequencies;

    private Dictionary(final Locale locale, final String[] keys, final String[] words,
            final byte[] frequencies) {
        mLocale = locale;
        mKeys = keys;
        mWords = words;
        mFrequencies = frequencies;
    }

    public static Dictionary empty(final Locale locale) {
        return new Dictionary(locale, new String[0], new String[0], new byte[0]);
    }

    public Locale getLocale() {
        return mLocale;
    }

    public int size() {
        return mWords.length;
    }

    public boolean isEmpty() {
        return mWords.length == 0;
    }

    /**
     * Reads a dictionary. The stream is closed when done.
     */
    public static Dictionary read(final InputStream inputStream, final Locale locale)
            throws IOException {
        final InputStream in = maybeUngzip(inputStream);
        final HashMap<String, Integer> entries = new HashMap<>();
        final List<String> order = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null && order.size() < MAX_WORDS) {
                lineNumber++;
                final String word;
                int frequency;
                if (line.startsWith(" word=")) {
                    if (line.contains("possibly_offensive") || line.contains("not_a_word=true")) {
                        continue;
                    }
                    word = getAospField(line, "word");
                    frequency = parseInt(getAospField(line, "f"), -1);
                } else {
                    final String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || line.startsWith(" ")
                            || trimmed.startsWith("dictionary=")) {
                        continue;
                    }
                    final String[] parts = trimmed.split("[\\t ]+");
                    word = parts[0];
                    frequency = parts.length > 1 ? parseInt(parts[1], -1) : -1;
                    if (frequency < 0) {
                        // Plain word lists are usually ordered from most to least common.
                        frequency = Math.max(1, MAX_FREQUENCY - lineNumber / 400);
                    }
                }
                if (word == null || word.isEmpty() || word.length() > WordUtils.MAX_WORD_LENGTH
                        || frequency <= 0) {
                    continue;
                }
                frequency = Math.min(frequency, MAX_FREQUENCY);
                final Integer previous = entries.get(word);
                if (previous == null) {
                    order.add(word);
                    entries.put(word, frequency);
                } else if (previous < frequency) {
                    entries.put(word, frequency);
                }
            }
        }
        return build(locale, order, entries);
    }

    private static InputStream maybeUngzip(final InputStream inputStream) throws IOException {
        final BufferedInputStream in = new BufferedInputStream(inputStream);
        in.mark(2);
        final int b1 = in.read();
        final int b2 = in.read();
        in.reset();
        if (b1 == 0x1f && b2 == 0x8b) {
            return new GZIPInputStream(in);
        }
        return in;
    }

    private static String getAospField(final String line, final String name) {
        final String prefix = name + "=";
        for (final String part : line.trim().split(",")) {
            if (part.startsWith(prefix)) {
                return part.substring(prefix.length());
            }
        }
        return null;
    }

    private static int parseInt(final String value, final int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static Dictionary build(final Locale locale, final List<String> words,
            final HashMap<String, Integer> frequencies) {
        final int count = words.size();
        final String[] keys = new String[count];
        final Integer[] indices = new Integer[count];
        for (int i = 0; i < count; i++) {
            keys[i] = WordUtils.fold(words.get(i), locale);
            indices[i] = i;
        }
        Arrays.sort(indices, (a, b) -> keys[a].compareTo(keys[b]));
        final String[] sortedKeys = new String[count];
        final String[] sortedWords = new String[count];
        final byte[] sortedFrequencies = new byte[count];
        for (int i = 0; i < count; i++) {
            final int index = indices[i];
            final String word = words.get(index);
            sortedWords[i] = word;
            // Share the string when the key is the same as the word, which is the common case.
            sortedKeys[i] = keys[index].equals(word) ? word : keys[index];
            sortedFrequencies[i] = (byte) (int) frequencies.get(word);
        }
        return new Dictionary(locale, sortedKeys, sortedWords, sortedFrequencies);
    }

    public static Dictionary fromWords(final Locale locale, final String[] words,
            final int[] frequencies) {
        final List<String> order = new ArrayList<>();
        final HashMap<String, Integer> map = new HashMap<>();
        for (int i = 0; i < words.length; i++) {
            if (!map.containsKey(words[i])) {
                order.add(words[i]);
            }
            map.put(words[i], Math.min(frequencies[i], MAX_FREQUENCY));
        }
        return build(locale, order, map);
    }

    private int getFrequencyAt(final int index) {
        return mFrequencies[index] & 0xFF;
    }

    private int lowerBound(final String key) {
        int low = 0;
        int high = mKeys.length;
        while (low < high) {
            final int mid = (low + high) >>> 1;
            if (mKeys[mid].compareTo(key) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    /**
     * Returns the frequency of a word (exact spelling), or 0 if it is not in the dictionary.
     */
    public int getFrequency(final String word) {
        final String key = WordUtils.fold(word, mLocale);
        for (int i = lowerBound(key); i < mKeys.length && mKeys[i].equals(key); i++) {
            if (mWords[i].equals(word)) {
                return getFrequencyAt(i);
            }
        }
        return 0;
    }

    public boolean contains(final String word) {
        return getFrequency(word) > 0;
    }

    /**
     * Collects the most frequent words starting with a prefix.
     *
     * @param foldedPrefix the prefix, already passed through {@link WordUtils#fold}.
     * @param maxResults the maximum number of words to return.
     * @param outWords receives the words, most frequent first.
     * @param outFrequencies receives the matching frequencies.
     */
    public void getCompletions(final String foldedPrefix, final int maxResults,
            final List<String> outWords, final List<Integer> outFrequencies) {
        if (foldedPrefix.isEmpty() || maxResults <= 0) {
            return;
        }
        // Keep the best maxResults matches in a small array sorted by frequency.
        final int[] best = new int[maxResults];
        int bestCount = 0;
        for (int i = lowerBound(foldedPrefix);
                i < mKeys.length && mKeys[i].startsWith(foldedPrefix); i++) {
            final int frequency = getFrequencyAt(i);
            if (bestCount == maxResults && frequency <= getFrequencyAt(best[bestCount - 1])) {
                continue;
            }
            int position = bestCount < maxResults ? bestCount++ : maxResults - 1;
            while (position > 0 && getFrequencyAt(best[position - 1]) < frequency) {
                best[position] = best[position - 1];
                position--;
            }
            best[position] = i;
        }
        for (int i = 0; i < bestCount; i++) {
            outWords.add(mWords[best[i]]);
            outFrequencies.add(getFrequencyAt(best[i]));
        }
    }
}

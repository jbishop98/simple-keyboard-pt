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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Words and word pairs the user has typed, with how often. Kept only on the device.
 *
 * All methods are thread safe.
 */
public final class UserHistory {
    static final int MAX_WORDS = 5000;
    static final int MAX_PAIRS = 10000;

    private static final char KEY_SEPARATOR = '\u0000';
    private static final String FILE_HEADER = "# simple-keyboard user history v1";

    private final Locale mLocale;
    /** Keyed by folded word + KEY_SEPARATOR + word, so prefix searches work on folded text. */
    private final TreeMap<String, Integer> mWords = new TreeMap<>();
    /** Previous word (lower case) to the words that followed it, with counts. */
    private final HashMap<String, HashMap<String, Integer>> mPairs = new HashMap<>();
    private int mPairCount;
    private boolean mDirty;

    public UserHistory(final Locale locale) {
        mLocale = locale;
    }

    private String wordKey(final String word) {
        return WordUtils.fold(word, mLocale) + KEY_SEPARATOR + word;
    }

    private String pairKey(final String previousWord) {
        return previousWord.toLowerCase(mLocale);
    }

    public synchronized void addWord(final String previousWord, final String word) {
        final String key = wordKey(word);
        final Integer count = mWords.get(key);
        mWords.put(key, count == null ? 1 : count + 1);
        if (previousWord != null) {
            HashMap<String, Integer> next = mPairs.get(pairKey(previousWord));
            if (next == null) {
                next = new HashMap<>();
                mPairs.put(pairKey(previousWord), next);
            }
            final Integer pairCount = next.get(word);
            if (pairCount == null) {
                mPairCount++;
            }
            next.put(word, pairCount == null ? 1 : pairCount + 1);
        }
        mDirty = true;
        trim();
    }

    /** Halves all counts, dropping entries that reach zero, until the size limits are met. */
    private void trim() {
        while (mWords.size() > MAX_WORDS) {
            final Iterator<Map.Entry<String, Integer>> it = mWords.entrySet().iterator();
            while (it.hasNext()) {
                final Map.Entry<String, Integer> entry = it.next();
                final int count = entry.getValue() / 2;
                if (count == 0) {
                    it.remove();
                } else {
                    entry.setValue(count);
                }
            }
        }
        while (mPairCount > MAX_PAIRS) {
            final Iterator<HashMap<String, Integer>> outer = mPairs.values().iterator();
            while (outer.hasNext()) {
                final HashMap<String, Integer> next = outer.next();
                final Iterator<Map.Entry<String, Integer>> it = next.entrySet().iterator();
                while (it.hasNext()) {
                    final Map.Entry<String, Integer> entry = it.next();
                    final int count = entry.getValue() / 2;
                    if (count == 0) {
                        it.remove();
                        mPairCount--;
                    } else {
                        entry.setValue(count);
                    }
                }
                if (next.isEmpty()) {
                    outer.remove();
                }
            }
        }
    }

    public synchronized int getWordCount(final String word) {
        final Integer count = mWords.get(wordKey(word));
        return count == null ? 0 : count;
    }

    public synchronized int getPairCount(final String previousWord, final String word) {
        if (previousWord == null) {
            return 0;
        }
        final HashMap<String, Integer> next = mPairs.get(pairKey(previousWord));
        if (next == null) {
            return 0;
        }
        final Integer count = next.get(word);
        return count == null ? 0 : count;
    }

    /**
     * Adds every learned word starting with the prefix, and its count, to {@code out}.
     */
    public synchronized void getCompletions(final String foldedPrefix,
            final Map<String, Integer> out) {
        if (foldedPrefix.isEmpty()) {
            return;
        }
        final SortedMap<String, Integer> range =
                mWords.subMap(foldedPrefix, foldedPrefix + Character.MAX_VALUE);
        for (final Map.Entry<String, Integer> entry : range.entrySet()) {
            final String key = entry.getKey();
            out.put(key.substring(key.indexOf(KEY_SEPARATOR) + 1), entry.getValue());
        }
    }

    /**
     * Returns the words that have followed {@code previousWord}, with counts.
     */
    public synchronized Map<String, Integer> getNextWords(final String previousWord) {
        final HashMap<String, Integer> next = mPairs.get(pairKey(previousWord));
        return next == null ? new HashMap<String, Integer>() : new HashMap<>(next);
    }

    public synchronized boolean isEmpty() {
        return mWords.isEmpty() && mPairs.isEmpty();
    }

    public synchronized boolean isDirty() {
        return mDirty;
    }

    public synchronized void clear() {
        mWords.clear();
        mPairs.clear();
        mPairCount = 0;
        mDirty = true;
    }

    public synchronized void write(final Writer writer) throws IOException {
        writer.write(FILE_HEADER);
        writer.write('\n');
        for (final Map.Entry<String, Integer> entry : mWords.entrySet()) {
            final String key = entry.getKey();
            writer.write("w\t" + key.substring(key.indexOf(KEY_SEPARATOR) + 1)
                    + "\t" + entry.getValue() + "\n");
        }
        for (final Map.Entry<String, HashMap<String, Integer>> pairs : mPairs.entrySet()) {
            for (final Map.Entry<String, Integer> entry : pairs.getValue().entrySet()) {
                writer.write("p\t" + pairs.getKey() + "\t" + entry.getKey()
                        + "\t" + entry.getValue() + "\n");
            }
        }
        mDirty = false;
    }

    public synchronized void read(final Reader reader) throws IOException {
        final BufferedReader in = new BufferedReader(reader);
        String line;
        while ((line = in.readLine()) != null) {
            final String[] parts = line.split("\t");
            try {
                if (parts.length == 3 && parts[0].equals("w")) {
                    final int count = Integer.parseInt(parts[2]);
                    if (count > 0 && !parts[1].isEmpty()) {
                        mWords.put(wordKey(parts[1]), count);
                    }
                } else if (parts.length == 4 && parts[0].equals("p")) {
                    final int count = Integer.parseInt(parts[3]);
                    if (count > 0 && !parts[1].isEmpty() && !parts[2].isEmpty()) {
                        HashMap<String, Integer> next = mPairs.get(parts[1]);
                        if (next == null) {
                            next = new HashMap<>();
                            mPairs.put(parts[1], next);
                        }
                        if (next.put(parts[2], count) == null) {
                            mPairCount++;
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // Skip damaged lines.
            }
        }
        trim();
    }

    public void load(final File file) {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file),
                StandardCharsets.UTF_8)) {
            read(reader);
        } catch (IOException e) {
            // A damaged history is not worth crashing the keyboard over; start fresh.
            clear();
        }
    }

    /**
     * Writes the history to a file, replacing it atomically.
     */
    public void save(final File file) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create " + parent);
        }
        final File temp = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp),
                StandardCharsets.UTF_8)) {
            write(writer);
        }
        if (!temp.renameTo(file)) {
            throw new IOException("Unable to replace " + file);
        }
    }
}

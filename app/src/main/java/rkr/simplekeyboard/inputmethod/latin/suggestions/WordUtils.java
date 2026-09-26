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

import java.text.Normalizer;
import java.util.Locale;

/**
 * Text helpers for word suggestions. Pure Java so it can be unit tested without Android.
 */
public final class WordUtils {
    public static final int MAX_WORD_LENGTH = 48;

    private static final String OPENING_PUNCTUATION = "([{\"\u201C\u2018\u00BF\u00A1\u00AB";

    private WordUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * The word being typed and the word before it, as found in the text before the cursor.
     */
    public static final class WordContext {
        /** The partial word right before the cursor. Empty when the cursor follows a space. */
        public final String mPartialWord;
        /** The previous word in the same sentence, or null if there is none. */
        public final String mPreviousWord;

        public WordContext(final String partialWord, final String previousWord) {
            mPartialWord = partialWord;
            mPreviousWord = previousWord;
        }
    }

    public static boolean isApostrophe(final int codePoint) {
        return codePoint == '\'' || codePoint == '\u2019';
    }

    public static boolean isWordCodePoint(final int codePoint) {
        if (Character.isLetter(codePoint) || isApostrophe(codePoint)) {
            return true;
        }
        final int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean isWordStartBoundary(final int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == '\u00A0'
                || isApostrophe(codePoint) || OPENING_PUNCTUATION.indexOf(codePoint) >= 0;
    }

    /** Returns the start index of the word ending at {@code end}, skipping leading apostrophes. */
    private static int findWordStart(final CharSequence text, final int end) {
        int start = end;
        while (start > 0) {
            final int codePoint = Character.codePointBefore(text, start);
            if (!isWordCodePoint(codePoint)) {
                break;
            }
            start -= Character.charCount(codePoint);
        }
        while (start < end && isApostrophe(text.charAt(start))) {
            start++;
        }
        return start;
    }

    /**
     * Finds the word being typed and the previous word.
     *
     * @param textBeforeCursor the text before the cursor.
     * @param textAfterCursor the text after the cursor.
     * @return the context, or null if the cursor is somewhere suggestions make no sense, such as
     * in the middle of a word, a number, an email address or a URL.
     */
    public static WordContext getWordContext(final CharSequence textBeforeCursor,
            final CharSequence textAfterCursor) {
        if (textAfterCursor != null && textAfterCursor.length() > 0
                && isWordCodePoint(Character.codePointAt(textAfterCursor, 0))) {
            return null;
        }
        final CharSequence text = textBeforeCursor == null ? "" : textBeforeCursor;
        final int end = text.length();
        final int start = findWordStart(text, end);
        // Anything other than a space or an opening bracket/quote glued to the word means it is
        // part of something else, like "user@exam" or "3rd".
        if (start > 0 && !isWordStartBoundary(Character.codePointBefore(text, start))) {
            if (start == end) {
                return new WordContext("", null);
            }
            return null;
        }
        final String partialWord = text.subSequence(start, end).toString();
        if (partialWord.length() > MAX_WORD_LENGTH) {
            return null;
        }

        // Only spaces may separate the previous word from the current one. Punctuation such as a
        // full stop or a comma breaks the link between them.
        int prevEnd = start;
        while (prevEnd > 0 && Character.isWhitespace(text.charAt(prevEnd - 1))
                && text.charAt(prevEnd - 1) != '\n') {
            prevEnd--;
        }
        String previousWord = null;
        if (prevEnd < start && prevEnd > 0) {
            final int prevStart = findWordStart(text, prevEnd);
            final boolean prevIsWholeWord = prevStart < prevEnd && (prevStart == 0
                    || isWordStartBoundary(Character.codePointBefore(text, prevStart)));
            if (prevIsWholeWord && prevEnd == trimTrailingApostrophes(text, prevStart, prevEnd)) {
                previousWord = text.subSequence(prevStart, prevEnd).toString();
            }
        }
        return new WordContext(partialWord, previousWord);
    }

    private static int trimTrailingApostrophes(final CharSequence text, final int start, int end) {
        while (end > start && isApostrophe(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    /**
     * Returns a key used to match words regardless of case, accents and apostrophes, so that
     * typing "cafe" or "dont" finds "caf\u00E9" and "don't".
     */
    public static String fold(final String word, final Locale locale) {
        final String lower = word.toLowerCase(locale);
        boolean plain = true;
        for (int i = 0; i < lower.length(); i++) {
            final char c = lower.charAt(i);
            if (c >= 0x80 || c == '\'') {
                plain = false;
                break;
            }
        }
        if (plain) {
            return lower;
        }
        final String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        final StringBuilder sb = new StringBuilder(decomposed.length());
        for (int i = 0; i < decomposed.length(); i++) {
            final char c = decomposed.charAt(i);
            // Drop accents from Latin-script letters only; for other scripts marks are meaningful.
            if (isApostrophe(c) || (c >= 0x0300 && c <= 0x036F)) {
                continue;
            }
            sb.append(c);
        }
        return Normalizer.normalize(sb, Normalizer.Form.NFC);
    }

    private static boolean hasLetters(final String word) {
        for (int i = 0; i < word.length(); ) {
            final int codePoint = word.codePointAt(i);
            if (Character.isLetter(codePoint)) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    public static boolean isAllUpperCase(final String word, final Locale locale) {
        return hasLetters(word) && word.equals(word.toUpperCase(locale))
                && !word.equals(word.toLowerCase(locale));
    }

    public static boolean startsWithUpperCase(final String word) {
        return !word.isEmpty() && Character.isUpperCase(word.codePointAt(0));
    }

    public static String capitalizeFirst(final String word, final Locale locale) {
        if (word.isEmpty()) {
            return word;
        }
        final int firstLength = Character.charCount(word.codePointAt(0));
        return word.substring(0, firstLength).toUpperCase(locale) + word.substring(firstLength);
    }

    /**
     * Matches the capitalization of a suggestion to what the user has typed so far.
     *
     * @param suggestion the word as stored in the dictionary.
     * @param typed the letters typed so far. May be empty.
     * @param capitalizeFirst whether the keyboard is in shifted/auto-caps mode (used when nothing
     * has been typed yet).
     */
    public static String applyCase(final String suggestion, final String typed,
            final boolean capitalizeFirst, final Locale locale) {
        if (typed.length() > 1 && isAllUpperCase(typed, locale)) {
            return suggestion.toUpperCase(locale);
        }
        if (typed.isEmpty() ? capitalizeFirst : startsWithUpperCase(typed)) {
            return capitalizeFirst(suggestion, locale);
        }
        return suggestion;
    }

    /**
     * Whether a finished word is worth learning. Skips very short or long tokens and anything
     * without letters.
     */
    public static boolean isLearnableWord(final String word) {
        return word != null && word.length() >= 2 && word.length() <= MAX_WORD_LENGTH
                && hasLetters(word);
    }
}

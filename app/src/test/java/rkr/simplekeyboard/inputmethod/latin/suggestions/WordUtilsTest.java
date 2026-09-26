package rkr.simplekeyboard.inputmethod.latin.suggestions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

public class WordUtilsTest {
    private static final Locale EN = Locale.ENGLISH;

    @Test
    public void partialWordAndPreviousWord() {
        final WordUtils.WordContext context = WordUtils.getWordContext("I said hel", "");
        assertEquals("hel", context.mPartialWord);
        assertEquals("said", context.mPreviousWord);
    }

    @Test
    public void emptyPartialAfterSpace() {
        final WordUtils.WordContext context = WordUtils.getWordContext("thank ", "");
        assertEquals("", context.mPartialWord);
        assertEquals("thank", context.mPreviousWord);
    }

    @Test
    public void punctuationBreaksPreviousWord() {
        assertNull(WordUtils.getWordContext("Done. Ne", "").mPreviousWord);
        assertNull(WordUtils.getWordContext("one, two", "").mPreviousWord);
        assertNull(WordUtils.getWordContext("line\nnext", "").mPreviousWord);
    }

    @Test
    public void apostrophesAndQuotes() {
        assertEquals("don't", WordUtils.getWordContext("I don't", "").mPartialWord);
        assertEquals("quo", WordUtils.getWordContext("say \"quo", "").mPartialWord);
        assertEquals("quo", WordUtils.getWordContext("say 'quo", "").mPartialWord);
    }

    @Test
    public void noSuggestionsInsideOtherTokens() {
        assertNull(WordUtils.getWordContext("mail me@exa", ""));
        assertNull(WordUtils.getWordContext("the 3rd", ""));
        assertNull(WordUtils.getWordContext("hel", "lo"));
    }

    @Test
    public void startOfText() {
        final WordUtils.WordContext context = WordUtils.getWordContext("", "");
        assertEquals("", context.mPartialWord);
        assertNull(context.mPreviousWord);
    }

    @Test
    public void foldIgnoresCaseAndAccents() {
        assertEquals("cafe", WordUtils.fold("Caf\u00E9", Locale.FRENCH));
        assertEquals("dont", WordUtils.fold("don't", EN));
        assertEquals("dont", WordUtils.fold("DON\u2019T", EN));
    }

    @Test
    public void applyCaseFollowsTypedText() {
        assertEquals("hello", WordUtils.applyCase("hello", "he", false, EN));
        assertEquals("Hello", WordUtils.applyCase("hello", "He", false, EN));
        assertEquals("HELLO", WordUtils.applyCase("hello", "HE", false, EN));
        assertEquals("Hello", WordUtils.applyCase("hello", "H", false, EN));
        assertEquals("Hello", WordUtils.applyCase("hello", "", true, EN));
        assertEquals("Paris", WordUtils.applyCase("Paris", "pa", false, EN));
    }

    @Test
    public void learnableWords() {
        assertTrue(WordUtils.isLearnableWord("hello"));
        assertFalse(WordUtils.isLearnableWord("a"));
        assertFalse(WordUtils.isLearnableWord(null));
        assertFalse(WordUtils.isLearnableWord("''"));
    }
}

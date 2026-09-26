package rkr.simplekeyboard.inputmethod.latin.suggestions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

import org.junit.Test;

public class DictionaryTest {
    private static final Locale EN = Locale.ENGLISH;

    private static Dictionary read(final String text) throws IOException {
        return Dictionary.read(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), EN);
    }

    private static List<String> complete(final Dictionary dictionary, final String prefix,
            final int max) {
        final List<String> words = new ArrayList<>();
        dictionary.getCompletions(WordUtils.fold(prefix, EN), max, words, new ArrayList<>());
        return words;
    }

    @Test
    public void tabSeparatedFormat() throws IOException {
        final Dictionary dictionary = read("hello\t100\nhelp\t150\nheld\t50\nworld\t200\n");
        assertEquals(4, dictionary.size());
        assertEquals(Arrays.asList("help", "hello", "held"), complete(dictionary, "hel", 5));
        assertEquals(Arrays.asList("help", "hello"), complete(dictionary, "hel", 2));
        assertEquals(Arrays.asList("hello"), complete(dictionary, "HELL", 5));
        assertTrue(complete(dictionary, "x", 5).isEmpty());
    }

    @Test
    public void aospFormatSkipsOffensiveWords() throws IOException {
        final Dictionary dictionary = read(
                "dictionary=main:en_us,locale=en_US\n"
                + " word=the,f=222,flags=,originalFreq=222\n"
                + " word=then,f=180,flags=,originalFreq=180\n"
                + "  shortcut=thx,f=14\n"
                + " word=thebad,f=100,flags=,originalFreq=100,possibly_offensive=true\n");
        assertEquals(2, dictionary.size());
        assertEquals(222, dictionary.getFrequency("the"));
        assertFalse(dictionary.contains("thebad"));
    }

    @Test
    public void gzipAndPlainWordList() throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
            gzip.write("# comment\nalpha\nbeta\nalphabet\n".getBytes(StandardCharsets.UTF_8));
        }
        final Dictionary dictionary =
                Dictionary.read(new ByteArrayInputStream(bytes.toByteArray()), EN);
        assertEquals(3, dictionary.size());
        assertEquals(Arrays.asList("alpha", "alphabet"), complete(dictionary, "al", 5));
    }

    @Test
    public void accentsAndCaseInsensitiveMatch() throws IOException {
        final Dictionary dictionary = Dictionary.fromWords(Locale.FRENCH,
                new String[] {"caf\u00E9", "Paris", "cafeti\u00E8re"}, new int[] {100, 90, 80});
        final List<String> words = new ArrayList<>();
        dictionary.getCompletions(WordUtils.fold("caf", Locale.FRENCH), 5, words,
                new ArrayList<>());
        assertEquals(Arrays.asList("caf\u00E9", "cafeti\u00E8re"), words);
        assertTrue(dictionary.contains("Paris"));
        assertFalse(dictionary.contains("paris"));
    }

    @Test
    public void bundledEnglishDictionary() throws IOException {
        // Unit tests run with the module directory as the working directory.
        final Dictionary dictionary = Dictionary.read(
                new FileInputStream("src/main/assets/dicts/en.dict"), Locale.US);
        assertTrue(dictionary.size() >= 40000);
        assertTrue(dictionary.contains("the"));
        assertEquals(Arrays.asList("the", "this", "that"), complete(dictionary, "th", 3));
    }
}

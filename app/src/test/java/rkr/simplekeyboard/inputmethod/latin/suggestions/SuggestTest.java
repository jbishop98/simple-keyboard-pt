package rkr.simplekeyboard.inputmethod.latin.suggestions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SuggestTest {
    private static final Locale EN = Locale.ENGLISH;

    @Rule
    public TemporaryFolder mTemp = new TemporaryFolder();

    private static Suggest newSuggest() {
        final Dictionary dictionary = Dictionary.fromWords(EN,
                new String[] {"the", "then", "there", "they", "thank", "you", "hello", "help",
                        "don't"},
                new int[] {220, 180, 190, 185, 120, 210, 100, 150, 200});
        return new Suggest(dictionary, new UserHistory(EN));
    }

    @Test
    public void completesFromDictionary() {
        final Suggest suggest = newSuggest();
        assertEquals(Arrays.asList("there", "they", "then"),
                suggest.getSuggestions(null, "the", false, 3));
        assertEquals(Arrays.asList("There", "They", "Then"),
                suggest.getSuggestions(null, "The", false, 3));
    }

    @Test
    public void learnedWordsRankHigher() {
        final Suggest suggest = newSuggest();
        suggest.learn(null, "then");
        suggest.learn(null, "then");
        assertEquals("then", suggest.getSuggestions(null, "the", false, 3).get(0));
    }

    @Test
    public void unknownWordsNeedTwoUses() {
        final Suggest suggest = newSuggest();
        suggest.learn(null, "thx");
        assertTrue(suggest.getSuggestions(null, "th", false, 10).indexOf("thx") < 0);
        suggest.learn(null, "thx");
        assertTrue(suggest.getSuggestions(null, "th", false, 10).contains("thx"));
    }

    @Test
    public void nextWordFromLearnedPairs() {
        final Suggest suggest = newSuggest();
        assertTrue(suggest.getSuggestions("thank", "", false, 3).isEmpty());
        suggest.learn("thank", "you");
        assertEquals(Arrays.asList("you"), suggest.getSuggestions("thank", "", false, 3));
        assertEquals(Arrays.asList("You"), suggest.getSuggestions("Thank", "", true, 3));
    }

    @Test
    public void sentenceStartCapitalIsNotStored() {
        final Suggest suggest = newSuggest();
        suggest.learn(null, "Hello");
        assertEquals(1, suggest.getUserHistory().getWordCount("hello"));
        assertEquals(0, suggest.getUserHistory().getWordCount("Hello"));
    }

    @Test
    public void historySurvivesSaveAndLoad() throws IOException {
        final Suggest suggest = newSuggest();
        suggest.learn("thank", "you");
        suggest.learn(null, "Zanzibar");
        final File file = new File(mTemp.getRoot(), "history/en.txt");
        suggest.getUserHistory().save(file);

        final UserHistory loaded = new UserHistory(EN);
        loaded.load(file);
        assertEquals(1, loaded.getWordCount("you"));
        assertEquals(1, loaded.getWordCount("Zanzibar"));
        assertEquals(1, loaded.getPairCount("thank", "you"));
    }

    @Test
    public void historyIsCapped() {
        final UserHistory history = new UserHistory(EN);
        history.addWord(null, "keeper");
        history.addWord(null, "keeper");
        for (int i = 0; i < UserHistory.MAX_WORDS + 10; i++) {
            history.addWord("prev" + i, "word" + i);
        }
        assertTrue(history.getWordCount("keeper") > 0);
        assertTrue(history.getWordCount("word1") == 0);
    }

    @Test
    public void apostropheIsOptional() {
        assertEquals(Arrays.asList("don't"), newSuggest().getSuggestions(null, "dont", false, 3));
    }

    @Test
    public void typedWordIsNotSuggested() {
        final List<String> results = newSuggest().getSuggestions(null, "they", false, 3);
        assertTrue(results.isEmpty());
    }
}

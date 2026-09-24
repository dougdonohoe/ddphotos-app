package com.donohoedigital.ddphotos.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The cases of photogen's {@code TestEscapeSyncText} (ddphotos {@code pkg/photogen/sync_test.go}). */
public class SyncTextTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
        "plain text is unchanged                  | So wide open!    | So wide open!",
        "newlines and tabs collapse to one space  | `one\ntwo\tthree` | one two three",
        "whitespace runs collapse                 | `a    b`         | a b",
        "leading and trailing space is trimmed    | `  hi  `         | hi",
        "html is escaped                          | <b>a & b</b>     | &lt;b&gt;a &amp; b&lt;/b&gt;",
        "ampersand is escaped once                | a & <b>          | a &amp; &lt;b&gt;",
        "double quotes are left alone             | she said \"hi\"  | she said \"hi\"",
        "empty stays empty                        | ``               | ``",
    })
    public void escape(String name, String in, String want) {
        assertEquals(want, SyncText.escape(in));
    }

    @org.junit.jupiter.api.Test
    public void escape_trimsUnicodeSpaceLikeGo() {
        // Go's strings.Fields splits on NBSP (U+00A0) and narrow NBSP (U+202F); String.strip()
        // does not treat them as whitespace, so they must not survive at either end.
        assertEquals("hi there", SyncText.escape("\u00A0hi\u202Fthere\u00A0"));
        assertEquals("hi", SyncText.escape(" \u00A0 hi \u2007"));
    }

    @org.junit.jupiter.api.Test
    public void escape_nullIsEmpty() {
        assertEquals("", SyncText.escape(null));
    }
}

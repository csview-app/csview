package net.kothar.csview.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class CharsetDetectionTest {

    private static String detect(byte[] sample, boolean wholeFile) {
        return CSV.detectCharset(sample, sample.length, wholeFile);
    }

    private static String rows(String row, int count) {
        return (row + "\r\n").repeat(count);
    }

    @Test
    public void plainAsciiIsUtf8() {
        // ICU's detector named this ISO-8859-2.
        byte[] ascii = rows("id,name,value", 50).getBytes(StandardCharsets.US_ASCII);
        assertEquals("UTF-8", detect(ascii, true));
    }

    @Test
    public void utf8TextIsUtf8() {
        byte[] utf8 = rows("1,東京都の商品,café", 50).getBytes(StandardCharsets.UTF_8);
        assertEquals("UTF-8", detect(utf8, true));
    }

    @Test
    public void aCharacterCutOffByTheSampleStillCountsAsUtf8() {
        // The sample is the file's first 10 KB, so it can end part way through a character: here
        // after the first of é's two bytes. ICU alone calls such a sample a single-byte charset.
        byte[] ascii = rows("id,name,value", 50).getBytes(StandardCharsets.US_ASCII);
        byte[] sample = Arrays.copyOf(ascii, ascii.length + 1);
        sample[ascii.length] = "é".getBytes(StandardCharsets.UTF_8)[0];
        assertEquals("UTF-8", detect(sample, false));
    }

    @Test
    public void shiftJisIsLeftToTheDetector() {
        byte[] sjis = rows("1,東京都の商品その1,100円", 50).getBytes(Charset.forName("Shift_JIS"));
        assertEquals("Shift_JIS", detect(sjis, true));
    }

    @Test
    public void latin1TextIsNotUtf8() {
        byte[] latin1 = rows("1,café,naïve résumé,Zürich", 50).getBytes(StandardCharsets.ISO_8859_1);
        assertNotEquals("UTF-8", detect(latin1, true));
    }
}

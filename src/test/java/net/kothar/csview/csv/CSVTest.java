package net.kothar.csview.csv;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.csv.CSVFormat;
import org.junit.Before;
import org.junit.Test;

public class CSVTest {

    private static final String TABS = "foo\t\"bar\"\tbaz\nfizz\tbuzz";
    private static final String TABS_FIRST_EMPTY = "\tfoo\t\"bar\"\tbaz\nfizz\tbuzz";
    private static final String COMMAS = "foo,\"bar\",baz\nfizz,buzz";
    private static final String CUSTOM = "foo-\"bar\"-baz\nfizz-buzz";

    protected int rows;
    protected int cells;
    protected int widestRow;
    private CompletableFuture<Void> future;

    private ScanHandler scanHandler = new ScanHandler() {

        @Override
        public void notifyCompleted() {
            future.complete(null);
        }

        @Override
        public void newRow(long cell, long row, int previousCols, long pos) {
            rows++;
            widestRow = Math.max(widestRow, previousCols);
        }

        @Override
        public void endOfInput(int lastRowCols) {
            widestRow = Math.max(widestRow, lastRowCols);
        }

        @Override
        public void newCell(long cell, long row, int col, long pos) {
            cells++;
        }
    };

    @Before
    public void reset() {
        rows = 0;
        cells = 0;
        widestRow = 0;
        future = new CompletableFuture<>();
    }

    @Test
    public void parses_comma_delimiter_by_default() throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(COMMAS);
        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 5, cells);
    }

    @Test
    public void ignores_tabs_with_default_delimiter()
            throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(TABS);
        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 2, cells);
    }

    @Test
    public void parses_tab_delimiter() throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(TABS);
        csv.setFormat(CSVFormat.DEFAULT.withDelimiter('\t'));

        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 5, cells);
    }

    @Test
    public void ignores_commas_with_tab_delimiter() throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(COMMAS);
        csv.setFormat(CSVFormat.DEFAULT.withDelimiter('\t'));

        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 2, cells);
    }

    @Test
    public void parses_empty_first_col_with_tab_delimiter() throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(TABS_FIRST_EMPTY);
        csv.setFormat(CSVFormat.DEFAULT.withDelimiter('\t'));

        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 6, cells);
    }

    @Test
    public void parses_custom_delimiter() throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(CUSTOM);
        csv.setFormat(CSVFormat.DEFAULT.withDelimiter('-'));

        csv.scan(scanHandler);

        future.get(10, TimeUnit.SECONDS);
        assertEquals("Rows", 2, rows);
        assertEquals("Cells", 5, cells);
    }

    private void scan(String contents) throws InterruptedException, ExecutionException, TimeoutException {
        CSV csv = new CSV();
        csv.setContents(contents);
        csv.scan(scanHandler);
        future.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void a_final_line_break_ends_the_last_row() throws Exception {
        scan("a,b\nc,d\n");
        assertEquals("Rows", 2, rows);
        assertEquals("Widest row", 2, widestRow);
    }

    @Test
    public void a_final_crlf_ends_the_last_row() throws Exception {
        scan("a,b\r\nc,d\r\n");
        assertEquals("Rows", 2, rows);
    }

    @Test
    public void keeps_blank_lines_before_the_end() throws Exception {
        scan("a\n\nb\n");
        assertEquals("Rows", 3, rows);
    }

    @Test
    public void keeps_a_blank_last_line_after_the_final_line_break() throws Exception {
        // The last line break ends the blank row; only the one after it is dropped.
        scan("a\n\n");
        assertEquals("Rows", 2, rows);
    }

    @Test
    public void counts_the_widest_row_when_it_is_last() throws Exception {
        scan("a\nb,c,d\n");
        assertEquals("Widest row", 3, widestRow);
    }

    @Test
    public void counts_the_widest_row_when_it_is_last_without_a_line_break() throws Exception {
        scan("a\nb,c,d");
        assertEquals("Rows", 2, rows);
        assertEquals("Widest row", 3, widestRow);
    }

    // The scanner reads 4 MiB at a time, so a line break that is the last byte of a block cannot
    // see whether anything follows it until the next block arrives.
    private static final int BLOCK = 4 << 20;

    @Test
    public void a_line_break_at_the_end_of_a_block_starts_a_row_when_more_follows() throws Exception {
        scan("x".repeat(BLOCK - 1) + "\ny");
        assertEquals("Rows", 2, rows);
    }

    @Test
    public void a_line_break_at_the_end_of_a_block_that_ends_the_file_starts_no_row() throws Exception {
        scan("x".repeat(BLOCK - 1) + "\n");
        assertEquals("Rows", 1, rows);
        assertEquals("Widest row", 1, widestRow);
    }

}

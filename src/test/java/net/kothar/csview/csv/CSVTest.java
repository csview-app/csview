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
    private CompletableFuture<Void> future;

    private ScanHandler scanHandler = new ScanHandler() {

        @Override
        public void notifyCompleted() {
            future.complete(null);
        }

        @Override
        public void newRow(long cell, long row, int previousCols, long pos) {
            rows++;
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

}

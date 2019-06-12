/*
 * Copyright 2016 Kothar Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.kothar.csview.csv;

import java.io.*;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.graphics.Point;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

import net.kothar.csview.IndexListener;
import net.kothar.csview.ProgressListener;

public class CSV {

    private Logger log = Logger.getLogger(getClass().getName());

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private static final int CELL_INDEX_DISTANCE = 10;

    /**
     * Maps rows onto the cell that row starts at
     */
    private Index rows = new Index();

    /**
     * Maps every Nth cell to its position in the file
     */
    private Index cells = new Index();

    private String contents;
    private RandomAccessFile randomAccessFile;
    private String file;

    private CSVFormat format = CSVFormat.DEFAULT;
    private int maxColumns = -1;

    private boolean disposed = false;
    private String charset = "UTF-8";

    private Cache<Long, List<String>> cellCache;

    private ProgressManager progressManager;
    private long fileLastModified;
    private long fileLastLength;

    public CSV() {
        cellCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .build();

    }

    public void setProgressManger(ProgressManager progressManager) {
        this.progressManager = progressManager;
    }

    public void addRowListener(IndexListener listener) {
        rows.addListener(listener);
    }

    /**
     * Stop any active scan and dispose of any resources this instance may be holding onto
     */
    public synchronized void dispose(DisposeEvent e) {
        disposed = true;
    }

    /**
     * Adds a row to the row index at the given cell
     *
     * @param cell
     */
    public synchronized void addRow(long cell) {
        rows.add(cell);
    }

    /**
     * Sets the in-memory contents of this CSV
     *
     * @param contents
     */
    public synchronized void setContents(String contents) {
        this.contents = contents;
    }

    public void setFile(String file) {
        this.file = file;
        try {
            this.randomAccessFile = new RandomAccessFile(file, "r");

            // Detect charset
            byte[] buffer = new byte[(int) Math.min(1024 * 10, randomAccessFile.length())];
            this.randomAccessFile.read(buffer, 0, buffer.length);
            CharsetMatch charsetMatch = new CharsetDetector()
                    .setText(buffer)
                    .detect();

            if (charsetMatch != null) {
                charset = charsetMatch.getName();
                System.out.println("Matched charset to " + charset);
            }

            // Detect delimiter
            String fileName = new File(file).getName();
            int extensionStart = fileName.lastIndexOf(".");
            if (extensionStart >= 0) {
                switch (fileName.substring(extensionStart)) {
                    case ".tsv":
                        setFormat(CSVFormat.DEFAULT.withDelimiter('\t'));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void scan(ProgressListener listener) {

        // Prepare status
        long total = 0;
        if (randomAccessFile != null) {
            try {
                total = randomAccessFile.length();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            total = contents.length();
        }

        if (total == 0) {
            return;
        }

        // Clear previous index
        rows.clear();
        cells.clear();

        // Perform scan
        IProgressMonitor progressMonitor = progressManager.getProgressMonitor();
        progressMonitor.beginTask("Scanning...", 1000);
        long totalProgress = total;
        scan(new ScanHandler() {
            long lastProgress = 0;

            @Override
            public void newCell(long cell, long row, int col, long pos) {
                if (cell % CELL_INDEX_DISTANCE == 0)
                    addCell(pos);
            }

            @Override
            public void newRow(long cell, long row, int previousCols, long pos) {

                if (previousCols > maxColumns) {
                    maxColumns = previousCols;
                    notifyColumnsChanged(previousCols);
                }

                addRow(cell);

                if (row % 5000 == 0) {
                    int worked = ((int) (((pos - lastProgress) * 1000) / totalProgress));
                    if (worked > 0) {
                        progressMonitor.worked(worked);
                        lastProgress = pos;
                    }
                    listener.changed();
                }
            }

            private void notifyColumnsChanged(int cols) {
                listener.columnsChanged(cols);
            }

            @Override
            public void notifyCompleted() {
                rows.compact();
                cells.compact();
                listener.completed();
                progressMonitor.done();
            }
        });
    }

    public void scan(ScanHandler handler) {
        if (file != null) {
            CompletableFuture.runAsync(() -> scanFile(handler));
        } else if (contents != null) {
            CompletableFuture.runAsync(() -> scanContents(handler));
        }
    }

    private void scanContents(ScanHandler handler) {
        scan(new ByteArrayInputStream(contents.getBytes()), handler);
    }

    private void scanFile(ScanHandler handler) {
        try (FileInputStream input = new FileInputStream(file)) {

            long startTime = System.currentTimeMillis();
            File fileInfo = new File(this.file);
            fileLastModified = fileInfo.lastModified();
            fileLastLength = fileInfo.length();

            scan(input, handler);

            System.out.println("Scanned " + rows.size() + " rows");
            double mb = randomAccessFile.length() / (double) (1 << 20);
            double sec = (System.currentTimeMillis() - startTime) / 1000D;
            System.out.println((long) (mb / sec) + " MiB/sec");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isModified() {
        if (file == null) {
            return false;
        }

        File fileInfo = new File(this.file);
        long fileModified = fileInfo.lastModified();
        long fileLength = fileInfo.length();

        return fileModified != fileLastModified || fileLength != fileLastLength;
    }

    private void scan(InputStream input, ScanHandler handler) {
        long pos = 0;
        long cell = 0;
        int col = 0;
        int row = 0;
        int blockSize = 4 << 20;

        /*
         * We use two buffers to allow the next block to be read while processing the first. We
         * could potentially use more threads or a dedicated thread for more throughput.
         *
         * I suspect we are currently limited by scanning the buffer or adding new position to the
         * list, so profiling is needed.
         */
        Holder<byte[]> bbuf, bbuf2;

        bbuf = new Holder<>(new byte[blockSize]);
        bbuf2 = new Holder<>(new byte[blockSize]);

        Character quote = getFormat().getQuoteCharacter();
        Character escape = getFormat().getEscapeCharacter();
        if (escape == null) {
            escape = quote;
        }
        try (PushbackInputStream bufferedInput = new PushbackInputStream(new BufferedInputStream(input), 3)) {

            // Check for and skip UTF-8 BOM
            byte[] firstBytes = new byte[3];
            bufferedInput.read(firstBytes);
            if (Arrays.equals(firstBytes, UTF8_BOM)) {
                pos += 3;
            } else {
                bufferedInput.unread(firstBytes);
            }

            // Add first row
            handler.newCell(0, 0, 0, pos);
            handler.newRow(0, 0, 0, pos);

            int len = bufferedInput.read(bbuf.value);
            byte b1 = 0, b2 = 0;
            boolean quoted = false;

            while (len > 0) {

                CompletableFuture<Integer> nextBuffer = null;
                if (bufferedInput.available() > 0) {
                    nextBuffer = CompletableFuture.supplyAsync(() -> {
                        try {
                            byte[] buffer;
                            synchronized (bbuf2) {
                                buffer = bbuf2.value;
                            }
                            return bufferedInput.read(buffer);
                        } catch (Exception e) {
                            e.printStackTrace();
                            return 0;
                        }
                    });
                }

                // Look for a line terminator
                // TODO handle comment lines
                int i;
                byte[] buffer = bbuf.value;
                for (i = 0; i < len; i++) {
                    b1 = b2;
                    b2 = buffer[i];

                    // Check for quoted values
                    if (quoted && b1 == escape && b2 == quote) {
                        b1 = 0;
                        b2 = 0;
                    } else if (b1 == quote) {
                        quoted = !quoted;
                        b1 = 0;
                    }

                    if (!quoted) {
                        // Check for cell ending
                        if (b2 == format.getDelimiter()) {
                            cell++;
                            col++;
                            handler.newCell(cell, row, col, pos + i + 1);
                        }

                        // Check for line ending
                        if (b2 == '\n') {
                            cell++;
                            col++;
                            row++;
                            handler.newCell(cell, row, col, pos + i + 1);
                            handler.newRow(cell, row, col, pos + i + 1);
                            col = 0;
                        } else if (b1 == '\r') {
                            cell++;
                            col++;
                            row++;
                            handler.newCell(cell, row, col, pos + i);
                            handler.newRow(cell, row, col, pos + i);
                            col = 0;
                        }
                    }
                }
                pos += i;

                if (nextBuffer == null) {
                    break;
                } else {
                    // Swap buffers
                    synchronized (bbuf2) {
                        len = nextBuffer.get();
                        byte[] temp = bbuf.value;
                        bbuf.value = bbuf2.value;
                        bbuf2.value = temp;
                    }
                }

                // Stop if the CSV has been disposed
                synchronized (this) {
                    if (disposed) {
                        System.out.println("Aborted scan");
                        return;
                    }
                }
            }
            handler.notifyCompleted();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private synchronized void addCell(long pos) {
        cells.add(pos);
    }

    public synchronized String[] getRow(int row) {
        Long fromCell = rows.getPosition(row);
        Long toCell = rows.getPosition(row + 1);
        if (fromCell == null) {
            fromCell = 0L;
        }
        if (toCell == null) {
            toCell = (long) cells.size() * CELL_INDEX_DISTANCE;
        }

        Long from = cells.getPosition((int) (fromCell / CELL_INDEX_DISTANCE));
        Long to = cells.getPosition((int) (toCell / CELL_INDEX_DISTANCE + (toCell % CELL_INDEX_DISTANCE == 0 ? 1 : 2)));

        String rowContent = getContent(from, to).trim();
        if (rowContent.isEmpty()) {
            return new String[0];
        }

        String[] values = parseRow(rowContent, fromCell % CELL_INDEX_DISTANCE > 0);
        return values;
    }

    public synchronized String getCell(int row, int col) {
        Long rowStart = rows.getPosition(row);
        if (rowStart == null) {
            return null;
        }

        Long nextRow = rows.getPosition(row + 1);
        Long colCell = rowStart + col;
        if (nextRow != null && colCell > nextRow) {
            return null;
        }

        String value = getBlockCell(colCell);
        return value;
    }

    private String getCellBlock(int cellBlockIndex) {
        Long from = cells.getPosition(cellBlockIndex);
        Long to = cells.getPosition(cellBlockIndex + 1);

        String block = getContent(from, to).trim();
        return block;
    }

    public String getCellContentsAt(Long position) {
        if (position == null) {
            return "";
        }

        int blockIndex = cells.itemAt(position);
        if (blockIndex < 0) {
            blockIndex = -blockIndex - 2;
        }

        String block = getCellBlock(blockIndex);
        if (block.isEmpty()) {
            return null;
        }

        try {
            // FIXME this is doing the block lookup twice and converting back from strings
            byte[] blockBytes = block.getBytes(charset);
            List<String> blockCells = parseBlock(blockIndex);

            // Work out which cell we actually asked for
            Holder<Integer> cellHolder = new Holder<>();
            long offset = position - cells.getPosition(blockIndex);
            assert offset > 0;
            scan(new ByteArrayInputStream(blockBytes), new ScanHandler() {
                private long lastValue;

                @Override
                public void notifyCompleted() {
                    if (cellHolder.value == null) {
                        cellHolder.value = (int) lastValue;
                    }
                }

                @Override
                public void newRow(long cell, long row, int previousCols, long pos) {
                }

                @Override
                public void newCell(long cell, long row, int col, long pos) {
                    if (cellHolder.value == null && pos > offset) {
                        cellHolder.value = (int) (cell - 1);
                    } else {
                        lastValue = cell;
                    }
                }
            });
            return blockCells.get(cellHolder.value);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Long getCellAt(Long position) {
        if (position == null) {
            return null;
        }

        int blockIndex = cells.itemAt(position);
        if (blockIndex < 0) {
            // Subtract 2 because we want the index before where the lookup value
            // would be inserted
            blockIndex = -blockIndex - 2;
        }

        String block = getCellBlock(blockIndex);
        if (block.isEmpty()) {
            return null;
        }

        try {
            // FIXME this is doing the block lookup twice and converting back from strings
            byte[] blockBytes = block.getBytes(charset);

            // Work out which cell we actually asked for
            Holder<Integer> cellHolder = new Holder<>();
            long offset = position - cells.getPosition(blockIndex);
            scan(new ByteArrayInputStream(blockBytes), new ScanHandler() {
                private long lastValue;

                @Override
                public void notifyCompleted() {
                    if (cellHolder.value == null) {
                        cellHolder.value = (int) lastValue;
                    }
                }

                @Override
                public void newRow(long cell, long row, int previousCols, long pos) {
                }

                @Override
                public void newCell(long cell, long row, int col, long pos) {
                    if (cellHolder.value == null && pos > offset) {
                        cellHolder.value = (int) (cell - 1);
                    } else {
                        lastValue = cell;
                    }
                }
            });
            return (long) (blockIndex * CELL_INDEX_DISTANCE + cellHolder.value);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    private String getBlockCell(Long cell) {
        // Check for previously parsed cells
        long blockIndex = cell / CELL_INDEX_DISTANCE;
        List<String> blockCells = parseBlock(blockIndex);

        // Return cell at appropriate offset
        long offset = cell % CELL_INDEX_DISTANCE;
        if (blockCells != null && blockCells.size() > offset) {
            return blockCells.get((int) offset);
        }
        return null;
    }

    private List<String> parseBlock(long blockIndex) {
        List<String> blockCells = cellCache.getIfPresent(blockIndex);

        // Parse cells
        if (blockCells == null) {

            String block = getCellBlock((int) blockIndex);
            if (block.isEmpty()) {
                return null;
            }

            try {
                blockCells = parseCells(block);
            } catch (IOException e) {
                // HACK: Try appending a new terminating quote to complete the line
                try {
                    blockCells = parseCells(block + "\"");
                } catch (IOException e1) {
                    // Just split on commas and newlines
                    blockCells = Arrays.asList(block.split("\\s*(" +
                            Pattern.quote("" + getFormat().getDelimiter()) +
                            "|" +
                            Pattern.quote(getFormat().getRecordSeparator()) + ")\\s*"));
                }
            }

            if (blockCells != null) {
                cellCache.put(blockIndex, blockCells);
            }
        }
        return blockCells;
    }

    private List<String> parseCells(String block) throws IOException {
        List<String> blockCells = new ArrayList<>();

        try {
            CSVParser parser = CSVParser.parse(block, getFormat());
            for (CSVRecord record : parser) {
                record.forEach(blockCells::add);
                if (blockCells.size() > CELL_INDEX_DISTANCE + 1) {
                    log.warning("Found more than " + CELL_INDEX_DISTANCE + " cells in cell block");
                    break;
                }
            }
        } catch (Throwable e) {
            blockCells.clear();
            blockCells.addAll(Arrays.asList(block.split("[" + getFormat().getRecordSeparator() + "]")));
            while (blockCells.size() < CELL_INDEX_DISTANCE) {
                blockCells.add("<ERROR>");
            }
        }

        return blockCells;
    }

    private String[] parseRow(String rowContent, boolean nextCR) {
        try {
            CSVParser parser;
            CSVRecord record;
            try {
                parser = CSVParser.parse(rowContent, getFormat());
                Iterator<CSVRecord> iterator = parser.iterator();
                record = iterator.next();
                if (nextCR)
                    record = iterator.next();
            } catch (RuntimeException e) {
                // HACK: Try appending a new terminating quote to complete the line
                parser = CSVParser.parse(rowContent + "\"", getFormat());
                Iterator<CSVRecord> iterator = parser.iterator();
                record = iterator.next();
                if (nextCR)
                    record = iterator.next();
            }
            String[] cols = new String[record.size()];
            for (int i = 0; i < cols.length; i++) {
                cols[i] = record.get(i);
            }
            return cols;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return rowContent.split("\\s*" + Pattern.quote("" + getFormat().getDelimiter()) + "\\s*");
    }

    private String getContent(Long from, Long to) {
        try {
            if (from == null) {
                return "";
            }
            if (to == null) {
                if (contents != null) {
                    to = (long) contents.length();
                } else {
                    to = Math.min(from + 1024 * 100, randomAccessFile.length());
                }
            }

            if (contents != null) {
                return contents.substring(from.intValue(), to.intValue());
            } else if (randomAccessFile != null) {
                byte[] bs = new byte[(int) (to - from)];
                randomAccessFile.seek(from);
                int read = randomAccessFile.read(bs);
                return new String(bs, 0, read, charset).trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return "";
    }

    public synchronized int getRowCount() {
        return rows.size();
    }

    public synchronized int getColCount() {
        return maxColumns;
    }

    public CSVFormat getFormat() {
        return format;
    }

    public void setFormat(CSVFormat format) {
        this.format = format;
        maxColumns = -1;
        cellCache.invalidateAll();
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
        cellCache.invalidateAll();
    }

    public Index search(Pattern pattern, ProgressListener listener) {
        Index results = new Index();
        long start = System.nanoTime();

        IProgressMonitor progressMonitor = progressManager.getProgressMonitor();
        progressMonitor.beginTask("Searching...", 1000);

        CompletableFuture.runAsync(() -> {
            try {
                long lastProgress = 0;
                long totalProgress = randomAccessFile.length();
                long lastCell = -1;
                Iterator<Long> cellIterator = cells.iterator();

                // We can't mmap pages larger than 2G, so proceed in blocks of 100M
                int blockSize = 100 << 20;
                for (long offset = 0; offset < randomAccessFile.length(); offset += blockSize) {

                    // Report progress
                    listener.changed();
                    long progress = (offset * 1000) / totalProgress;
                    if (progress != lastProgress) {
                        progressMonitor.worked((int) (progress - lastProgress));
                        lastProgress = progress;
                    }

                    // TODO parallelize block processing

                    // Map a buffer long enough to match the whole pattern at the end of the block
                    // if we start on the last byte of the block
                    long mapSize = Math.min(blockSize + (1 << 10), randomAccessFile.length() - offset);
                    MappedByteBuffer mappedBuffer = randomAccessFile.getChannel().map(
                            MapMode.READ_ONLY, offset, mapSize);

                    // Perform the search
                    int maxMatch = (int) Math.min(blockSize, mapSize);
                    Matcher matcher = pattern.matcher(new ByteCharSequence(mappedBuffer));
                    while (matcher.find() && matcher.start() < maxMatch) {
                        long position = offset + matcher.start();

                        boolean newCell = false;
                        while (lastCell <= position && cellIterator.hasNext()) {
                            lastCell = Math.abs(cellIterator.next());
                            newCell = true;
                        }

                        if (newCell) {
                            results.add(position);
                        }
                    }
                }

                // Log performance
                Duration duration = Duration.ofNanos(System.nanoTime() - start);
                System.out.println("Search complete in " + duration);
                double bytesPerSec = randomAccessFile.length()
                        / (TimeUnit.NANOSECONDS.toMillis(duration.toNanos()) / 1000.0);
                System.out.println(((int) bytesPerSec >> 20) + " MiB/sec");
                System.out.println("Found " + results.size() + " matches");
                results.compact();

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                listener.completed();
                progressMonitor.done();
            }
        });
        return results;
    }

    /**
     * Get the coordinates of the cell at the given file position
     *
     * @param position
     * @return
     */
    public Point getPoint(Long position) {
        Long cell = getCellAt(position);

        int row = rows.itemAt(cell);
        if (row < 0)
            row = -row - 2;

        Long rowStart = rows.getPosition(row);
        int col = (int) (cell - rowStart);
        return new Point(col, row);
    }

}

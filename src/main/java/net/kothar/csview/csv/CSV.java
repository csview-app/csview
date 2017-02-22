/* Copyright 2016 Kothar Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.kothar.csview.csv;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.ws.Holder;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.swt.events.DisposeEvent;

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
	
	/** Maps rows onto the cell that row starts at */
	private Index rows = new Index();
	
	/** Maps every Nth cell to its position in the file */
	private Index cells = new Index();
	
	private String contents;
	private RandomAccessFile randomAccessFile;
	private String file;
	
	private CSVFormat format = CSVFormat.DEFAULT;
	private int maxColumns = -1;
	
	private List<ProgressListener> progressListeners = new ArrayList<>();
	
	private boolean disposed = false;
	private String charset = "UTF-8";
	
	private Cache<Long, List<String>> cellCache;
	
	public CSV() {
		cellCache = CacheBuilder.newBuilder()
			.maximumSize(1000)
			.build();
		
	}

	public void addRowListener(IndexListener listener) {
		rows.addListener(listener);
	}

	/**
	 * Stop any active scan and dispose of any resources this
	 * instance may be holding onto
	 */
	public synchronized void dispose(DisposeEvent e) {
		disposed = true;
	}
	
	/**
	 * Adds a row to the row index at the given character offset
	 * @param cell
	 */
	public synchronized void addRow(long cell, long pos) {
		rows.add(cell);
		if (rows.size() % 5000 == 0) {
			notifyProgress(pos);
		}
	}

	/**
	 * Sets the in-memory contents of this CSV
	 * @param string
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public void scan() {
		if (file != null) {
			CompletableFuture.runAsync(this::scanFile);
		} else if (contents != null) {
			CompletableFuture.runAsync(this::scanContents);
		}
	}

	private void scanContents() {
		scan(new ByteArrayInputStream(contents.getBytes()));
	}
	
	private void scanFile() {
		try (FileInputStream input = new FileInputStream(file)) {
			scan(input);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void scan(InputStream input) {
		long pos = 0;
		long cell = 0;
		int col = 0;
		int blockSize = 1024 * 1024 * 4;

		/* We use two buffers to allow the next block to be read
		 * while processing the first. We could potentially use more
		 * threads or a dedicated thread for more throughput.
		 * 
		 * I suspect we are currently limited by scanning the buffer or adding
		 * new position to the list, so profiling is needed.
		 */
		Holder<byte[]> bbuf, bbuf2;
		
		
		bbuf = new Holder<>(new byte[blockSize]);
		bbuf2 = new Holder<>(new byte[blockSize]);
		
		Character quote = getFormat().getQuoteCharacter();
		Character escape = getFormat().getEscapeCharacter();
		if (escape == null) {
			escape = quote;
		}

		long startTime = System.currentTimeMillis();
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
			addCell(pos);
			addRow(0, pos);

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
							if (cell % CELL_INDEX_DISTANCE == 0) addCell(pos + i + 1);
						}
						
						// Check for line ending
						if (b2 == '\n') {
							cell++;
							col++;
							if (cell % CELL_INDEX_DISTANCE == 0) addCell(pos + i);
							if (col > maxColumns) {
								maxColumns = col;
								notifyColumnsChanged(col);
							}
							col = 0;
							addRow(cell, pos);
						} else if (b1 == '\r') {
							cell++;
							col++;
							if (cell % CELL_INDEX_DISTANCE == 0) addCell(pos + i - 1);
							if (col > maxColumns) {
								maxColumns = col;
								notifyColumnsChanged(col);
							}
							col = 0;
							addRow(cell, pos);
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

			System.out.println("Scanned " + rows.size() + " rows");
			double mb = randomAccessFile.length() / (double) (1 << 20);
			double sec = (System.currentTimeMillis() - startTime) / 1000D;
			System.out.println((long) (mb / sec) + " MiB/sec");
			notifyCompleted();
			
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
		Long toCell = rows.getPosition(row+1);
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
		Long nextRow = rows.getPosition(row+1);
		Long colCell = rowStart + col;
		if (nextRow != null && colCell > nextRow) {
			return null;
		}
		
		int cellBlockIndex = (int) (colCell / CELL_INDEX_DISTANCE);
		Long from = cells.getPosition(cellBlockIndex);
		Long to = cells.getPosition(cellBlockIndex + 1);

		String block = getContent(from, to).trim();
		if (block.isEmpty()) {
			return null;
		}
		
		String value = parseCell(block, colCell);
		return value;
	}

	private String parseCell(String block, Long cell) {
		// Check for previously parsed cells
		long blockStart = cell / CELL_INDEX_DISTANCE;
		List<String> blockCells = cellCache.getIfPresent(blockStart);
		
		// Parse cells
		if (blockCells == null) {
			try {
				blockCells = parseCells(block);
			} catch (IOException e) {
				// HACK: Try appending a new terminating quote to complete the line
				try {
					blockCells = parseCells(block + "\"");
				} catch (IOException e1) {
					// Just split on commas and newlines
					blockCells = Arrays.asList(block.split("\\s*(" + 
							Pattern.quote(""+getFormat().getDelimiter()) +
							"|" + 
							Pattern.quote(getFormat().getRecordSeparator()) + ")\\s*"));
				}
			}
			
			if (blockCells != null) {
				cellCache.put(blockStart, blockCells);
			}
		}

		// Return cell at appropriate offset
		long offset = cell % CELL_INDEX_DISTANCE;
		if (blockCells != null && blockCells.size() > offset) {
			return blockCells.get((int) offset);
		}
		return null;
	}

	private List<String> parseCells(String block) throws IOException {
		List<String> blockCells = new ArrayList<>();

		CSVParser parser = CSVParser.parse(block, getFormat());
		for (CSVRecord record: parser) {
			record.forEach(blockCells::add);
			if (blockCells.size() > CELL_INDEX_DISTANCE + 1) {
				log.warning("Found more than " + CELL_INDEX_DISTANCE + " cells in cell block");
				break;
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

		return rowContent.split("\\s*" + Pattern.quote(""+getFormat().getDelimiter()) + "\\s*");
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
				byte[] bs = new byte[(int) (to-from)];
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

	public void addProgressListener(ProgressListener listener) {
		progressListeners.add(listener);
	}
	
	private void notifyColumnsChanged(int columns) {
		for (ProgressListener listener: progressListeners) {
			listener.columnsChanged(columns);
		}
	}
	
	private void notifyProgress(long progress) {
		for (ProgressListener listener: progressListeners) {
			listener.changed(progress);
		}
	}
	
	private void notifyCompleted() {
		for (ProgressListener listener: progressListeners) {
			listener.completed();
		}
	}

	public CSVFormat getFormat() {
		return format;
	}

	public void setFormat(CSVFormat format) {
		this.format = format;
		maxColumns = -1;
	}

	public String getCharset() {
		return charset;
	}

	public void setCharset(String charset) {
		this.charset = charset;
	}
	
}

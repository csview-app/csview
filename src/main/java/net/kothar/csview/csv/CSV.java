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
import org.apache.commons.lang3.StringEscapeUtils;
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
	
	public void scan(ProgressListener listener) {
		scan(new ScanHandler() {
			@Override
			public void newCell(long cell, long row, int col, long pos) {
				if (cell % CELL_INDEX_DISTANCE == 0) addCell(pos);
			}

			@Override
			public void newRow(long cell, long row, int previousCols, long pos) {
				if (previousCols > maxColumns) {
					maxColumns = previousCols;
					notifyColumnsChanged(previousCols);
				}
				
				addRow(cell, pos);
				
				if (row % 5000 == 0) {
					listener.changed(pos);
				}
			}

			private void notifyColumnsChanged(int cols) {
				listener.columnsChanged(cols);
			}

			@Override
			public void notifyCompleted() {
				listener.completed();
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
			scan(input, handler);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void scan(InputStream input, ScanHandler handler) {
		long pos = 0;
		long cell = 0;
		int col = 0;
		int row = 0;
		int blockSize = 4 << 20;

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
							handler.newCell(cell, row, col, pos + i);
						}
						
						// Check for line ending
						if (b2 == '\n') {
							cell++;
							col++;
							row++;
							handler.newCell(cell, row, col, pos + i);
							handler.newRow(cell, row, col, pos + i);
							col = 0;
						} else if (b1 == '\r') {
							cell++;
							col++;
							row++;
							handler.newCell(cell, row, col, pos + i - 1);
							handler.newRow(cell, row, col, pos + i - 1);
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

			System.out.println("Scanned " + rows.size() + " rows");
			double mb = randomAccessFile.length() / (double) (1 << 20);
			double sec = (System.currentTimeMillis() - startTime) / 1000D;
			System.out.println((long) (mb / sec) + " MiB/sec");
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

	public Index search(String searchPattern, ProgressListener listener) {
		Pattern pattern = Pattern.compile(searchPattern);
		Index index = new Index();
		
		// Scan cells
		scan(new ScanHandler() {
			
			long lastPos = 0;
			byte[] buffer = new byte[1 << 20];
			
			@Override
			public void newRow(long cell, long row, int previousCols, long pos) {
				if (row % 5000 == 0) {
					listener.changed(pos);
				}
			}
			
			@Override
			public void newCell(long cell, long row, int col, long pos) {
				if (pos == lastPos) {
					return;
				}
				
				try {
					// Get cell contents
					randomAccessFile.seek(lastPos);
					int read = randomAccessFile.read(buffer, 0, (int) (pos - lastPos));
					String rawCell = new String(buffer, 0, read, charset).trim();
					
					// Unescape CSV
					String unescapedCell = StringEscapeUtils.unescapeCsv(rawCell);
					
					// Look for pattern
					if (pattern.matcher(unescapedCell).find()) {
						index.add(lastPos);
					}
					
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					lastPos = pos;
				}
			}

			@Override
			public void notifyCompleted() {
				listener.completed();
			}
		});
		return index;
	}
	
}

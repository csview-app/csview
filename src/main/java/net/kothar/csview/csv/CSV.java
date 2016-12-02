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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.xml.ws.Holder;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.eclipse.swt.events.DisposeEvent;

import net.kothar.csview.ProgressListener;
import net.kothar.csview.RowListener;

public class CSV {

	private LineMap rows = new LineMap();
	private String contents;
	private RandomAccessFile randomAccessFile;
	private String file;
	
	private CSVFormat format = CSVFormat.DEFAULT;
	
	private List<ProgressListener> progressListeners = new ArrayList<>();
	
	private boolean disposed = false;
	
	public CSV() {
	}

	public void addRowListener(RowListener listener) {
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
	 * @param pos
	 */
	public synchronized void addRow(long pos) {
		rows.add(pos);
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
		} catch (FileNotFoundException e) {
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

		// Add first row
		addRow(0);

		long startTime = System.currentTimeMillis();
		try (InputStream bufferedInput = new BufferedInputStream(input)) {

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
					
					// Check for line ending
					if (!quoted) {
						if (b2 == '\n') {
							addRow(pos + i);
						} else if (b1 == '\r') {
							addRow(pos + i - 1);
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

	public synchronized String[] getRow(int row) {
		Long from = rows.getPosition(row);
		Long to = rows.getPosition(row+1);

		String rowContent = getContent(from, to).trim();
		if (rowContent.isEmpty()) {
			return new String[0];
		}
		
		return parseRow(rowContent);
	}

	private String[] parseRow(String rowContent) {
		try {
			CSVParser parser;
			CSVRecord record;
			try {
				parser = CSVParser.parse(rowContent, getFormat());
				record = parser.iterator().next();
			} catch (RuntimeException e) {
				// HACK: Try appending a new terminating quote to complete the line
				parser = CSVParser.parse(rowContent + "\"", getFormat());
				record = parser.iterator().next();
			}
			String[] cols = new String[record.size()];
			for (int i = 0; i < cols.length; i++) {
				cols[i] = record.get(i);
			}
			return cols;
		} catch (Exception e) {
			e.printStackTrace();
		}

		return rowContent.split("\\s*,\\s*");
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
				return new String(bs, 0, read, "UTF-8").trim();
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
	}
	
}

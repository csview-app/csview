package net.kothar.csview;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;


public class CSV {

	private LineMap rows = new LineMap();
	private String contents;
	private RandomAccessFile randomAccessFile;
	private String file;
	
	private Cache<Integer, String[]> rowCache;
	
	public CSV() {
		rowCache = CacheBuilder.newBuilder()
			.maximumSize(500)
			.build();
	}

	public void addRowListener(RowListener listener) {
		rows.addListener(listener);
	}

	/**
	 * Adds a row to the row index at the given character offset
	 * @param pos
	 */
	public synchronized void addRow(long pos) {
		rows.add(pos);
	}

	/**
	 * Sets the in-memory contents of this CSV
	 * @param string
	 */
	public synchronized void setContents(String contents) {
		this.contents = contents;
		CompletableFuture.runAsync(this::scanContents);
	}

	public void setFile(String file) {
		this.file = file;
		try {
			this.randomAccessFile = new RandomAccessFile(file, "r");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		CompletableFuture.runAsync(this::scanFile);
	}

	private void scanContents() {
		int pos = 0;
		Matcher matcher = Pattern.compile("\\r|\\n|\\r\\n").matcher(contents);
		while (matcher.find()) {
			addRow(pos);
			pos = matcher.end();
		}

		// Add last row
		if (pos < contents.length()) {
			addRow(pos);
		}

		System.out.println("Scanned " + rows.size() + " rows");
	}

	/* We use two buffers to allow the next block to be read
	 * while processing the first. We could potentially use more
	 * threads or a dedicated thread for more throughput.
	 * 
	 * I suspect we are currently limited by scanning the buffer or adding
	 * new position to the list, so profiling is needed.
	 */
	private byte[] bbuf, bbuf2;
	
	private void scanFile() {
		long pos = 0;
		int blockSize = 1024 * 1024 * 4;
		bbuf = new byte[blockSize + 1];
		bbuf2 = new byte[blockSize + 1];

		// Add first row
		addRow(0);

		long startTime = System.currentTimeMillis();
		try (FileInputStream input = new FileInputStream(file)) {
			int len = input.read(bbuf) - 1;
			
			while (len > 0) {
				bbuf2[0] = bbuf[len];
				CompletableFuture<Integer> nextBuffer = CompletableFuture.supplyAsync(() -> {
					try {
						return input.read(bbuf2, 1, blockSize);
					} catch (Exception e) {
						e.printStackTrace();
						return 0;
					}
				});
				
				// Look for a line terminator
				int i;
				for (i = 0; i < len; i++) {
					byte b = bbuf[i];
					if (b == '\n') {
						addRow(pos + i);
					} else if (b == '\r') {
						// Peek at the next char
						if (bbuf[i+1] == '\n') {
							i++;
						}
						addRow(pos + i);
					}
				}
				pos += i;

				// Swap buffers
				len = nextBuffer.get();
				byte[] temp = bbuf;
				bbuf = bbuf2;
				bbuf2 = temp;
			}

			System.out.println("Scanned " + rows.size() + " rows");
			double mb = randomAccessFile.length() / (double) (1 << 20);
			double sec = (System.currentTimeMillis() - startTime) / 1000D;
			System.out.println((long) (mb / sec) + " MiB/sec");
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		} finally {
			bbuf = null;
			bbuf2 = null;
		}
	}

	public synchronized String[] getRow(int row) {
		try {
			return rowCache.get(row, () -> loadRow(row));
		} catch (ExecutionException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private String[] loadRow(int row) {
		Long from = rows.getPosition(row);
		Long to = rows.getPosition(row+1);

		String rowContent = getContent(from, to).trim();

		try {
			CSVParser parser = CSVParser.parse(rowContent, CSVFormat.DEFAULT);
			CSVRecord record = parser.iterator().next();
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
}

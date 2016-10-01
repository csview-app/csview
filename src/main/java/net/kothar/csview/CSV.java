package net.kothar.csview;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;


public class CSV {

	private LineMap rows = new LineMap();
	private String contents;
	private RandomAccessFile randomAccessFile;
	private String file;

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

	private void scanFile() {
		long pos = 0;
		byte[] bbuf = new byte[1024 * 1024];
		ByteBuffer buffer = ByteBuffer.allocate(bbuf.length + 2);

		// Add first row
		addRow(0);

		try (FileInputStream input = new FileInputStream(file)) {
			int read;
			while ((read = input.read(bbuf)) > 0) {

				// Add new data to buffer
				buffer.limit(buffer.capacity());
				buffer.put(bbuf, 0, read);
				buffer.flip();

				// Look for a line terminator
				for (int i = 0; i < buffer.limit() - 1; i++) {
					byte b = buffer.get();
					if (b == '\n') {
						addRow(pos + buffer.position());
					} else if (b == '\r') {
						// Peek at the next char
						if (buffer.get() != '\n') {
							buffer.position(buffer.position() - 1);
						}
						addRow(pos + buffer.position());
					}
				}
				pos += buffer.position();

				// Update buffer
				buffer.compact();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.println("Scanned " + rows.size() + " rows");
	}

	public synchronized String[] getRow(int row) {
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

	private Long getContentLength() {
		if (contents != null) {
			return (long) contents.length();
		} else if (randomAccessFile != null) {
			try {
				return randomAccessFile.length();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return 0L;
	}

	public synchronized int getRowCount() {
		return rows.size();
	}
}

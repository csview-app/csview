package net.kothar.csview.index;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

public class MappedBufferAllocator {

	public class Page {
		
		private int index;
		
		private ByteBuffer buffer;

		public Page(int index, ByteBuffer buffer) {
			this.index = index;
			this.buffer = buffer;
		}

		public int getIndex() {
			return index;
		}

		public ByteBuffer getBuffer() {
			return buffer;
		}
		
		/**
		 * Returns this block to the allocator to be re-used
		 */
		public void free() {
			MappedBufferAllocator.this.free(index);
			buffer = null;
		}
	}

	public static final int PAGE_SIZE = 4 * 1024;
	
	private File file;
	private RandomAccessFile randomAccessFile;

	private MappedByteBuffer buffer;

	LongBuffer index;
	
	public MappedBufferAllocator() {
		try {
			file = File.createTempFile("net.kothar.csview_", ".index");
			file.deleteOnExit();
			randomAccessFile = new RandomAccessFile(file, "rw");
			
			remap(PAGE_SIZE * 1024);
			index = block(0).asLongBuffer();
			initIndex(index);
		} catch (IOException e) {
			throw new RuntimeException("Unable to create mapped buffer", e);
		}
	}

	private void initIndex(LongBuffer buffer) {
		buffer.put(0, 1L << 63);
		for (int i = 1; i < buffer.limit(); i++)
			buffer.put(i, 0);
	}
	
	public synchronized Page page(int i) {
		return new Page(i, block(i));
	}

	public synchronized ByteBuffer block(int i) {
		int offset = i * PAGE_SIZE;
		try {
			long size = randomAccessFile.length();
			if (offset + PAGE_SIZE > size) {
				while (offset + PAGE_SIZE > size) {
					size <<= 1;
				}
				remap(size);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		buffer.position(offset);
		buffer.limit(offset + PAGE_SIZE);
		return buffer.slice();
	}

	private void remap(long size) throws IOException {
		System.out.println("Resizing buffer file to " + (size / (1024 * 1024)) + " MiB");
		randomAccessFile.setLength(size);
		buffer = randomAccessFile.getChannel().map(MapMode.READ_WRITE, 0, size);
	}
	
	public Page allocate() {
		// Find the next free block
		for (int i = 0; i < index.limit(); i++) {
			long value = index.get(i);
			if (value == 0xFFFFFFFFFFFFFFFFL) {
				continue;
			}
			
			int page = i * 64;
			long mask = 1L << 63;
			while ((value & mask) != 0L) {
				mask >>>= 1;
				page ++;
			}
			value |= mask;
			index.put(i, value);
			
			return new Page(page, block(page));
		}
		
		throw new OutOfMemoryError("TODO: add new index pages");
	}
	
	synchronized void free(int page) {
		int pos = page / 64;
		int bit = page % 64;
		long mask = ~(1L << (63-bit));
		
		long value = index.get(pos);
		value &= mask;
		index.put(pos, value);
	}
	
	public static class Test {
		public static void main(String[] args) {
			MappedBufferAllocator allocator = new MappedBufferAllocator();
			
			System.out.println("Index: " + Long.toHexString(allocator.index.get(0)));
			Page page = allocator.allocate();
			System.out.println("Allocated page: " + page.index);
			System.out.println("Index: " + Long.toHexString(allocator.index.get(0)));
			
			int n = 1500;
			for (int i = 0; i < n; i++) {
				allocator.allocate();
			}
			System.out.println("Allocated " + n + " pages");
			System.out.println("Index: " + Long.toHexString(allocator.index.get(0)));
			
			page.free();
			System.out.println("Freed original page");
			System.out.println("Index: " + Long.toHexString(allocator.index.get(0)));
			
			page = allocator.allocate();
			System.out.println("Allocated page: " + page.index);
			System.out.println("Index: " + Long.toHexString(allocator.index.get(0)));
			
		}
	}
}

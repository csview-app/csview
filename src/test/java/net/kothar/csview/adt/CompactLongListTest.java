package net.kothar.csview.adt;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;

import org.junit.Before;
import org.junit.Test;

import net.kothar.csview.adt.CompactLongList.Block;

public class CompactLongListTest {

	private CompactLongList list;

	@Before
	public void setup() {
		list = new CompactLongList();
		list.blockSize = 2;
	}
	
	@Test
	public void add() {
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		
		assertEquals(Long.valueOf(50), list.get(50));
	}
	
	@Test
	public void remove() {
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		
		list.remove(50);
		assertEquals(Long.valueOf(51), list.get(50));
		
		for (long i = 0; i < 99; i++) {
			list.remove(0);
		}
		
		assertEquals(0, list.size());
		assertEquals(0, list.root.height);
		
		// Add after remove
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
	}
	
	@Test(expected=IndexOutOfBoundsException.class)
	public void get_out_of_range() {
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		
		list.get(150);
	}
	
	@Test(expected=IndexOutOfBoundsException.class)
	public void get_negative_index() {
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		
		list.get(-1);
	}
	
	@Test
	public void block_split() {
		ByteBuffer buffer = ByteBuffer.allocate(2);
		buffer.put((byte) 1);
		buffer.put((byte) 2);
		Block b = list.new Block(buffer, 1, 0);
		
		assertEquals(2, b.size());
		assertEquals(Long.valueOf(1), b.get(0));
		assertEquals(Long.valueOf(2), b.get(1));
		
		b.split(1);
		assertEquals(1, b.left.size());
		assertEquals(1, b.right.size());
		assertEquals(Long.valueOf(1), b.get(0));
		assertEquals(Long.valueOf(2), b.get(1));
	}
	
	@Test
	public void binary_search() {
		list.blockSize = 10;
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		
		int pos = list.search(50);
		assertEquals(50, pos);
		
		list.remove(50);
		pos = list.search(50);
		assertEquals(-51, pos);
		
		pos = list.search(51);
		assertEquals(50, pos);
		
		list.add(50, 50L);
		pos = list.search(50);
		assertEquals(50, pos);
	}
	
	@Test
	public void sparse_binary_search() {
		list.blockSize = 1000;
		for (long i = 0; i < 10000; i++) {
			list.add(i * 100L);
		}
		
		assertEquals(100, list.search(10000));
		assertEquals(-101, list.search(9990));
		assertEquals(-102, list.search(10001));
	}

	@Test
	public void add_at_index() {
		for (long i = 0; i < 100; i++) {
			list.add(i);
		}
		for (int i = 0; i < 100; i++) {
			list.add(i * 2, (long) i);
		}

		for (int i = 0; i < 100; i++) {
			Long value = list.get(i * 2);
			assertEquals("List value does not match at " + i * 2, Long.valueOf(i), value);
		}
	}
	
	@Test
	public void binary_encoding() {
		for (int valueLength = 1; valueLength < 8; valueLength++) {
			ByteBuffer buffer = ByteBuffer.allocate(valueLength * 256);
			for (int i = 0; i < 256; i++) {
				CompactLongList.writeItem(i, buffer, 128, valueLength);
			}
			
			buffer.rewind();
			for (int i = 0; i < 256; i++) {
				Long value = CompactLongList.readItem(buffer, 128, valueLength);
				assertEquals("Result doesn't match for " + valueLength + " byte encoding", Long.valueOf(i), value);
			}
		}
	}
}
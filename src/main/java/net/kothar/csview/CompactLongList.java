package net.kothar.csview;

import java.nio.ByteBuffer;
import java.util.AbstractList;

/**
 * Specialises the behaviour of BlockList by storing longs in a byte array;
 * @author mhouston
 */
public class CompactLongList extends AbstractList<Long> {
	
	private static final int DEFAULT_BLOCK_SIZE = 10_000;
	Block root;
	int blockSize = DEFAULT_BLOCK_SIZE;
	
	class Block {
		ByteBuffer items;
		int valueLength;
		long offset;
		
		Block left;
		Block right;
		
		private int height = 0;
		private int treeSize = 0;
		
		public Block() {
			valueLength = 8;
			items = ByteBuffer.allocate(blockSize * valueLength);
			items.limit(0);
			offset = 0;
		}
		
		Block(ByteBuffer items, int valueLength, long offset) {
			this.items = items;
			this.valueLength = valueLength;
			this.offset = offset;
			compact();
		}
		
		int size() {
			if (items != null) {
				return items.limit() / valueLength;
			} else {
				return treeSize;
			}
		}
		
		Long get(int index) {
			if (items != null) {
				items.position(index * valueLength);
				return readItem(items, offset, valueLength);
			}
			
			if (index < left.size()) {
				return left.get(index);
			} else {
				return right.get(index - left.size());
			}
		}

		void split(int index) {
			split(index, index);
		}
		
		void split(int index1, int index2) {
			items.position(index2 * valueLength);
			right = new Block(items.slice(), valueLength, offset);
			
			items.position(0);
			items.limit(index1 * valueLength);
			left = new Block(items, valueLength, offset);
			
			items = null;
			updateTree();
		}
		
		void split() {
			left = new Block(items, valueLength, offset);
			right = new Block();
			
			items = null;
			valueLength = 0;
			offset = 0;
			
			updateTree();
		}

		public Long remove(int index) {
			Long value;
			if (items != null) {
				value = get(index);
				if (index == 0) {
					items.position(1);
					items = items.slice();
				} else if (index == size() - 1) {
					items.limit(items.limit() - 1);
				} else {
					split(index, index + 1);
				}
			} else if (index < left.size()) {
				value = left.remove(index);
				updateTree();
			} else {
				value = right.remove(index - left.size());
				updateTree();
			}
			return value;
		}
		
		public void add(int index, Long e) {
			if (index == size()) {
				append(e);
				return;
			} else if (items != null) {
				split(index);
				left.append(e);
			} else if (index <= left.size()) {
				left.add(index, e);
			} else {
				right.add(index - left.size(), e);
			}
			treeSize++;
		}
		
		public void append(Long e) {
			if (items != null) {
				if (size() >= blockSize) {
					split();
				} else if (requiredValueLength(e - offset) > valueLength) {
					split();
				}
			}
			
			if (items != null) {
				int limit = items.limit();
				items.limit(limit + valueLength);
				items.position(limit);
				writeItem(e, items, offset, valueLength);
			} else {
				right.append(e);
				if (!balance()) {
					updateTree();
				}
			}
		}

		private int requiredValueLength(long distance) {
			int newValueLength = 1;
			while (newValueLength < 8 && distance > 1L << (newValueLength * 8 - 1))
				newValueLength += 1;
			return newValueLength;
		}

		private void updateTree() {
			treeSize = left.size() + right.size();
			height = Math.max(right.height, left.height) + 1;
		}

		private boolean balance() {
			if (right.height > left.height + 1) {
				Block A = right;
				Block B = this;
				
				Block a = left;
				Block b = right.left;
				Block c = right.right;
				
				A.left = a;
				A.right = b;
				A.updateTree();
				
				B.left = A;
				B.right = c;
				B.updateTree();
				return true;
			} else if (left.height > right.height + 1) {
				Block A = this;
				Block B = left;
				
				Block a = left.left;
				Block b = left.right;
				Block c = right;
				
				B.left = b;
				B.right = c;
				B.updateTree();
				
				A.left = a;
				A.right = B;
				A.updateTree();
				return true;
			}
			
			return false;
		}

		private void compact() {
			// Choose a new offset
			long min = Long.MAX_VALUE;
			long max = Long.MIN_VALUE;
			
			for (int i = 0; i < size(); i++) {
				Long value = get(i);
				if (value < min) min = value;
				if (value > max) max = value;
			}
			
			long newOffset = (min + max) / 2;
			long distance = max - newOffset;
			
			// Choose a new valueLength
			int newValueLength = requiredValueLength(distance);
			if (newValueLength == 8) newOffset = 0;
			
			ByteBuffer buffer = ByteBuffer.allocate(blockSize * newValueLength);
			
			items.rewind();
			
			if (newValueLength == valueLength && newOffset == offset) {
				// No re-encoding needed if lengths and offsets match
				buffer.put(items);
			} else {
				// Re-encode with new offset
				while (items.remaining() > 0) {
					long value = readItem(items, offset, valueLength);
					writeItem(value, buffer, newOffset, newValueLength);
				}
			}
			
			buffer.flip();
			items = buffer;
			offset = newOffset;
			valueLength = newValueLength;
		}

		private long readItem(ByteBuffer buffer, long offset, int valueLength) {
			switch (valueLength) {
			case 1:
				return buffer.get() + offset;
			case 2:
				return buffer.getShort() + offset;
			case 3:
				long value = 0xFFL & buffer.get();
				value |= (0xFFL & buffer.get()) << 8;
				value |= buffer.get() << 16;
				return value + offset;
			case 4:
				return buffer.getInt() + offset;
			case 5:
			case 6:
			case 7:
			case 8:
				return buffer.getLong();
			default:
				throw new IllegalArgumentException();
			}
		}

		private void writeItem(long value, ByteBuffer buffer, long offset, int valueLength) {
			switch (valueLength) {
			case 1:
				buffer.put((byte) (value - offset));
			case 2:
				buffer.putShort((short) (value - offset));
				return;
			case 3:
				value = value - offset;
				buffer.put((byte) (value & 0xFF));
				value >>= 8;
				buffer.put((byte) (value & 0xFF));
				value >>= 8;
				buffer.put((byte) value);
				return;
			case 4:
				buffer.putInt((int) (value - offset));
				return;
			case 5:
			case 6:
			case 7:
			case 8:
				buffer.putLong(value);
				return;
			default:
				throw new IllegalArgumentException();
			}
		}
	}

	@Override
	public Long get(int index) {
		return root.get(index);
	}

	@Override
	public int size() {
		if (root == null) {
			return 0;
		}
		
		return root.size();
	}
	
	@Override
	public void add(int index, Long e) {
		if (root == null) {
			root = new Block();
		}
		root.add(index, e);
	}
	
	@Override
	public Long remove(int index) {
		if (root == null) {
			throw new IndexOutOfBoundsException();
		}
		return root.remove(index);
	}

	public static class Test {
		public static void main(String[] args) {
			CompactLongList list = new CompactLongList();
			list.blockSize = 2;
			
			for (long i = 0; i < 100; i++) {
				list.add(i);
			}
			for (long i = 0; i < 100; i++) {
				list.add((int) (i * 2), i);
			}
			
			for (int i = 0; i < 100; i++) {
				Long value = list.get(i * 2);
				if (value != i) {
					throw new RuntimeException("List value does not match at " + i + ": got " + value);
				}
			}
		}
	}
	
}

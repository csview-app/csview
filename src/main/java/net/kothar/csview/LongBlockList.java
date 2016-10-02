package net.kothar.csview;

import java.nio.ByteBuffer;
import java.util.AbstractList;

/**
 * Specialises the behaviour of BlockList by storing longs in a byte array;
 * @author mhouston
 */
public class LongBlockList extends AbstractList<Long> {
	
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
				return items.getLong(index * valueLength);
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
			if (items != null && size() >= blockSize) {
				split(size());
			}
			
			if (items != null) {
				int limit = items.limit();
				items.limit(limit + valueLength);
				items.position(limit);
				items.putLong(e);
			} else {
				right.append(e);
				if (!balance()) {
					updateTree();
				}
			}
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
			ByteBuffer buffer = ByteBuffer.allocate(blockSize * valueLength);
			items.rewind();
			buffer.put(items);
			buffer.flip();
			items = buffer;
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
			LongBlockList list = new LongBlockList();
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

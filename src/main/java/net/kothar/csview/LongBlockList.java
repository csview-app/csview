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
	int valueLength = 8;
	
	class Block {
		boolean copyOnWrite;
		ByteBuffer items;
		
		Block parent;
		Block left;
		Block right;
		
		private int height = 0;
		private int _size = 0;
		
		public Block() {
			items = ByteBuffer.allocate(blockSize * valueLength);
			items.limit(0);
			copyOnWrite = false;
		}
		
		Block(ByteBuffer items, Block parent) {
			this.items = items;
			this.parent = parent;
			copyOnWrite = true;
		}
		
		int size() {
			if (items != null) {
				return items.limit() / valueLength;
			} else {
				return _size;
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
			_size = items.limit() / valueLength;
			items.position(index * valueLength);
			right = new Block(items.slice(), this);
			items.flip();
			left = new Block(items.slice(), this);
			
			items = null;
			height = 1;
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
					_size = size() - 1;
					
					items.position((index + 1) * valueLength);
					right = new Block(items.slice(), this);
					
					items.position(0);
					items.limit(index * valueLength);
					left = new Block(items.slice(), this);
					
					items = null;
					height = 1;
				}
			} else if (index < left.size()) {
				value = left.remove(index);
				update();
			} else {
				value = right.remove(index - left.size());
				update();
			}
			return value;
		}
		
		public void add(Long e) {
			checkCopy();
			if (items != null && size() >= blockSize) {
				split(size() / 2);
			}
			
			if (items != null) {
				int limit = items.limit();
				items.limit(limit + valueLength);
				items.position(limit);
				items.putLong(e);
			} else {
				right.add(e);
				if (!balance()) {
					update();
				}
			}
		}

		private void update() {
			_size = left.size() + right.size();
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
				A.update();
				
				B.left = A;
				B.right = c;
				B.update();
				return true;
			} else if (left.height > right.height + 1) {
				Block A = this;
				Block B = left;
				
				Block a = left.left;
				Block b = left.right;
				Block c = right;
				
				B.left = b;
				B.right = c;
				B.update();
				
				A.left = a;
				A.right = B;
				A.update();
				return true;
			}
			
			return false;
		}

		private void checkCopy() {
			if (copyOnWrite) {
				ByteBuffer buffer = ByteBuffer.allocate(blockSize * valueLength);
				items.rewind();
				buffer.put(items);
				buffer.flip();
				items = buffer;
				copyOnWrite = false;
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
	public boolean add(Long e) {
		if (root == null) {
			root = new Block();
		}
		root.add(e);
		return true;
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
			
			for (int i = 0; i < 100; i++) {
				Long value = list.get(i);
				if (value != i) {
					throw new RuntimeException("List value does not match at " + i + ": got " + value);
				}
			}
		}
	}
	
}

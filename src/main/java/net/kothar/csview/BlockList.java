package net.kothar.csview;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

public class BlockList<E> extends AbstractList<E> {
	
	private static final int DEFAULT_BLOCK_SIZE = 10_000;
	Block root;
	int blockSize = DEFAULT_BLOCK_SIZE;
	
	class Block {
		boolean copyOnWrite;
		List<E> items;
		
		Block parent;
		Block left;
		Block right;
		
		private int height = 0;
		private int _size = 0;
		
		public Block() {
			items = new ArrayList<E>(blockSize);
			copyOnWrite = false;
		}
		
		Block(List<E> items, Block parent) {
			this.items = items;
			this.parent = parent;
			copyOnWrite = true;
		}
		
		int size() {
			if (items != null) {
				return items.size();
			} else {
				return _size;
			}
		}
		
		E get(int index) {
			if (items != null) {
				return items.get(index);
			}
			
			if (index < left.size()) {
				return left.get(index);
			} else {
				return right.get(index - left.size());
			}
		}

		void split(int index) {
			_size = items.size();
			left = new Block(items.subList(0, index), this);
			right = new Block(items.subList(index, _size), this);
			
			items = null;
			height = 1;
		}

		public E remove(int index) {
			E value;
			if (items != null) {
				value = items.get(index);
				if (index == 0) {
					items = items.subList(1, items.size());
				} else if (index == items.size() - 1) {
					items = items.subList(0, items.size() - 1);
				} else {
					_size = items.size() - 1;
					left = new Block(items.subList(0, index), this);
					right = new Block(items.subList(index + 1, items.size()), this);
					
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
		
		public void add(E e) {
			checkCopy();
			if (items != null && items.size() >= blockSize) {
				split(items.size() / 2);
			}
			
			if (items != null) {
				items.add(e);
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
				items = new ArrayList<>(items);
				copyOnWrite = false;
			}
		}
	}

	@Override
	public E get(int index) {
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
	public boolean add(E e) {
		if (root == null) {
			root = new Block();
		}
		root.add(e);
		return true;
	}
	
	@Override
	public E remove(int index) {
		if (root == null) {
			throw new IndexOutOfBoundsException();
		}
		return root.remove(index);
	}

	public static class Test {
		public static void main(String[] args) {
			BlockList<Integer> list = new BlockList<>();
			list.blockSize = 2;
			
			for (int i = 0; i < 100; i++) {
				list.add(i);
			}
			
			for (int i = 0; i < 100; i++) {
				Integer value = list.get(i);
				if (value != i) {
					throw new RuntimeException("List value does not match at " + i + ": got " + value);
				}
			}
		}
	}
	
}

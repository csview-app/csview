package net.kothar.csview.index;

import java.util.AbstractList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import net.kothar.csview.index.MappedBufferAllocator.Page;

public class BList implements SortedSet<Long> {
	
	protected Node root;

	protected MappedBufferAllocator allocator;
	
	public BList() {
		this(new MappedBufferAllocator());
	}
	
	public BList(MappedBufferAllocator allocator) {
		this.allocator = allocator;
		root = new Node();
	}

	@Override
	public int size() {
		return root.size;
	}

	@Override
	public boolean isEmpty() {
		return root.size == 0;
	}

	@Override
	public boolean contains(Object o) {
		if (o instanceof Number) {
			return root.contains(((Number) o).longValue());
		}
		
		throw new IllegalArgumentException();
	}

	@Override
	public Iterator<Long> iterator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T[] toArray(T[] a) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean add(Long e) {
		return root.add(e);
	}

	@Override
	public boolean remove(Object o) {
		if (o instanceof Number) {
			return root.remove(((Number) o).longValue());
		}

		throw new IllegalArgumentException();
	}

	@Override
	public boolean containsAll(Collection<?> c) {
		return c.stream().allMatch(this::contains);
	}

	@Override
	public boolean addAll(Collection<? extends Long> c) {
		return c.stream()
			.map(this::add)
			.reduce(false, (a, b) -> a | b);
	}

	@Override
	public boolean retainAll(Collection<?> c) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean removeAll(Collection<?> c) {
		return c.stream()
			.map(this::remove)
			.reduce(false, (a, b) -> a | b);
	}

	@Override
	public void clear() {
		root.free();
		root = new Node();
	}

	@Override
	public Comparator<? super Long> comparator() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<Long> subSet(Long fromElement, Long toElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<Long> headSet(Long toElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public SortedSet<Long> tailSet(Long fromElement) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Long first() {
		return get(0);
	}

	@Override
	public Long last() {
		return get(size() - 1);
	}
	
	public Long get(int i) {
		if (i < 0 || i >= size()) {
			throw new IndexOutOfBoundsException();
		}
		return root.get(i);
	}

	class Node extends AbstractList<Long> {
		
		long start;
		int size;
		int height;
		
		Page data;
		Node left;
		Node right;
		
		public Node() {
			data = allocator.allocate();
		}

		public Node(Page data) {
			this.data = data;
		}

		public void free() {
			if (isInternal()) {
				left.free();
				right.free();
			} else {
				data.free();
			}
		}

		private boolean isInternal() {
			return data == null;
		}
		
		private boolean rebalance(boolean modified) {
			if (!modified) {
				return false;
			}
			
			// Check if heights are out of balance
			
			// Rotate tree
			
			// Update the height of this node
			height = Math.max(left.height, right.height) + 1;
			
			return true;
		}

		public boolean add(long e) {
			if (isInternal()) {
				boolean modified;
				if (e < right.start) {
					modified = left.add(e);
				} else {
					modified = right.add(e);
				}
				return rebalance(modified);
			}
			
			// TODO Auto-generated method stub
			return false;
		}

		public boolean remove(long e) {
			if (isInternal()) {
				boolean modified;
				if (e < right.start) {
					modified = left.remove(e);
				} else {
					modified = right.remove(e);
				}
				return rebalance(modified);
			}
			

			int i = Collections.binarySearch(this, e);
			
			// Copy values right of e to a new page
			right = new Node();
			right.size = size - i;
			
			// Shrink this page
			left = new Node(data);
			left.size = i;
			
			// Split the node
			data = null;
			height = 1;
			size --;
			start = left.start;
			
			return false;
		}

		public boolean contains(long e) {
			if (isInternal()) {
				if (e < right.start) {
					return left.contains(e);
				} else {
					return right.contains(e);
				}
			}
			
			int i = Collections.binarySearch(this, e);
			return i >= 0;
		}

		@Override
		public Long get(int i) {
			if (isInternal()) {
				if (i < left.size) {
					return left.get(i);
				} else {
					return right.get(i - left.size);
				}
			}
			
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int size() {
			return size;
		}
		
	}

	public static class Test {
		public static void main(String[] args) {
			BList list = new BList();
			
			List<Long> values = Arrays.asList(6L, 4L, 7L, 9L);
			
			list.addAll(values);
			
			if (!list.containsAll(values)) {
				throw new RuntimeException("Not all values found in list");
			}
		}
	}
}

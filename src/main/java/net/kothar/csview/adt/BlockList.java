/*
 * Copyright 2016 Kothar Labs
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.kothar.csview.adt;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores a sorted list of elements in a B-Tree-style tree
 * 
 * @author mhouston
 *
 * @param <E>
 * @deprecated
 */
@Deprecated
public class BlockList<E> extends AbstractList<E> {

	private static int BLOCK_SIZE = 10_000;

	static abstract class Block<E, L extends Block<E, L, I>, I> {
		I items;

		L	left;
		L	right;

		protected int	height		= 0;
		protected int	treeSize	= 0;

		@SuppressWarnings("unchecked")
		protected boolean balance() {
			if (right.height > left.height + 1) {
				L A = right;
				L B = (L) this;

				L a = left;
				L b = right.left;
				L c = right.right;

				A.left = a;
				A.right = b;
				A.updateTree();

				B.left = A;
				B.right = c;
				B.updateTree();
				return true;
			} else if (left.height > right.height + 1) {
				L A = (L) this;
				L B = left;

				L a = left.left;
				L b = left.right;
				L c = right;

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

		abstract void appendItem(E e);

		public abstract E remove(int index);

		protected void split() {
			split(countItems());
		}

		protected abstract void split(int index1, int index2);

		protected int size() {
			if (items != null) {
				return countItems();
			} else {
				return treeSize;
			}
		}

		protected abstract int countItems();

		protected void split(int index) {
			split(index, index);
		}

		public void add(int index, E e) {
			if (items != null) {
				if (index == countItems()) {
					append(e);
				} else {
					split(index);
					left.append(e);
					updateTree();
				}
			} else {
				if (index < left.size()) {
					left.add(index, e);
				} else {
					right.add(index - left.size(), e);
				}
			}
		}

		protected void updateTree() {
			treeSize = left.size() + right.size();
			height = Math.max(right.height, left.height) + 1;
		}

		protected void checkMerge() {
			if (treeSize > BLOCK_SIZE / 2 && treeSize < BLOCK_SIZE) {
				merge();
			}
		}

		protected void merge() {
			throw new UnsupportedOperationException();
		}

		protected E get(int index) {
			if (items != null) {
				return getItem(index);
			}

			if (index < left.size()) {
				return left.get(index);
			} else {
				return right.get(index - left.size());
			}
		}

		protected abstract E getItem(int index);

		public void append(E e) {
			if (items != null && countItems() >= BLOCK_SIZE) {
				split();
			}

			if (items != null) {
				appendItem(e);
			} else {
				right.append(e);
				if (!balance()) {
					updateTree();
				}
			}
		}
	}

	Block<E, ?, ?> root;

	static class ArrayListBlock<E> extends Block<E, ArrayListBlock<E>, List<E>> {

		public ArrayListBlock() {
			items = new ArrayList<E>(BLOCK_SIZE);
		}

		ArrayListBlock(List<E> items) {
			this.items = new ArrayList<>(items);
		}

		@Override
		protected int countItems() {
			return items.size();
		}

		@Override
		protected E getItem(int index) {
			return items.get(index);
		}

		@Override
		protected void split(int index1, int index2) {
			left = new ArrayListBlock<>(items.subList(0, index1));
			right = new ArrayListBlock<>(items.subList(index2, items.size()));

			items = null;
			updateTree();
		}

		@Override
		protected void split() {
			left = new ArrayListBlock<>();
			left.items = items;

			right = new ArrayListBlock<>();
			updateTree();
		}

		@Override
		public E remove(int index) {
			E value;
			if (items != null) {
				value = items.get(index);
				if (index == 0) {
					items = items.subList(1, items.size());
				} else if (index == items.size() - 1) {
					items = items.subList(0, items.size() - 1);
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

		@Override
		void appendItem(E e) {
			items.add(e);
		}

		@Override
		protected void merge() {
			ArrayList<E> newItems = new ArrayList<>();
			merge(newItems);

			items = newItems;
			left = null;
			right = null;
			height = 0;
			treeSize = 0;
		}

		private void merge(ArrayList<E> newItems) {
			if (items != null) {
				newItems.addAll(newItems);
			} else {
				left.merge(newItems);
				right.merge(newItems);
			}
		}
	}

	public BlockList() {
		this(new ArrayListBlock<>());
	}

	protected BlockList(Block<E, ?, ?> root) {
		this.root = root;
	}

	@Override
	public E get(int index) {
		return root.get(index);
	}

	@Override
	public int size() {
		return root.size();
	}

	@Override
	public void add(int index, E e) {
		root.add(index, e);
	}

	@Override
	public E remove(int index) {
		return root.remove(index);
	}

	public static class Test {
		public static void main(String[] args) {
			BlockList<Integer> list = new BlockList<>();
			BLOCK_SIZE = 10;

			for (int i = 0; i < 100; i++) {
				list.add(i);
			}
			for (int i = 0; i < 100; i++) {
				list.add(i * 2, i);
			}

			for (int i = 0; i < 100; i++) {
				Integer value = list.get(i * 2);
				if (value != i) {
					throw new RuntimeException("List value does not match at " + i + ": got " + value);
				}
			}

			for (int i = 99; i >= 0; i--) {
				list.remove(i * 2);
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

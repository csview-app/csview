/* Copyright 2016 Kothar Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.kothar.csview.adt;

import java.nio.ByteBuffer;
import java.util.AbstractList;

/**
 * Specialises the behaviour of BlockList by storing positive longs in a byte array;
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

		int height = 0;
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
				try {
					items.position(index * valueLength);
				} catch (IllegalArgumentException e) {
					throw new IndexOutOfBoundsException("Unable to get element at " + index);
				}
				assert items.remaining() >= valueLength;
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
					items.position(valueLength);
					items = items.slice();
				} else if (index == size() - 1) {
					items.limit(items.limit() - valueLength);
				} else {
					split(index, index + 1);
				}
			} else if (index < left.size()) {
				value = left.remove(index);
				cleanTree();
			} else {
				value = right.remove(index - left.size());
				cleanTree();
			}
			return value;
		}

		private void cleanTree() {
			updateTree();
			if (left.size() == 0) {
				items = right.items;
				offset = right.offset;
				valueLength = right.valueLength;
				height = right.height;
				left = right.left;
				right = right.right;
			} else if (right.size() == 0) {
				items = left.items;
				offset = left.offset;
				valueLength = left.valueLength;
				height = left.height;
				right = left.right;
				left = left.left;
			} else {
				balance();
			}
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
				int newLimit = limit + valueLength;
				if (newLimit > items.capacity()) {
					ByteBuffer newItems = ByteBuffer.allocate(blockSize * valueLength);
					items.rewind();
					newItems.put(items);
					items = newItems;
				}
				items.limit(newLimit);
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
			assert items.remaining() / valueLength <= buffer.remaining() / newValueLength;

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

		public int search(long value) {
			// Binary search items
			if (items != null) {
				int min = 0;
				int max = size() - 1;
				
				int pivot = max / 2;
				long pvalue = get(pivot);

				while (min < max) {
					if (pvalue == value) {
						return pivot;
					} else if (pvalue > value) {
						max = pivot - 1;
					} else {
						min = pivot + 1;
					}
					pivot = (min + max) / 2;
					pvalue = get(pivot);
				}

				if (pvalue == value) {
					return pivot;
				}
				
				// Found nearest
				if (pvalue < value)
					return -(pivot + 1) - 1;
				else
					return -pivot - 1;
			}

			// Delegate to branches
			if (right.get(0) > value) {
				return left.search(value);
			} else {
				int rpos = right.search(value);
				if (rpos < 0) {
					return rpos - left.size();
				}
				return rpos + left.size();
			}
		}
	}

	@Override
	public Long get(int index) {
		return root.get(index);
	}

	/**
	 * Looks up the index of the item with value less than or equal to the provided value.
	 * This will only return a valid result if the list contains values in sorted order.
	 * @param value
	 * @return
	 */
	public int search(long value) {
		if (root == null) {
			return -1;
		}

		return root.search(value);
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

	static long readItem(ByteBuffer buffer, long offset, int valueLength) {
		switch (valueLength) {
		case 1:
			return buffer.get() + offset;
		case 2:
			return buffer.getShort() + offset;
		case 3:
			long value = 0xFFFFL & buffer.getShort();
			value |= ((long) buffer.get()) << 16;
			return value + offset;
		case 4:
			return buffer.getInt() + offset;
		case 5:
			value = 0xFFFFFFFFL & buffer.getInt();
			value |= ((long) buffer.get()) << 32;
			return value + offset;
		case 6:
			value = 0xFFFFFFFFL & buffer.getInt();
			value |= ((long) buffer.getShort()) << 32;
			return value + offset;
		case 7:
			value = 0xFFFFFFFFL & buffer.getInt();
			value |= (0xFFFFL & buffer.getShort()) << 32;
			value |= ((long) buffer.get()) << 48;
			return value + offset;
		case 8:
			return buffer.getLong();
		default:
			throw new IllegalArgumentException();
		}
	}

	static void writeItem(long value, ByteBuffer buffer, long offset, int valueLength) {
		switch (valueLength) {
		case 1:
			assert Math.abs(value - offset) <= 1L << 7;
			buffer.put((byte) (value - offset));
			return;
		case 2:
			assert Math.abs(value - offset) <= 1L << 15;
			buffer.putShort((short) (value - offset));
			return;
		case 3:
			assert Math.abs(value - offset) <= 1L << 23;
			value = value - offset;
			buffer.putShort((short) (value & 0xFFFF));
			value >>= 16;
			buffer.put((byte) value);
			return;
		case 4:
			assert Math.abs(value - offset) <= 1L << 31;
			buffer.putInt((int) (value - offset));
			return;
		case 5:
			assert Math.abs(value - offset) <= 1L << 39;
			value = value - offset;
			buffer.putInt((int) (value & 0xFFFFFFFFL));
			value >>= 32;
			buffer.put((byte) value);
			return;
		case 6:
			assert Math.abs(value - offset) <= 1L << 47;
			value = value - offset;
			buffer.putInt((int) (value & 0xFFFFFFFFL));
			value >>= 32;
			buffer.putShort((short) value);
			return;
		case 7:
			assert Math.abs(value - offset) <= 1L << 55;
			value = value - offset;
			buffer.putInt((int) (value & 0xFFFFFFFFL));
			value >>= 32;
			buffer.putShort((short) (value & 0xFFFF));
			value >>= 16;
			buffer.put((byte) value);
			return;
		case 8:
			buffer.putLong(value);
			return;
		default:
			throw new IllegalArgumentException();
		}
	}
}

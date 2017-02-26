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
package net.kothar.csview.csv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.kothar.csview.IndexListener;
import net.kothar.csview.adt.BlockList;
import net.kothar.csview.adt.CompactLongList;

public class Index {
	
	private List<Long> positions = new CompactLongList();
	private ArrayList<IndexListener> listeners = new ArrayList<>();
	long lastPosition;
	
	public Index() {
	}
	
	public Index(List<Long> listImpl) {
		this.positions = listImpl;
	}
	
	public synchronized void add(Long position) {
		if (positions.isEmpty() || position > lastPosition) {
			positions.add(position);
			lastPosition = position;
		} else {
			int line = itemAt(position);
			if (line < 0) {
				positions.add(-line, position);
			} else {
				throw new IllegalArgumentException("Duplicate line position added");
			}
		}
		
		// TODO Fire insertion event if not last item
		// TODO Fire append event otherwise
		for (IndexListener listener: listeners) {
			listener.itemAdded(size() - 1);
		}
	}
	
	/** Find the item at position */
	public synchronized int itemAt(Long position) {
		if (positions instanceof CompactLongList) {
			return ((CompactLongList) positions).search(position);
		}
		return Collections.binarySearch(positions, position);
	}

	/** The position of item */
	public synchronized Long getPosition(int item) {
		if (positions.isEmpty() || item >= size()) {
			return null;
		}
		return positions.get(item);
	}

	public synchronized int size() {
		return positions.size();
	}

	public synchronized void removePosition(Long position) {
		int item = itemAt(position);
		if (item > 0)
			removeItem(item);
	}
	
	public synchronized void removeItem(int item) {
		positions.remove(item);
		
		// TODO Fire removal event
	}
	
	@Override
	public synchronized String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10 && i < positions.size(); i++) {
			sb.append(i + ": " + getPosition(i) + "\n");
		}
		if (positions.size() >= 10) {
			sb.append(" + " + (positions.size() - 10) + " more lines");
		}
		return sb.toString();
	}
	
	public static class Test {
		public static void main(String[] args) {
			
			System.out.println("ArrayList");
			Index map = new Index(new ArrayList<>());
			testMap(map);
			printmem();
			
			System.out.println("\n\nBlockList");
			map = new Index(new BlockList<>());
			Runtime.getRuntime().gc();
			testMap(map);
			printmem();
			
			System.out.println("\n\nLongBlockList");
			map = new Index(new CompactLongList());
			Runtime.getRuntime().gc();
			testMap(map);
			printmem();
		}

		private static void printmem() {
			long free = Runtime.getRuntime().freeMemory();
			long total = Runtime.getRuntime().totalMemory();
			System.out.println("Memory usage pre-GC: " + mb(total - free) + "/" + mb(total));
			Runtime.getRuntime().gc();
			free = Runtime.getRuntime().freeMemory();
			total = Runtime.getRuntime().totalMemory();
			System.out.println("Memory usage post-GC: " + mb(total - free) + "/" + mb(total));
		}
		
		private static String mb(long value) {
			return (value >> 20) + " mB";
		}

		private static void testMap(Index map) {
			long start = System.currentTimeMillis();
			for (long pos = 0; map.size() < 2_000_000; pos += 48) {
				map.add(pos);
			}
			System.out.println("Mapped 2M lines in " + (System.currentTimeMillis() - start)/1000d + "s");
			
			start = System.currentTimeMillis();
			map.removeItem(5);
			System.out.println("Removed line 5 in " + (System.currentTimeMillis() - start) + "ms");

			start = System.currentTimeMillis();
			for (int i = 0; i < 10_000; i ++) {
				map.removeItem((int) (Math.random() * map.size()));
			}
			System.out.println("Removed 10K random lines in " + (System.currentTimeMillis() - start)/1000d + "s");
		}
	}

	public void addListener(IndexListener listener) {
		listeners.add(listener);
	}
}

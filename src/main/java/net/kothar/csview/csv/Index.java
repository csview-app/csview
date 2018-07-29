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
package net.kothar.csview.csv;

import java.util.ArrayList;

import net.kothar.compactlist.CompactList;
import net.kothar.csview.IndexListener;

public class Index {

	private CompactList					positions	= new CompactList();
	private ArrayList<IndexListener>	listeners	= new ArrayList<>();
	long								lastPosition;

	public Index() {
	}

	public synchronized void add(Long position) {
		if (positions.isEmpty() || position > lastPosition) {
			positions.add(position);
			lastPosition = position;
		} else {
			int line = itemAt(position);
			if (line < 0) {
				positions.add(-line - 1, position);
			} else {
				throw new IllegalArgumentException("Duplicate line position added");
			}
		}

		// TODO Fire insertion event if not last item
		// TODO Fire append event otherwise
		for (IndexListener listener : listeners) {
			listener.itemAdded(size() - 1);
		}
	}

	/** Find the item at position */
	public synchronized int itemAt(Long position) {
		return (int) positions.search(position);
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

	public void addListener(IndexListener listener) {
		listeners.add(listener);
	}

	public void clear() {
		positions.clear();
		lastPosition = 0;
	}
}

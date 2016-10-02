package net.kothar.csview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections4.list.TreeList;

public class LineMap {
	
	private List<Long> linePositions = new BlockList<>();
	private ArrayList<RowListener> listeners = new ArrayList<>();
	long lastPosition;
	
	public LineMap() {
	}
	
	public LineMap(List<Long> listImpl) {
		this.linePositions = listImpl;
	}
	
	public void add(Long position) {
		if (linePositions.isEmpty() || position > lastPosition) {
			linePositions.add(position);
			lastPosition = position;
		} else {
			int line = line(position);
			if (line < 0) {
				linePositions.add(-line, position);
			} else {
				throw new IllegalArgumentException("Duplicate line position added");
			}
		}
		
		// TODO Fire insertion event if not last line
		// TODO Fire append event otherwise
		for (RowListener listener: listeners) {
			listener.rowAdded(size() - 1);
		}
	}
	
	/** Find the line at position */
	private int line(Long position) {
		return Collections.binarySearch(linePositions, position);
	}

	/** The position of line */
	public Long getPosition(int line) {
		if (linePositions.isEmpty() || line >= size()) {
			return null;
		}
		return linePositions.get(line);
	}

	public int size() {
		return linePositions.size();
	}

	public void removePosition(Long position) {
		int line = line(position);
		if (line > 0)
			removeLine(line);
	}
	
	public void removeLine(int line) {
		linePositions.remove(line);
		
		// TODO Fire removal event
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 10 && i < linePositions.size(); i++) {
			sb.append(i + ": " + getPosition(i) + "\n");
		}
		if (linePositions.size() >= 10) {
			sb.append(" + " + (linePositions.size() - 10) + " more lines");
		}
		return sb.toString();
	}
	
	public static class Test {
		public static void main(String[] args) {
			
			System.out.println("ArrayList");
			testMap(new LineMap(new ArrayList<>()));
			printmem();
			
			System.out.println("\n\nTreeList");
			testMap(new LineMap(new TreeList<>()));
			printmem();
			
			System.out.println("\n\nBlockList");
			testMap(new LineMap(new BlockList<>()));
			printmem();
		}

		private static void printmem() {
			long free = Runtime.getRuntime().freeMemory();
			long total = Runtime.getRuntime().totalMemory();
			System.out.println("Memory usage: " + (total - free) + "/" + total);
			Runtime.getRuntime().gc();
		}

		private static void testMap(LineMap map) {
			long start = System.currentTimeMillis();
			for (long pos = 0; map.size() < 2_000_000; pos += 48) {
				map.add(pos);
			}
			System.out.println("Mapped 2M lines in " + (System.currentTimeMillis() - start)/1000d + "s");
			
			start = System.currentTimeMillis();
			map.removeLine(5);
			System.out.println("Removed line 5 in " + (System.currentTimeMillis() - start) + "ms");

			start = System.currentTimeMillis();
			for (int i = 0; i < 10_000; i ++) {
				map.removeLine((int) (Math.random() * map.size()));
			}
			System.out.println("Removed 10K random lines in " + (System.currentTimeMillis() - start)/1000d + "s");
		}
	}

	public void addListener(RowListener listener) {
		listeners .add(listener);
	}
}

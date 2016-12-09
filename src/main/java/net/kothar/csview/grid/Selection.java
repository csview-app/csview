package net.kothar.csview.grid;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.graphics.Rectangle;

public class Selection {
	
	Set<Rectangle> selectedRegions = new HashSet<>();
	
	private Rectangle lastRegion;
	
	/**
	 * @param region
	 * @return true if the region was new
	 */
	public boolean addRegion(Rectangle region) {
		lastRegion = region;
		return selectedRegions.add(region);
	}
	
	public void removeRegion(Rectangle region) {
		selectedRegions.remove(region);
	}
	
	public void clear() {
		selectedRegions.clear();
		lastRegion = null;
	}
	
	public boolean isSelected(int col, int row) {
		Rectangle cell = new Rectangle(col, row, 1, 1);
		return selectedRegions.parallelStream().anyMatch(cell::intersects);
	}
	
	public Rectangle getLastRegion() {
		return lastRegion;
	}

	public Rectangle getUnion() {
		Rectangle union = null;
		for (Rectangle region: selectedRegions) {
			if (union == null) {
				union = region;
			} else {
				union = union.union(region);
			}
		}
		return union;
	}
}

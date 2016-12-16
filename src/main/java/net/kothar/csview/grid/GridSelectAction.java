package net.kothar.csview.grid;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

public class GridSelectAction implements MouseAction {

	private Grid grid;
	private int col;
	private int row;
	
	private Rectangle lastSelection;
	private boolean newSelection;
	
	public GridSelectAction(Grid grid, int col, int row) {
		this.grid = grid;
		this.col = col;
		this.row = row;
	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
	}

	private Point getCell(MouseEvent e) {
		int x = getColAt(e.x - grid.getRowHeaderSize() + grid.getXOffset());
		int y = getRowAt(e.y - grid.getColumnHeaderSize() + grid.getYOffset());
		return new Point(x, y);
	}
	
	private int getRowAt(int y) {
		if (y < 0)
			return 0;
		else if (y >= grid.rows.getTotal())
			return grid.rows.getCount() - 1;
		return grid.rows.getItemAt(y);
	}

	private int getColAt(int x) {
		if (x < 0)
			return 0;
		else if (x >= grid.cols.getTotal())
			return grid.cols.getCount() - 1;
		return grid.cols.getItemAt(x);
	}
	
	@Override
	public void mouseDown(MouseEvent e) {
		
		// Extend last selection if SHIFT is held down
		if ((e.stateMask & SWT.MOD2) != 0) {
			lastSelection = grid.selection.getLastRegion();
			if (lastSelection != null) {
				grid.selection.removeRegion(lastSelection);
				lastSelection.add(new Rectangle(col, row, 1, 1));
			}
		}
		
		// Add to existing selection if CMD/CTRL is held down
		if ((e.stateMask & SWT.MOD1) == 0) {
			grid.invalidateTiles(grid.selection.selectedRegions);
			grid.selection.clear();
		} 
		
		if (lastSelection == null)
			lastSelection = new Rectangle(col, row, 1, 1);
		
		newSelection = grid.selection.addRegion(lastSelection);
		grid.setCurrentCell(new Point(col, row));
		grid.invalidateTiles(lastSelection);
	}

	@Override
	public void mouseUp(MouseEvent e) {
		// Handle single cell toggle
		Point cell = getCell(e);
		if (!newSelection && col == cell.x && row == cell.y && (e.stateMask & SWT.MOD1) != 0) {
			grid.selection.removeRegion(lastSelection);
			grid.invalidateTiles(lastSelection);
		}
	}

	@Override
	public void mouseMove(MouseEvent e) {
		Point cell = getCell(e);
		Rectangle selection = new Rectangle(col, row, cell.x - col + 1, cell.y - row + 1);
		if (selection.width <= 0) {
			selection = new Rectangle(selection.x + selection.width - 1, selection.y, 2-selection.width, selection.height);
		}
		if (selection.height <= 0) {
			selection = new Rectangle(selection.x, selection.y + selection.height - 1, selection.width, 2-selection.height);
		}
		if (!selection.equals(lastSelection)) {
			grid.selection.removeRegion(lastSelection);
			grid.selection.addRegion(selection);
			grid.setCurrentCell(cell);

			if (lastSelection.intersects(selection)) {
				Set<Rectangle> invalidRegions = diff(lastSelection, selection);
				if (!invalidRegions.isEmpty()) {
					grid.invalidateTiles(invalidRegions);
				}
			} else {
				grid.invalidateTiles(selection, lastSelection);
			}
			
			lastSelection = selection;
		}
	}
	
	/**
     * Finds the difference between two intersecting rectangles
     * @see http://stackoverflow.com/a/5155851
     * 
     * @param r
     * @param s
     * @return An array of rectangle areas that are covered by either r or s, but
     *         not both
     */
    public static Set<Rectangle> diff( Rectangle r, Rectangle s )
    {
        int a = Math.min( r.x, s.x );
        int b = Math.max( r.x, s.x );
        int c = Math.min( r.x + r.width, s.x + s.width );
        int d = Math.max( r.x + r.width, s.x + s.width );

        int e = Math.min( r.y, s.y );
        int f = Math.max( r.y, s.y );
        int g = Math.min( r.y + r.height, s.y + s.height );
        int h = Math.max( r.y + r.height, s.y + s.height );

        // X = intersection, 0-7 = possible difference areas
        // h +-+-+-+
        // . |5|6|7|
        // g +-+-+-+
        // . |3|X|4|
        // f +-+-+-+
        // . |0|1|2|
        // e +-+-+-+
        // . a b c d

        Set<Rectangle> result = new HashSet<>();

        int w1 = b-a;
        int w2 = c-b;
        int w3 = d-c;
        
        int h1 = h-g;
        int h2 = g-f;
        int h3 = f-e;
        
        // we'll always have rectangles 1, 3, 4 and 6
		result.add(new Rectangle( b, e, w2, h3 ));
		result.add(new Rectangle( a, f, w1, h2 ));
		result.add(new Rectangle( c, f, w3, h2 ));
		result.add(new Rectangle( b, g, w2, h1 ));

        // decide which corners
        if( r.x == a && r.y == e || s.x == a && s.y == e )
        { // corners 0 and 7
        	result.add(new Rectangle( a, e, w1, h3 ));
        	result.add(new Rectangle( c, g, w3, h1 ));
        }
        else
        { // corners 2 and 5
        	result.add(new Rectangle( c, e, w3, h3 ));
        	result.add(new Rectangle( a, g, w1, h1 ));
        }
        
        result.removeIf(x -> x.height == 0 || x.width == 0);

        return result;
    }

	@Override
	public Cursor getCursor(MouseEvent e) {
		return null;
	}

}

package net.kothar.csview.grid;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.widgets.Canvas;

public class MouseHandler implements MouseListener, MouseMoveListener {

	private static final int SEPARATOR_SENSITIVITY = 5;
	private Grid grid;
	private Canvas canvas;
	
	private MouseAction nextAction;
	private MouseAction activeAction;

	public MouseHandler(Grid grid) {
		this.grid = grid;
		this.canvas = grid.canvas;
		
		canvas.addMouseMoveListener(this);
		canvas.addMouseListener(this);
	}

	@Override
	public void mouseMove(MouseEvent e) {
		boolean rowHeaders = e.x <= grid.getRowHeaderSize();
		boolean colHeaders = e.y <= grid.getColumnHeaderSize();

		nextAction = null;

		if (rowHeaders && colHeaders) {
			cornerMouseMove(e);
		} else if (rowHeaders) {
			rowHeadersMouseMove(e);
		} else if (colHeaders) {
			colHeadersMouseMove(e);
		} else {
			cellsMouseMove(e);
		}
		
		if (activeAction != null) {
			activeAction.mouseMove(e);
		} else if (nextAction != null) {
			canvas.setCursor(nextAction.getCursor(e));
		} else {
			canvas.setCursor(null);
		}
	}

	private void cellsMouseMove(MouseEvent e) {
		int mouseX = e.x - grid.getRowHeaderSize() + grid.getXOffset();
		if (mouseX < 0 || mouseX >= grid.cols.getTotal()) {
			return;
		}
		int colIndex = grid.cols.getItemAt(mouseX);
		
		int mouseY = e.y - grid.getColumnHeaderSize() + grid.getYOffset();
		if (mouseY < 0 || mouseY >= grid.rows.getTotal()) {
			return;
		}
		int rowIndex = grid.rows.getItemAt(mouseY);
		
		nextAction = new GridSelectAction(grid, colIndex, rowIndex);
	}

	private void colHeadersMouseMove(MouseEvent e) {
		
		int rowHeaderSize = grid.getRowHeaderSize();
		int mouseX = e.x - rowHeaderSize + grid.getXOffset();
		int colIndex;
		if (mouseX >= grid.cols.getTotal()) {
			colIndex = grid.cols.getCount() -1;
		} else {
			colIndex = grid.cols.getItemAt(mouseX);
		}
		
		int colPos = grid.cols.getPosition(colIndex);
		int colWidth = grid.cols.getSize(colIndex);
		
		if (Math.abs(colPos + colWidth - mouseX) < SEPARATOR_SENSITIVITY) {
			nextAction = new ColResizeAction(grid, colIndex);
		} else if (Math.abs(mouseX - colPos) < SEPARATOR_SENSITIVITY && colIndex > 0) {
			nextAction = new ColResizeAction(grid, colIndex - 1);
		} else if (e.x - rowHeaderSize < SEPARATOR_SENSITIVITY) {
			nextAction = new RowHeaderResizeAction(grid);
		}
	}

	private void rowHeadersMouseMove(MouseEvent e) {

	}

	private void cornerMouseMove(MouseEvent e) {

	}

	@Override
	public void mouseDoubleClick(MouseEvent e) {
		if (nextAction != null) {
			nextAction.mouseDoubleClick(e);
		}
	}

	@Override
	public void mouseDown(MouseEvent e) {
		if (nextAction != null) {
			nextAction.mouseDown(e);
			activeAction = nextAction;
		}
	}

	@Override
	public void mouseUp(MouseEvent e) {
		if (activeAction != null) {
			activeAction.mouseUp(e);
			activeAction = null;
		}
	}

}

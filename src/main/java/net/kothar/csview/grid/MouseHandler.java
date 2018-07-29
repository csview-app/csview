package net.kothar.csview.grid;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.widgets.Canvas;

public class MouseHandler implements MouseListener, MouseMoveListener {

	private static final int	SEPARATOR_SENSITIVITY	= 5;
	protected Grid				grid;
	private Canvas				canvas;

	protected MouseAction	nextAction;
	protected MouseAction	activeAction;

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
			canvas.setCursor(activeAction.getCursor(e));
		} else if (nextAction != null) {
			canvas.setCursor(nextAction.getCursor(e));
		} else {
			canvas.setCursor(null);
		}
	}

	protected void cellsMouseMove(MouseEvent e) {
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

		nextAction = createCellAction(colIndex, rowIndex);
	}

	protected MouseAction createCellAction(int colIndex, int rowIndex) {
		return new GridSelectAction(grid, colIndex, rowIndex);
	}

	protected void colHeadersMouseMove(MouseEvent e) {

		int rowHeaderSize = grid.getRowHeaderSize();
		int mouseX = e.x - rowHeaderSize + grid.getXOffset();
		int colIndex;
		if (mouseX >= grid.cols.getTotal()) {
			colIndex = grid.cols.getCount() - 1;
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
		} else {
			nextAction = createColumnHeaderAction(grid, colIndex);
		}
	}

	protected MouseAction createColumnHeaderAction(Grid grid, int colIndex) {
		return null;
	}

	protected void rowHeadersMouseMove(MouseEvent e) {
		int colHeaderSize = grid.getColumnHeaderSize();
		int mouseY = e.y - colHeaderSize + grid.getYOffset();
		int rowIndex;
		if (mouseY >= grid.rows.getTotal()) {
			rowIndex = grid.rows.getCount() - 1;
		} else {
			rowIndex = grid.rows.getItemAt(mouseY);
		}

		int rowPos = grid.rows.getPosition(rowIndex);
		int rowHeight = grid.rows.getSize(rowIndex);

		if (Math.abs(rowPos + rowHeight - mouseY) < SEPARATOR_SENSITIVITY) {
			nextAction = createRowResizeAction(grid, rowIndex);
		} else if (Math.abs(mouseY - rowPos) < SEPARATOR_SENSITIVITY && rowIndex > 0) {
			nextAction = createRowResizeAction(grid, rowIndex - 1);
		} else if (e.x - colHeaderSize < SEPARATOR_SENSITIVITY) {
			nextAction = createColHeaderResizeAction(grid);
		} else {
			nextAction = createRowHeaderAction(grid, rowIndex);
		}
	}

	protected MouseAction createRowHeaderAction(Grid grid, int rowIndex) {
		return null;
	}

	protected MouseAction createColHeaderResizeAction(Grid grid) {
		return null;
	}

	protected MouseAction createRowResizeAction(Grid grid, int rowIndex) {
		return null;
	}

	protected void cornerMouseMove(MouseEvent e) {

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

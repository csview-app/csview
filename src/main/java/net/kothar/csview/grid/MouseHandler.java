package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.widgets.Canvas;

public class MouseHandler implements MouseListener, MouseMoveListener {

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

		canvas.setCursor(null);

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
		}
	}

	private void cellsMouseMove(MouseEvent e) {
		// TODO Auto-generated method stub

	}

	private void colHeadersMouseMove(MouseEvent e) {
		canvas.setCursor(e.display.getSystemCursor(SWT.CURSOR_SIZEWE));
	}

	private void rowHeadersMouseMove(MouseEvent e) {
		canvas.setCursor(e.display.getSystemCursor(SWT.CURSOR_SIZENS));
	}

	private void cornerMouseMove(MouseEvent e) {
		// TODO Auto-generated method stub

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

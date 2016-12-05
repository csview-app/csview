package net.kothar.csview.grid;

import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.graphics.Cursor;

public interface MouseAction extends MouseListener, MouseMoveListener {

	Cursor getCursor(MouseEvent e);

}

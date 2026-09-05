package net.kothar.csview.grid;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * While a large file is scanned the grid gets laid out from inside a cascade that, on macOS, can
 * report a client area starting above (0,0). A layout that anchored the canvas at that origin put
 * the column header up behind the title bar, and since the grid is not laid out again until the
 * scan ends, it stayed there for the whole scan.
 */
public class GridCanvasLayoutTest {

    private Shell shell;
    private Rectangle clientAreaOverride;

    /** Lets a test hand the layout the displaced client area macOS produces mid-cascade. */
    private class TestGrid extends Grid {
        TestGrid(Composite parent, int style) {
            super(parent, style);
        }

        @Override
        public Rectangle getClientArea() {
            return clientAreaOverride != null ? clientAreaOverride : super.getClientArea();
        }
    }

    @Before
    public void createShell() {
        shell = new Shell();
        shell.setSize(800, 600);
    }

    @After
    public void disposeShell() {
        if (shell != null && !shell.isDisposed()) {
            shell.dispose();
        }
    }

    @Test
    public void canvasFillsTheGridWhenTheClientAreaIsWhereItShouldBe() {
        TestGrid grid = new TestGrid(shell, SWT.NORMAL);
        grid.setSize(400, 300);
        grid.layout(true);

        Rectangle area = grid.getClientArea();
        assertEquals("canvas bounds", new Rectangle(0, 0, area.width, area.height),
                grid.canvas.getBounds());
    }

    @Test
    public void canvasStaysAtTheTopWhenTheClientAreaOriginIsDisplaced() {
        TestGrid grid = new TestGrid(shell, SWT.NORMAL);
        grid.setSize(400, 300);

        // What macOS reports mid-cascade: right size, origin 8px above where it belongs
        clientAreaOverride = new Rectangle(0, -8, 400, 300);
        grid.layout(true);

        assertEquals("canvas must not follow the displaced origin",
                new Rectangle(0, 0, 400, 300), grid.canvas.getBounds());
    }
}

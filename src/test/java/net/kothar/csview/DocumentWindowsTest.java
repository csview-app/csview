package net.kothar.csview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocumentWindowsTest {

    private Display display;
    private DocumentWindows windows;

    @Before
    public void setUp() {
        display = Display.getDefault();
        windows = new DocumentWindows(display);
    }

    @After
    public void tearDown() {
        // Other tests make their windows on the default display; Display.getDefault() makes a
        // new one for them once this one is gone.
        if (!display.isDisposed()) {
            display.dispose();
        }
    }

    @Test
    public void keepsRunningWhileAWindowIsOpen() {
        Shell first = open();
        Shell second = open();
        assertEquals(2, windows.count());

        first.close();

        assertEquals(1, windows.count());
        assertFalse(display.isDisposed());
        assertFalse(second.isDisposed());
    }

    @Test
    public void endsWhenTheLastWindowCloses() {
        open().close();

        assertEquals(0, windows.count());
        assertTrue(display.isDisposed());
    }

    @Test
    public void endsWhenTheDisplayIsDisposedWithWindowsOpen() {
        // What SWT does on Windows when the session ends or an installer's Restart Manager closes
        // the application. Disposing the display again from the last window's dispose listener
        // left Display.release() looping for ever, so this call never returned.
        Shell first = open();
        Shell second = open();

        display.dispose();

        assertTrue(display.isDisposed());
        assertTrue(first.isDisposed());
        assertTrue(second.isDisposed());
        assertEquals(0, windows.count());
    }

    private Shell open() {
        Shell shell = new Shell(display);
        windows.track(shell);
        shell.open();
        return shell;
    }
}

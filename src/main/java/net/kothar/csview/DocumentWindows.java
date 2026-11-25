package net.kothar.csview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

/**
 * Counts the open document windows, and disposes the display when the last of them closes, which
 * ends the event loop and so the application.
 *
 * <p>The display is only disposed when a window closes on its own. When the display is already
 * being disposed - on Windows, when the session ends or an installer's Restart Manager asks the
 * application to close - disposing the display disposes each window in turn, and disposing it again
 * from inside that left SWT's Display.release() looping on SWTExceptions for ever: the process
 * never exited, and the heap filled with the exceptions.
 */
public class DocumentWindows {

    private final Display display;
    private boolean displayDisposing;
    private int open;

    public DocumentWindows(Display display) {
        this.display = display;
        display.addListener(SWT.Dispose, event -> displayDisposing = true);
    }

    /** Counts {@code shell} as open until it is disposed. */
    public void track(Shell shell) {
        open++;
        shell.addDisposeListener(event -> {
            if (--open == 0 && !displayDisposing) {
                display.dispose();
            }
        });
    }

    public int count() {
        return open;
    }
}

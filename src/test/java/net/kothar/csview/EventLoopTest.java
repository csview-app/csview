package net.kothar.csview;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.eclipse.swt.SWTException;
import org.eclipse.swt.widgets.Display;
import org.junit.After;
import org.junit.Test;

public class EventLoopTest {

    private final Display display = Display.getDefault();

    @After
    public void tearDown() {
        if (!display.isDisposed()) {
            display.dispose();
        }
    }

    @Test
    public void endsQuietlyWhenTheDisplayIsDisposedMidDispatch() {
        // Stands in for Windows handling a sent WM_ENDSESSION inside readAndDispatch: the display
        // is disposed there, and SWT then throws as it finishes the call.
        display.asyncExec(() -> {
            display.dispose();
            throw new NullPointerException("msg is null");
        });

        BaseLoader.runUntilDisposed(display);

        assertTrue(display.isDisposed());
    }

    @Test
    public void passesOnErrorsWhileTheDisplayIsAlive() {
        display.asyncExec(() -> {
            throw new IllegalStateException("a real bug");
        });

        try {
            BaseLoader.runUntilDisposed(display);
            fail("the loop swallowed an exception from a live display");
        } catch (SWTException | IllegalStateException expected) {
        }
    }
}

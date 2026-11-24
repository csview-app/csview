package net.kothar.csview;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.widgets.Display;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;

public abstract class BaseLoader implements ApplicationActions {

    public static final String APP_NAME = "CSView";

    protected Display display;

    private File logFile;

    public void start(String[] args) {
        Display.setAppName(APP_NAME);

        try {
            logFile = File.createTempFile(APP_NAME, ".log");
            FileOutputStream logOut = new FileOutputStream(logFile);
            PrintStream realOut = System.out;
            PrintStream realErr = System.err;

            System.out.println("Logging to " + logFile);
            PrintStream log = new PrintStream(new OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    logOut.write(b);
                    realOut.write(b);
                }

                @Override
                public void write(byte[] b) throws IOException {
                    logOut.write(b);
                    realOut.write(b);
                }

                @Override
                public void close() throws IOException {
                    logOut.close();
                    System.setOut(realOut);
                    System.setErr(realErr);
                }
            });

            System.setOut(log);
            System.setErr(log);

            System.out.println("\nStarted new session: " + new Date());

            for (Map.Entry<Object, Object> prop : System.getProperties().entrySet()) {
                System.out.println(prop.getKey() + "=" + prop.getValue());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread.setDefaultUncaughtExceptionHandler(this::handleUnexpectedException);
        display = Display.getDefault();
    }

    protected void displayLoop() {
        runUntilDisposed(display);
    }

    /**
     * Runs the event loop until the display is disposed.
     *
     * <p>On Windows a sent WM_ENDSESSION (the session ending, or an installer's Restart Manager
     * closing the application) is handled inside readAndDispatch, by disposing the display, and
     * SWT 3.134 then reads the message it has already released and throws a NullPointerException
     * from Display.filterMessage. Left to the uncaught exception handler, that opened an
     * "Unexpected error" dialog which kept the process alive, so an installer could not replace
     * it. An exception from a display that is already disposed is that, and is ignored.
     */
    public static void runUntilDisposed(Display display) {
        while (!display.isDisposed()) {
            try {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            } catch (RuntimeException e) {
                if (!display.isDisposed()) {
                    throw e;
                }
            }
        }
    }

    private void handleUnexpectedException(Thread t, Throwable e) {
        e.printStackTrace();

        String message = e.getLocalizedMessage();
        if (logFile != null) {
            message += "\n\nFull log can be found at " + logFile;
        }

        String stack;
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        e.printStackTrace(new PrintStream(bs, true, StandardCharsets.UTF_8));
        stack = bs.toString(StandardCharsets.UTF_8);

        String[] stackLines = stack.split("\n");
        IStatus[] children = Arrays.stream(stackLines)
                .map((line) -> new Status(Status.ERROR, "net.kothar.csview", line))
                .toArray(IStatus[]::new);

        ErrorDialog.openError(null, "Unexpected error", message,
                new MultiStatus("net.kothar.csview", Status.ERROR, children, e.getLocalizedMessage(), e));

        System.exit(-1);
    }
}

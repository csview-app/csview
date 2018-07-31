package net.kothar.csview;

import net.kothar.csview.ui.Menus;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import java.io.*;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseLoader implements ApplicationActions {

    public static final String APP_NAME = "CSView";
    public static final String VERSION = "1.3.0";

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

            for (Map.Entry<Object, Object> prop: System.getProperties().entrySet()) {
                System.out.println(prop.getKey() + "=" + prop.getValue());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Thread.setDefaultUncaughtExceptionHandler(this::handleUnexpectedException);
        display = Display.getDefault();
    }

    protected void displayLoop() {
        while (!display.isDisposed()) {
            if (!display.readAndDispatch()) {
                display.sleep();
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
        try {
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bs, true, "UTF-8"));
            stack = bs.toString("UTF-8");
        } catch (UnsupportedEncodingException e1) {
            stack = e.getLocalizedMessage();
        }

        String[] stackLines = stack.split("\n");
        IStatus[] children = Arrays.stream(stackLines)
                .map((line) -> new Status(Status.ERROR, "net.kothar.csview", line))
                .collect(Collectors.toList())
                .toArray(new IStatus[0]);

        ErrorDialog.openError(null, "Unexpected error", message,
                new MultiStatus("net.kothar.csview", Status.ERROR, children, e.getLocalizedMessage(), e));

        System.exit(-1);
    }
}

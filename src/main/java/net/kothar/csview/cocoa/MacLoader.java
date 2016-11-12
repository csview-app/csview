/* Copyright 2016 Kothar Labs

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package net.kothar.csview.cocoa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;

import net.kothar.csview.CSView;
import net.kothar.csview.Menus;

public class MacLoader {

	static final String APP_NAME = "CSView";

	private static Display display;

	private static File logFile;

	public static void main(String[] args) {

		Display.setAppName(APP_NAME);
		new CocoaUIEnhancer().earlyStartup();

		try {
			logFile = File.createTempFile(APP_NAME, ".log");
			System.out.println("Logging to " + logFile);
			PrintStream log = new PrintStream(logFile);
			System.setOut(log);
			System.setErr(log);

			System.out.println("\nStarted new session: " + new Date());

			for (Entry<Object, Object> prop: System.getProperties().entrySet()) {
				System.out.println(prop.getKey() + "=" + prop.getValue());
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		display = Display.getDefault();
		display.addListener(SWT.OpenDocument, new Listener() {
			@Override
			public void handleEvent(Event event) {
				String filename = event.text;
				File file = new File(filename);
				openFile(filename, file);
			}
		});

		Menu menuBar = display.getMenuBar();
		Menus.createFileMenu(menuBar);
		if (System.getProperties().containsKey("net.kothar.csview.debug")) {
			Menus.createDebugMenu(menuBar);
		}

		Thread.setDefaultUncaughtExceptionHandler(MacLoader::handleUnexpectedException);
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}

	private static void handleUnexpectedException(Thread t, Throwable e) {
		e.printStackTrace();
		String message = e.getLocalizedMessage();

		if (logFile != null) {
			message += "\n\nFull log can be found at " + logFile;
		}

		ErrorDialog.openError(null, "Unexpected error", "An unexpected error was caught", createMultiStatus(e.getLocalizedMessage(), e));
		System.exit(-1);
	}

	private static MultiStatus createMultiStatus(String msg, Throwable t) {

		List<Status> childStatuses = new ArrayList<>();
		StackTraceElement[] stackTraces = Thread.currentThread().getStackTrace();

		for (StackTraceElement stackTrace: stackTraces) {
			Status status = new Status(IStatus.ERROR,
					"net.kothar.csview", stackTrace.toString());
			childStatuses.add(status);
		}

		MultiStatus ms = new MultiStatus("net.kothar.csview",
				IStatus.ERROR, childStatuses.toArray(new Status[] {}),
				t.toString(), t);
		return ms;
	}

	public static void openFile(String filename, File file) {
		if (file.exists()) {
			CSView view = new CSView(file);
			view.open();
		} else {
			Shell shell = new Shell(display);
			shell.setText("File not found: " + filename);
			shell.open();
		}
	}
}

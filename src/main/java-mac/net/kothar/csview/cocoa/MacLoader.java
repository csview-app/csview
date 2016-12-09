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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;

import net.kothar.csview.ApplicationActions;
import net.kothar.csview.ui.CSView;
import net.kothar.csview.ui.Menus;

public class MacLoader implements ApplicationActions {

	static final String APP_NAME = "CSView";
	static final String VERSION = "1.1.1";

	private Display display;

	private File logFile;

	public static void main(String[] args) {
		new MacLoader().start(args);
	}
	
	public void start(String[] args) {
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

		new Menus(this, display.getMenuBar());

		Thread.setDefaultUncaughtExceptionHandler(this::handleUnexpectedException);
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

	public void openFile(String filename, File file) {
		if (file.exists()) {
			openFile(file);
		} else {
			MessageDialog.openError(null, "File not found", filename);
		}
	}

	@Override
	public void openFile(File file) {
		CSView view = new CSView(file);
		view.addMenuBar();
		view.open();
		
		new Menus(this, view, view.getMenuBarManager().getMenu());
	}
}

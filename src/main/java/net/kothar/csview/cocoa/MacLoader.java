package net.kothar.csview.cocoa;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Date;
import java.util.Map.Entry;

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

	public static void main(String[] args) {
		
		Display.setAppName(APP_NAME);
		new CocoaUIEnhancer().earlyStartup();
		
		try {
			File logFile = File.createTempFile(APP_NAME, ".log");
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
		
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
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

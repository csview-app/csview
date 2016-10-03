package net.kothar.csview;

import java.io.File;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

public class MacLoader {
	private static Display display;

	public static void main(String[] args) {
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
		
		MenuItem fileMenu = new MenuItem(menuBar, SWT.DROP_DOWN);
		fileMenu.setText("File");
		
		Menu menu = new Menu(fileMenu);
		MenuItem open = new MenuItem(menu, SWT.NORMAL);
		open.setText("Open file");
		
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

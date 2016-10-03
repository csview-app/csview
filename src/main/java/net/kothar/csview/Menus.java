package net.kothar.csview;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

public class Menus {

	public static void createFileMenu(Menu menuBar) {
		MenuItem file = new MenuItem(menuBar, SWT.CASCADE);
		file.setText("File");
		
		Menu fileMenu = new Menu(menuBar);
		file.setMenu(fileMenu);
		MenuItem open = new MenuItem(fileMenu, SWT.NORMAL);
		open.setText("Open file");
		open.setAccelerator(SWT.MOD1 + 'O');
		
		open.addSelectionListener(adapt(Commands::openFile));
	}

	private static SelectionListener adapt(Runnable action) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		};
	}
}

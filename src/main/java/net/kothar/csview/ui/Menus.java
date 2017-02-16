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
package net.kothar.csview.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import net.kothar.csview.ApplicationActions;
import net.kothar.csview.DocumentActions;

public class Menus {

	private ApplicationActions actions;
	private DocumentActions docActions;
	private Commands commands;

	public Menus(ApplicationActions actions, Menu menuBar) {
		this.actions = actions;
		commands = new Commands(actions);
		
		createFileMenu(menuBar);
		if (System.getProperties().containsKey("net.kothar.csview.debug")) {
			createDebugMenu(menuBar);
		}
	}

	public Menus(ApplicationActions actions, DocumentActions docActions, Menu menuBar) {
		this.actions = actions;
		this.docActions = docActions;
		commands = new Commands(actions);
		
		Menu fileMenu = createFileMenu(menuBar);
		addDocumentFileActions(fileMenu);
		
		createSelectionMenu(menuBar);
		
		if (System.getProperties().containsKey("net.kothar.csview.debug")) {
			createDebugMenu(menuBar);
		}
	}

	private Menu createSelectionMenu(Menu menuBar) {
		MenuItem menuItem = new MenuItem(menuBar, SWT.CASCADE);
		menuItem.setText("Selection");
		
		Menu menu = new Menu(menuBar);
		menuItem.setMenu(menu);
		
		MenuItem copy = new MenuItem(menu, SWT.NORMAL);
		copy.setText("Copy");
		copy.setAccelerator(SWT.MOD1 + 'C');
		
		copy.addSelectionListener(adapt(docActions::copySelection));
		
		return menu;
	}

	private void addDocumentFileActions(Menu fileMenu) {
		
		MenuItem close = new MenuItem(fileMenu, SWT.NORMAL);
		close.setText("Close file");
		close.setAccelerator(SWT.MOD1 + 'W');
		
		close.addSelectionListener(adapt(docActions::close));
		
	}

	public Menu createFileMenu(Menu menuBar) {
		MenuItem file = new MenuItem(menuBar, SWT.CASCADE);
		file.setText("File");
		
		Menu fileMenu = new Menu(menuBar);
		file.setMenu(fileMenu);
		
		MenuItem open = new MenuItem(fileMenu, SWT.NORMAL);
		open.setText("Open file");
		open.setAccelerator(SWT.MOD1 + 'O');
		
		open.addSelectionListener(adapt(commands::openFile));
		
		return fileMenu;
	}

	private SelectionListener adapt(Runnable action) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		};
	}

	public void createDebugMenu(Menu menuBar) {
		MenuItem debug = new MenuItem(menuBar, SWT.CASCADE);
		debug.setText("Debug");
		
		Menu debugMenu = new Menu(menuBar);
		debug.setMenu(debugMenu);
		
		// Throw exception
		MenuItem exception = new MenuItem(debugMenu, SWT.NORMAL);
		exception.setText("Throw exception");
		exception.setAccelerator(SWT.MOD1 + 'E');
		
		exception.addSelectionListener(adapt(() -> {
			throw new RuntimeException("Test exception", new IllegalArgumentException("Internal exception"));
		}));
		
		// Show search
		if (docActions != null) {
			MenuItem search = new MenuItem(debugMenu, SWT.NORMAL);
			search.setText("Show search sidebar");
			search.setAccelerator(SWT.MOD1 + 'F');

			search.addSelectionListener(adapt(docActions::showSearch));
		}
	}
}

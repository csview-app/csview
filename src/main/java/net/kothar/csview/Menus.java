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

	public static void createDebugMenu(Menu menuBar) {
		MenuItem debug = new MenuItem(menuBar, SWT.CASCADE);
		debug.setText("Debug");
		
		Menu debugMenu = new Menu(menuBar);
		debug.setMenu(debugMenu);
		MenuItem exception = new MenuItem(debugMenu, SWT.NORMAL);
		exception.setText("Throw exception");
		exception.setAccelerator(SWT.MOD1 + 'E');
		
		exception.addSelectionListener(adapt(() -> {
			throw new RuntimeException("Test exception");
		}));
	}
}

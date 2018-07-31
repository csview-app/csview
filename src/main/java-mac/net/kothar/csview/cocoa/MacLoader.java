/* Copyright 2016 - 2018 Kothar Labs

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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

public class MacLoader extends BaseLoader {

	public static void main(String[] args) {
		new MacLoader().start(args);
	}

	@Override
	public void start(String[] args) {
		new CocoaUIEnhancer().earlyStartup();

		super.start(args);
		display.addListener(SWT.OpenDocument, new Listener() {
			@Override
			public void handleEvent(Event event) {
				String filename = event.text;
				File file = new File(filename);
				openFile(filename, file);
			}
		});

		new Menus(this, display.getMenuBar());

		displayLoop();
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

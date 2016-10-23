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

import java.io.File;

import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;

public class Commands {

	public static void openFile() {
		Shell shell = new Shell();
		FileDialog dialog = new FileDialog(shell);
		dialog.setFilterExtensions(new String[] {"*.csv"});
		dialog.setText("Select CSV file to open");
		String filename = dialog.open();
		if (filename != null) {
			File file = new File(filename);
			if (file.exists()) {
				new CSView(file).open();
			}
		}
	}

}

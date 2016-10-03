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

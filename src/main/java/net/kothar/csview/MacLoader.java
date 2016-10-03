package net.kothar.csview;

import java.io.File;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;

public class MacLoader {
	private static Display display;

	public static void main(String[] args) {
		display = Display.getDefault();
		Shell shell = new Shell(display);
		shell.setText("Started SWT");
		shell.setSize(200, 100);
		display.asyncExec(shell::open);
		
		display.asyncExec(new Runnable() {
			@Override
			public void run() {
				Application app = Application.getApplication();
				System.out.println("Got application: " + app);
				
				app.removeAboutMenuItem();
				app.removePreferencesMenuItem();
				
				app.addApplicationListener(new ApplicationAdapter() {
					@Override
					public void handleOpenFile(ApplicationEvent event) {
						super.handleOpenFile(event);
						
						String filename = event.getFilename();
						File file = new File(filename);
						if (file.exists()) {
							CSView view = new CSView(file);
							display.asyncExec(view::open);
						} else {
							Shell shell = new Shell(display);
							shell.setText("File not found");
							display.asyncExec(shell::open);
						}
					}
					
					@Override
					public void handleOpenApplication(ApplicationEvent e) {
						super.handleOpenApplication(e);
					}
				});
				System.out.println("Added event listener");
			}
		});
		displayLoop(display);
	}
	
	public static void displayLoop(Display display) {
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
}

package net.kothar.csview;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.eclipse.swt.widgets.Menu;

import net.kothar.csview.ui.CSView;
import net.kothar.csview.ui.Commands;
import net.kothar.csview.ui.Menus;

public class SingleInstanceLoader extends BaseLoader {

	private static final InstanceChannel instances = InstanceChannel.forCurrentUser();

	private int openDocuments = 0;

	public static void main(String[] args) {
		// The running instance has its own working directory, so relative paths are resolved here.
		List<String> paths = Arrays.stream(args).map(arg -> new File(arg).getAbsolutePath()).toList();
		if (instances.send(paths)) {
			System.out.println("Opened in single process");
			return;
		}

		// Absolute here too, so the first window's title and Window menu entry show the full path
		// as the windows opened by later launches do.
		new SingleInstanceLoader().start(paths.toArray(String[]::new));
	}

	@Override
	public void start(String[] args) {

		super.start(args);

		try {
			instances.listen(this::openFromAnotherLaunch);
		} catch (IOException e) {
			System.err.println("Later launches will open in their own process: " + e);
		}

		if (open(args)) {
			displayLoop();
		}
	}

	private boolean open(String[] args) {
		System.out.println("Open " + Arrays.asList(args));
		
		if (args.length == 0) {
			if (!new Commands(this).openFile()) {
				if (openDocuments == 0) {
					return false;
				}
			}
		} else {
			display.asyncExec(() -> {
				for (String string: args) {
					File file = new File(string);
					if (file.exists() && file.isFile()) {
						openFile(file);
					}
				}
			});
		}
		
		return true;
	}

	private void openFromAnotherLaunch(List<String> paths) {
		// This arrives on the channel's thread, and SWT only lets the UI thread open windows or the
		// file dialog (which a launch with no files asks for).
		display.asyncExec(() -> open(paths.toArray(String[]::new)));
	}

	@Override
	public void openFile(File file) {
		openDocuments++;
		
		CSView csView = new CSView(file);

		csView.addMenuBar();
		csView.useAppIcon();
		csView.open();
		csView.getShell().forceActive();

		Menu menu = csView.getMenuBarManager().getMenu();
		new Menus(this, csView, menu);

		csView.getShell().addDisposeListener(e -> {
			if (--openDocuments == 0) {
				e.display.dispose();
			}
		});
	}

}

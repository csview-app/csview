package net.kothar.csview.ui;

import java.util.TimerTask;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Display;

public class Adapters {

	public static SelectionListener select(Runnable action) {
		return new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				action.run();
			}
		};
	}

	public static TimerTask task(Runnable action) {
		return new TimerTask() {
			@Override
			public void run() {
				action.run();
			}
		};
	}

	public static TimerTask displayTask(Display display, Runnable action) {
		return new TimerTask() {
			@Override
			public void run() {
				display.asyncExec(action);
			}
		};
	}

}

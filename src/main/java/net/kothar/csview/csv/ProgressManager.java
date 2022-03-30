package net.kothar.csview.csv;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.StatusLineManager;
import org.eclipse.swt.widgets.Display;

public class ProgressManager {
	
	private StatusLineManager statusLineManager;
	private Display display;

	public ProgressManager(StatusLineManager statusLineManager, Display display) {
		this.statusLineManager = statusLineManager;
		this.display = display;
	}

	IProgressMonitor getProgressMonitor() {
		IProgressMonitor monitor = statusLineManager.getProgressMonitor();
		return new IProgressMonitor() {
			
			@Override
			public void worked(int arg0) {
				display.asyncExec(() -> monitor.worked(arg0));
			}
			
			@Override
			public void subTask(String arg0) {
				display.asyncExec(() -> monitor.subTask(arg0));
			}
			
			@Override
			public void setTaskName(String arg0) {
				display.asyncExec(() -> monitor.setTaskName(arg0));
			}
			
			@Override
			public void setCanceled(boolean arg0) {
				display.asyncExec(() -> monitor.setCanceled(arg0));
			}
			
			@Override
			public boolean isCanceled() {
				return monitor.isCanceled();
			}
			
			@Override
			public void internalWorked(double arg0) {
				display.asyncExec(() -> monitor.internalWorked(arg0));
			}
			
			@Override
			public void done() {
				display.asyncExec(monitor::done);
			}
			
			@Override
			public void beginTask(String arg0, int arg1) {
				display.asyncExec(() -> monitor.beginTask(arg0, arg1));
			}
		};
	}
}

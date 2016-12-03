package net.kothar.csview.grid;

import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class ShowGrid extends ApplicationWindow {
	
	public ShowGrid(Shell parentShell) {
		super(parentShell);
	}

	public static void main(String[] args) {
		ShowGrid grid = new ShowGrid(null);
		grid.open();
		
		Shell shell = grid.getShell();
		Display display = shell.getDisplay();
		shell.addDisposeListener(e -> display.dispose());
		
		while (!display.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
	}
	
	@Override
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		
		shell.setSize(800,  600);
	}
	
	@Override
	protected Control createContents(Composite parent) {
		Composite contents = (Composite) super.createContents(parent);
		contents.setLayout(new FillLayout());
		Grid grid = new Grid(contents, SWT.NORMAL);
		
		for (int i = 0; i < 10_000; i++) {
			grid.addRow(Grid.DEFAULT);
			grid.addCol(Grid.DEFAULT);
		}
		
		return contents;
	}
	
}

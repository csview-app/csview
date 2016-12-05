package net.kothar.csview.grid;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class TestGrid extends ApplicationWindow {
	
	public TestGrid(Shell parentShell) {
		super(parentShell);
	}

	public static void main(String[] args) {
		TestGrid grid = new TestGrid(null);
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
		
		grid.setCols(1_000_000);
		grid.setRows(1_000_000);
		
		grid.setContentProvider(i -> i);
		grid.setLabelProvider(new ITableLabelProvider() {
			
			@Override
			public void removeListener(ILabelProviderListener arg0) {
			}
			
			@Override
			public boolean isLabelProperty(Object arg0, String arg1) {
				return false;
			}
			
			@Override
			public void dispose() {
			}
			
			@Override
			public void addListener(ILabelProviderListener arg0) {
			}
			
			@Override
			public String getColumnText(Object row, int col) {
				return col + "," + row;
			}
			
			@Override
			public Image getColumnImage(Object arg0, int arg1) {
				return null;
			}
		});
		
		return contents;
	}
	
}

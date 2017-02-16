package net.kothar.csview.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

public class SearchSidebar extends Composite {

	public SearchSidebar(Composite parent, int style) {
		super(parent, style);
		
		createContents();
	}

	private void createContents() {
		setLayout(new GridLayout(1, false));
		
		Label title = new Label(this, SWT.NORMAL);
		title.setText("Search");
		title.setBackground(getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND));
		title.setForeground(getDisplay().getSystemColor(SWT.COLOR_TITLE_FOREGROUND));
	}
	
}

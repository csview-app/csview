package net.kothar.csview.ui;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.StatusLineLayoutData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;

import com.google.common.base.Objects;

public class StatusLineMenuContribution extends ControlContribution {

	private CLabel label;
	private String text;
	private MenuManager menuManager;
	
	private int widthHint = -1;
	private int heightHint = -1;
	
	public StatusLineMenuContribution(String id, String text) {
		super(id);
		this.setText(text);
		
		menuManager = new MenuManager();
	}

	@Override
	protected Control createControl(Composite parent) {
		
		Label sep = new Label(parent, SWT.SEPARATOR);
		
		label = new CLabel(parent, SWT.SHADOW_NONE);
		label.setText(text);
		label.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				Menu menu = menuManager.createContextMenu(label);
				menu.setVisible(true);
			}
		});

		
		// Set layout
		GC gc = new GC(parent);
		gc.setFont(parent.getFont());
		FontMetrics fm = gc.getFontMetrics();
		heightHint = fm.getHeight();
		gc.dispose();
		
		StatusLineLayoutData layoutData = new StatusLineLayoutData();
		layoutData.heightHint = heightHint;
		sep.setLayoutData(layoutData);
		
		layoutData = new StatusLineLayoutData();
		layoutData.widthHint = widthHint;
		label.setLayoutData(layoutData);
		return label;
		
	}
	
	public String getText() {
		return text;
	}

	public void setText(String text) {
		if (Objects.equal(text, this.text))
			return;
		
		this.text = text;
		if (label != null) {
			label.setText(text);
			IContributionManager parent = getParent();
			if (parent != null)
				parent.update(true);
		}
	}

	public MenuManager getMenuManager() {
		return menuManager;
	}
}

package net.kothar.csview.ui;

import java.text.NumberFormat;

import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.swt.graphics.Image;

public class NumberFormatLabelProvider implements ILabelProvider {
	
	private static final NumberFormat numberFormat = NumberFormat.getIntegerInstance();
	
	private int offset;
	
	public NumberFormatLabelProvider(int offset) {
		this.offset = offset;
	}

	@Override
	public void addListener(ILabelProviderListener arg0) {
	}

	@Override
	public void dispose() {
	}

	@Override
	public boolean isLabelProperty(Object arg0, String arg1) {
		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener arg0) {
	}

	@Override
	public Image getImage(Object arg0) {
		return null;
	}

	@Override
	public String getText(Object element) {
		return numberFormat.format(((Number) element).longValue() + offset);
	}

}

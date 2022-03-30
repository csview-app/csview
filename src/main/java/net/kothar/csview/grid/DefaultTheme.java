package net.kothar.csview.grid;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

public class DefaultTheme implements Theme {

    private final ResourceManager resourceManager;

    private Color outerBorderColor;
    private Color cellBorderColor;

    public DefaultTheme(Control owner) {
        resourceManager = new LocalResourceManager(JFaceResources.getResources(), owner);
    }

    public void dispose() {
        resourceManager.dispose();
    }

    @Override
    public Color getOuterBorderColor() {
        if (outerBorderColor == null) {
            outerBorderColor = resourceManager.createColor(new RGB(240, 240, 240));
        }
        return outerBorderColor;
    }

    @Override
    public Color getSelectionTextColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_LIST_SELECTION_TEXT);
    }

    @Override
    public Color getDefaultTextColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_LIST_FOREGROUND);
    }

    @Override
    public Color getCurrentCellBorderColor() {
        return getHeaderShadowColor();
    }

    @Override
    public Color getSelectionBackgroundColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_LIST_SELECTION);
    }

    @Override
    public Color getHeaderBackgroundColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_BACKGROUND);
    }

    @Override
    public Color getHeaderShadowColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_NORMAL_SHADOW);
    }

    @Override
    public Color getHeaderTextColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_FOREGROUND);
    }

    @Override
    public Color getHeaderHighlightColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_WIDGET_LIGHT_SHADOW);
    }

    @Override
    public Color getCellBorderColor() {
        if (cellBorderColor == null) {
            cellBorderColor = resourceManager.createColor(new RGB(240, 240, 240));
        }
        return cellBorderColor;
    }

    @Override
    public Color getCellBackgroundColor() {
        return Display.getDefault().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
    }
}

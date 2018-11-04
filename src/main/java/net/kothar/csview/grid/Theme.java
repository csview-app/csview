package net.kothar.csview.grid;

import org.eclipse.swt.graphics.Color;

public interface Theme {

    Color getOuterBorderColor();

    Color getSelectionTextColor();

    Color getDefaultTextColor();

    Color getCurrentCellBorderColor();

    Color getSelectionBackgroundColor();

    Color getHeaderBackgroundColor();

    Color getHeaderShadowColor();

    Color getHeaderTextColor();

    Color getHeaderHighlightColor();

    Color getCellBorderColor();

    Color getCellBackgroundColor();
}

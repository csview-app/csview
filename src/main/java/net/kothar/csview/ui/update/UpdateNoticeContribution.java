/*
 * Copyright 2016 - 2026 Kothar Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.kothar.csview.ui.update;

import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.StatusLineLayoutData;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/**
 * A status line item advertising the CSView 2 release: an info icon carrying an unread marker until
 * the user opens it. Clicking the icon shows {@link UpdateNoticeDialog} and removes the item, both
 * from this window and from any other window that happens to be open.
 * <p>
 * The status line rebuilds its controls from scratch on every update, so this item is created and
 * disposed repeatedly over the life of a window; nothing that has to survive an update is held here.
 */
public class UpdateNoticeContribution extends ControlContribution {

    private static final String ID = "updateNotice";

    private static final int MIN_ICON_SIZE = 13;

    /** macOS system blue */
    private static final int[] INFO_COLOUR = {0, 122, 255};
    /** macOS system red */
    private static final int[] MARKER_COLOUR = {255, 59, 48};

    private final Runnable visibilityListener = this::refresh;

    /**
     * The status line itself, which unlike our own control lives as long as the window does.
     */
    private Composite statusLine;

    public UpdateNoticeContribution() {
        super(ID);
        setVisible(UpdateNotice.isVisible());
        UpdateNotice.addVisibilityListener(visibilityListener);
    }

    @Override
    public void dispose() {
        UpdateNotice.removeVisibilityListener(visibilityListener);
        super.dispose();
    }

    @Override
    protected Control createControl(Composite parent) {

        if (statusLine == null) {
            statusLine = parent;
            // A window can go away without its status line manager being disposed
            parent.addDisposeListener(e -> UpdateNotice.removeVisibilityListener(visibilityListener));
        }

        Label separator = new Label(parent, SWT.SEPARATOR);

        GC gc = new GC(parent);
        gc.setFont(parent.getFont());
        int textHeight = gc.getFontMetrics().getHeight();
        gc.dispose();

        int iconSize = Math.max(MIN_ICON_SIZE, textHeight - 2);
        int markerSize = markerSize(iconSize);

        Canvas icon = new Canvas(parent, SWT.NONE);
        icon.setBackground(parent.getBackground());
        icon.setCursor(parent.getDisplay().getSystemCursor(SWT.CURSOR_HAND));
        icon.setToolTipText(UpdateNotice.NAME + " is available on the Mac App Store - click for details");

        Color infoColour = new Color(parent.getDisplay(), INFO_COLOUR[0], INFO_COLOUR[1], INFO_COLOUR[2]);
        Color markerColour = new Color(parent.getDisplay(), MARKER_COLOUR[0], MARKER_COLOUR[1], MARKER_COLOUR[2]);
        icon.addDisposeListener(e -> {
            infoColour.dispose();
            markerColour.dispose();
        });

        icon.addPaintListener(e -> paint(e, iconSize, infoColour, markerColour));
        icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDown(MouseEvent e) {
                showNotice(icon.getShell());
            }
        });

        StatusLineLayoutData separatorLayout = new StatusLineLayoutData();
        separatorLayout.heightHint = textHeight;
        separator.setLayoutData(separatorLayout);

        StatusLineLayoutData iconLayout = new StatusLineLayoutData();
        iconLayout.widthHint = iconSize + markerSize / 2 + 8;
        iconLayout.heightHint = Math.max(textHeight, iconSize + markerSize / 2);
        icon.setLayoutData(iconLayout);

        return icon;
    }

    private static int markerSize(int iconSize) {
        return Math.max(5, Math.round(iconSize * 0.45f));
    }

    /**
     * Draw a circled 'i', with an unread marker overlapping its top right corner. The glyph is drawn
     * rather than rendered as text so that it stays centred in the circle at any status line size.
     */
    private static void paint(PaintEvent e, int iconSize, Color infoColour, Color markerColour) {
        Control icon = (Control) e.widget;
        GC gc = e.gc;
        gc.setAntialias(SWT.ON);

        Rectangle bounds = icon.getBounds();
        int markerSize = markerSize(iconSize);
        int left = 2;
        int top = Math.max(0, (bounds.height - iconSize - markerSize / 2) / 2) + markerSize / 2;

        gc.setBackground(infoColour);
        gc.fillOval(left, top, iconSize, iconSize);

        int stroke = Math.max(2, Math.round(iconSize * 0.16f));
        int glyphLeft = left + (iconSize - stroke) / 2;
        int stemTop = top + Math.round(iconSize * 0.44f);
        int stemBottom = top + iconSize - Math.round(iconSize * 0.22f);
        gc.setBackground(e.display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillOval(glyphLeft, top + Math.round(iconSize * 0.2f), stroke, stroke);
        gc.fillRoundRectangle(glyphLeft, stemTop, stroke, stemBottom - stemTop, stroke, stroke);

        if (!UpdateNotice.hasBeenSeen()) {
            int markerLeft = left + iconSize - markerSize / 2;
            int markerTop = top - markerSize / 2;

            // A ring in the status line colour keeps the marker legible against the icon
            gc.setBackground(icon.getBackground());
            gc.fillOval(markerLeft - 1, markerTop - 1, markerSize + 2, markerSize + 2);
            gc.setBackground(markerColour);
            gc.fillOval(markerLeft, markerTop, markerSize, markerSize);
        }
    }

    private void showNotice(Shell shell) {
        // Marking the notice as seen disposes the control we are handling an event for, so let this
        // event finish before opening the dialog
        UpdateNotice.markSeen();
        shell.getDisplay().asyncExec(() -> UpdateNoticeDialog.show(shell));
    }

    /**
     * Show or hide this item to match the current state of the notice. Called from the notice
     * rather than from an event handler, so the work is deferred until the current event is done
     * with the control we are about to dispose.
     */
    private void refresh() {
        if (getParent() == null || (statusLine != null && statusLine.isDisposed())) {
            return;
        }

        Display display = statusLine == null ? Display.getDefault() : statusLine.getDisplay();
        display.asyncExec(() -> {
            IContributionManager parent = getParent();
            if (parent == null || (statusLine != null && statusLine.isDisposed())) {
                return;
            }

            setVisible(UpdateNotice.isVisible());
            parent.update(true);
        });
    }
}

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

import net.kothar.csview.ui.AboutDialog;
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
 * the user opens it. Clicking the icon shows the {@link AboutDialog}, which carries the CSView 2
 * details, and removes the item, both from this window and from any other window that happens to be
 * open.
 * <p>
 * The status line rebuilds its controls from scratch on every update, so this item is created and
 * disposed repeatedly over the life of a window; nothing that has to survive an update is held here.
 */
public class UpdateNoticeContribution extends ControlContribution {

    private static final String ID = "updateNotice";

    private static final int MIN_ICON_SIZE = 14;

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

        // Kept even: the glyph inside the disc is centred by halving the difference between
        // the two, and an odd difference would leave it half a pixel off centre
        int iconSize = Math.max(MIN_ICON_SIZE, (textHeight - 2) & ~1);
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
     * Draw a circled 'i', with an unread marker overlapping its top right corner. The glyph is
     * drawn rather than rendered as text so that it stays centred in the disc at any status line
     * size.
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

        // A dot over a stem, both the width of the stroke. The diameter and the stroke are both
        // even and the glyph's height is made even too, so every half here divides exactly and the
        // glyph lands on the disc's centre rather than half a pixel off it.
        int stroke = strokeWidth(iconSize);
        int gap = Math.max(1, Math.round(iconSize * 0.07f));
        int stemHeight = Math.max(stroke, Math.round(iconSize * 0.34f));
        if (((iconSize - (stroke + gap + stemHeight)) & 1) != 0) {
            stemHeight++;
        }

        int glyphLeft = left + (iconSize - stroke) / 2;
        int glyphTop = top + (iconSize - (stroke + gap + stemHeight)) / 2;

        // Square, and deliberately not antialiased: at status line sizes the dot is only a couple
        // of pixels across, and smoothing washes it out until the stem outweighs it and the whole
        // glyph reads as sitting low in the disc.
        gc.setAntialias(SWT.OFF);
        gc.setBackground(e.display.getSystemColor(SWT.COLOR_WHITE));
        gc.fillRectangle(glyphLeft, glyphTop, stroke, stroke);
        gc.fillRectangle(glyphLeft, glyphTop + stroke + gap, stroke, stemHeight);
        gc.setAntialias(SWT.ON);

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

    /**
     * @return the width of the 'i', always even so that it centres exactly on an even diameter
     */
    private static int strokeWidth(int iconSize) {
        return Math.max(2, Math.round(iconSize * 0.14f / 2) * 2);
    }

    private void showNotice(Shell shell) {
        // Opening the dialog marks the notice as seen, which disposes the control we are handling
        // an event for, so let this event finish first
        shell.getDisplay().asyncExec(() -> AboutDialog.show(shell));
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

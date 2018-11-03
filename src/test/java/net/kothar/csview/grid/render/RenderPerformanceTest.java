package net.kothar.csview.grid.render;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Shell;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;

@Ignore
public class RenderPerformanceTest {

    int[] colours = {SWT.COLOR_BLACK, SWT.COLOR_BLUE, SWT.COLOR_RED, SWT.COLOR_GRAY, SWT.COLOR_YELLOW, SWT.COLOR_MAGENTA, SWT.COLOR_GREEN};
    int nextColour = 0;

    @Test
    public void scroll_canvas() {
        Shell shell = new Shell();
        shell.setSize(1024, 768);
        shell.setLayout(new FillLayout());

        Canvas canvas = new Canvas(shell, SWT.DEFAULT);
        canvas.addPaintListener(this::paintCanvas);

        canvas.getHorizontalBar().addSelectionListener(new SelectionAdapter() {
            int lastPos = 0;

            @Override
            public void widgetSelected(SelectionEvent e) {
                ScrollBar bar = (ScrollBar) e.getSource();
                int pos = bar.getSelection();
                int shift = Math.abs(pos - lastPos) * 10;
                if (pos > lastPos) {
                    canvas.scroll(shift, 0, 0, 0, canvas.getSize().x - shift, canvas.getSize().y, false);
                } else {
                    canvas.scroll(0, 0, shift, 0, canvas.getSize().x - shift, canvas.getSize().y, false);
                }
                lastPos = pos;
            }
        });

        canvas.getVerticalBar().addSelectionListener(new SelectionAdapter() {
            int lastPos = 0;

            @Override
            public void widgetSelected(SelectionEvent e) {
                ScrollBar bar = (ScrollBar) e.getSource();
                int pos = bar.getSelection();
                int shift = Math.abs(pos - lastPos) * 10;
                if (pos > lastPos) {
                    canvas.scroll(0, shift, 0, 0, canvas.getSize().x, canvas.getSize().y - shift, false);
                } else {
                    canvas.scroll(0, 0, 0, shift, canvas.getSize().x, canvas.getSize().y - shift, false);
                }
                lastPos = pos;
            }
        });


        shell.open();
        displayLoop(shell);
    }

    private void displayLoop(Shell shell) {
        while (!shell.isDisposed()) {
            if (!shell.getDisplay().readAndDispatch()) {
                shell.getDisplay().sleep();
            }
        }
    }

    private void paintCanvas(PaintEvent paintEvent) {
        long start = System.nanoTime();
        GC gc = paintEvent.gc;
        gc.setBackground(gc.getDevice().getSystemColor(colours[nextColour++ % colours.length]));
        gc.fillRectangle(paintEvent.x, paintEvent.y, paintEvent.width, paintEvent.height);
        long end = System.nanoTime();
        System.out.println(paintEvent + " - Draw time: " + ((end - start) / 1000) + "ms");
    }
}

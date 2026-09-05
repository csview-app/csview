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
package net.kothar.csview.ui;

import net.kothar.csview.ui.update.UpdateNotice;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageDataProvider;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import java.io.IOException;
import java.io.InputStream;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.function.IntFunction;

/**
 * What CSView is, who wrote it and which version is running, plus - where there is one to point at
 * - a description of the CSView 2 release. The two are shown together so that there is a single
 * permanent home for the CSView 2 details: the status line notice hides itself once it has been
 * read, but this dialog is always a menu away.
 */
public class AboutDialog extends Dialog {

    /** Filtered at build time, so the name and version here are the ones this build was made with. */
    private static final String RESOURCE_BUNDLE = "net.kothar.csview.Messages";

    private static final String HOME_PAGE = "https://www.kothar.net/csview";
    private static final String SOURCE_URL = "https://github.com/csview-app/csview";
    private static final String ICON_CREDIT_URL = "http://jandousek.cz";

    /** The CSView 2 icon, at the size it is drawn and again for screens that can use twice that. */
    private static final String CSVIEW_2_ICON = "/csview2-icon.png";
    private static final String CSVIEW_2_ICON_2X = "/csview2-icon@2x.png";

    private static final int ICON_SIZE = 64;
    private static final int TEXT_WIDTH_IN_CHARS = 62;

    /** Breathing room around the CSView 2 section, so it reads as a section rather than a footnote. */
    private static final int SECTION_PADDING = 12;

    public AboutDialog(Shell parent) {
        super(parent);
    }

    /**
     * Open the dialog, unless its parent window has gone away in the meantime. Opening it counts as
     * having read the CSView 2 notice, whether the user came from the status line or the menu, so
     * the unread marker does not outlive the details it points at.
     */
    public static void show(Shell parent) {
        if (parent != null && parent.isDisposed()) {
            return;
        }

        UpdateNotice.markSeen();
        new AboutDialog(parent).open();
    }

    /**
     * Open the dialog over whichever window is currently active, for callers such as the macOS
     * application menu that have no window of their own to parent it to.
     */
    public static void showOnActiveShell() {
        show(Display.getDefault().getActiveShell());
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("About " + appName());
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        Composite area = (Composite) super.createDialogArea(parent);

        Composite content = new Composite(area, SWT.NONE);
        content.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        GridLayout layout = new GridLayout(2, false);
        layout.horizontalSpacing = 16;
        layout.verticalSpacing = 8;
        content.setLayout(layout);

        addIcon(content, size -> CSView.getAppIcon().getImageData().scaledTo(size, size), 0);
        addApplicationDetails(content);

        if (UpdateNotice.isSupportedPlatform()) {
            addSeparator(content);
            addIcon(content, AboutDialog::csview2Icon, SECTION_PADDING);
            addUpdateNotice(content);
        }

        return area;
    }

    /**
     * Fill the dialog's icon column. The label is created whatever happens to the image, so that
     * the text beside it stays in the column it belongs to.
     *
     * @param source  the icon, at a size in pixels: asked for again at every zoom the screen it is
     *                shown on uses, so that a HiDPI screen gets its own pixels rather than
     *                {@link #ICON_SIZE} of them magnified
     * @param padding the space to leave above the icon, matching the column beside it
     */
    private void addIcon(Composite parent, IntFunction<ImageData> source, int padding) {
        Label icon = new Label(parent, SWT.NONE);

        GridData data = new GridData(SWT.CENTER, SWT.TOP, false, false);
        data.verticalIndent = padding;
        icon.setLayoutData(data);

        try {
            Image image = new Image(parent.getDisplay(),
                    (ImageDataProvider) zoom -> source.apply(ICON_SIZE * zoom / 100));
            icon.setImage(image);
            icon.addDisposeListener(e -> image.dispose());
        } catch (RuntimeException e) {
            // The dialog is still perfectly readable without the icon
            System.out.println("Unable to load application icon: " + e);
        }
    }

    /**
     * @param size the width and height wanted, in pixels
     */
    private static ImageData csview2Icon(int size) {
        ImageData icon = readIcon(size > ICON_SIZE ? CSVIEW_2_ICON_2X : CSVIEW_2_ICON);
        return icon.width == size ? icon : icon.scaledTo(size, size);
    }

    private static ImageData readIcon(String resource) {
        try (InputStream stream = AboutDialog.class.getResourceAsStream(resource)) {
            return new ImageData(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + resource, e);
        }
    }

    private void addApplicationDetails(Composite parent) {
        Composite text = column(parent, 0);

        Label title = new Label(text, SWT.NONE);
        title.setText(appName());
        title.setFont(titleFont(text));

        Label version = new Label(text, SWT.NONE);
        version.setText("Version " + version() + "  \u00b7  \u00a9 Kothar Labs 2016 - 2026");

        link(text, HOME_PAGE, HOME_PAGE);

        Label licence = new Label(text, SWT.NONE);
        licence.setText("Source code available under the Apache License Version 2.0\n"
                + "Parts under the Eclipse Public License v1.0");
        licence.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        link(text, SOURCE_URL, SOURCE_URL);
        link(text, ICON_CREDIT_URL, "Icon by Honza Dousek");
    }

    /**
     * A description of the CSView 2 release, with a link to its App Store page.
     */
    private void addUpdateNotice(Composite parent) {
        Composite text = column(parent, SECTION_PADDING);

        Label title = new Label(text, SWT.NONE);
        title.setText(UpdateNotice.HEADLINE);
        title.setFont(titleFont(text));

        paragraph(text, "CSView 2 is a ground-up rewrite of CSView as a native macOS app, and is "
                + "now available on the Mac App Store. CSView 1.x stays free and cross-platform, "
                + "but is no longer actively developed.");

        Composite features = new Composite(text, SWT.NONE);
        features.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout featureLayout = new GridLayout(1, false);
        featureLayout.marginWidth = 0;
        featureLayout.marginHeight = 0;
        featureLayout.verticalSpacing = 3;
        features.setLayout(featureLayout);

        for (String feature : UpdateNotice.FEATURES) {
            Label bullet = new Label(features, SWT.NONE);
            bullet.setText("\u2022  " + feature);
        }

        Label requirements = new Label(text, SWT.NONE);
        requirements.setText(UpdateNotice.REQUIREMENTS);
        requirements.setForeground(parent.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY));

        link(text, UpdateNotice.APP_STORE_URL, "View CSView 2 on the Mac App Store");
    }

    /**
     * The column of text that sits beside an icon.
     *
     * @param padding the space to leave above the column and below its contents
     */
    private Composite column(Composite parent, int padding) {
        Composite column = new Composite(parent, SWT.NONE);

        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.verticalIndent = padding;
        column.setLayoutData(data);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.marginBottom = padding;
        layout.verticalSpacing = 6;
        column.setLayout(layout);

        return column;
    }

    private void addSeparator(Composite parent) {
        Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);

        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 2;
        // Matched to the padding below the rule, so the section sits evenly between the two
        data.verticalIndent = SECTION_PADDING;
        separator.setLayoutData(data);
    }

    private void link(Composite parent, String url, String text) {
        Link link = new Link(parent, SWT.NONE);
        link.setText("<a href=\"" + url + "\">" + text + "</a>");
        link.addListener(SWT.Selection, e -> Program.launch(url));
    }

    private void paragraph(Composite parent, String message) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(message);

        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.widthHint = convertWidthInCharsToPixels(TEXT_WIDTH_IN_CHARS);
        label.setLayoutData(data);
    }

    private Font titleFont(Composite parent) {
        FontData[] fontData = parent.getFont().getFontData();
        for (FontData data : fontData) {
            data.setHeight(data.getHeight() + 4);
            data.setStyle(SWT.BOLD);
        }

        Font font = new Font(parent.getDisplay(), fontData);
        parent.addDisposeListener(e -> font.dispose());
        return font;
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Close", true);
    }

    @Override
    protected boolean isResizable() {
        return false;
    }

    private static String appName() {
        return string("CSView.appName", "CSView");
    }

    private static String version() {
        return string("CSView.version", "");
    }

    /**
     * @return the named build property, or the given fallback if the bundle cannot be read
     */
    private static String string(String key, String fallback) {
        try {
            return ResourceBundle.getBundle(RESOURCE_BUNDLE).getString(key);
        } catch (MissingResourceException e) {
            return fallback;
        }
    }
}

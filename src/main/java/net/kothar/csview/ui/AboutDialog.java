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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

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
    private static final String SOURCE_URL = "https://bitbucket.org/mikehouston/csview";
    private static final String ICON_CREDIT_URL = "http://jandousek.cz";

    private static final int ICON_SIZE = 64;
    private static final int TEXT_WIDTH_IN_CHARS = 62;

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

        addAppIcon(content);
        addApplicationDetails(content);

        if (UpdateNotice.isSupportedPlatform()) {
            addSeparator(content);
            addUpdateNotice(content);
        }

        return area;
    }

    private void addAppIcon(Composite parent) {
        Label icon = new Label(parent, SWT.NONE);
        icon.setLayoutData(new GridData(SWT.CENTER, SWT.TOP, false, false));

        try {
            Image scaled = new Image(parent.getDisplay(),
                    CSView.getAppIcon().getImageData().scaledTo(ICON_SIZE, ICON_SIZE));
            icon.setImage(scaled);
            icon.addDisposeListener(e -> scaled.dispose());
        } catch (RuntimeException e) {
            // The dialog is still perfectly readable without the icon
            System.out.println("Unable to scale application icon: " + e);
        }
    }

    private void addApplicationDetails(Composite parent) {
        Composite text = column(parent, 1);

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
        Composite text = column(parent, 2);

        Label title = new Label(text, SWT.NONE);
        title.setText(UpdateNotice.NAME);
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
     * @param horizontalSpan the number of the dialog's two columns to fill: one to sit beside the
     *                       application icon, two to run the full width beneath it
     */
    private Composite column(Composite parent, int horizontalSpan) {
        Composite column = new Composite(parent, SWT.NONE);

        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.horizontalSpan = horizontalSpan;
        column.setLayoutData(data);

        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        layout.verticalSpacing = 6;
        column.setLayout(layout);

        return column;
    }

    private void addSeparator(Composite parent) {
        Label separator = new Label(parent, SWT.SEPARATOR | SWT.HORIZONTAL);

        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false);
        data.horizontalSpan = 2;
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

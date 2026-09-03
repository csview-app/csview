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

import net.kothar.csview.ui.CSView;
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
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Shell;

/**
 * A short description of the CSView 2 release, with a link to its App Store page.
 */
public class UpdateNoticeDialog extends Dialog {

    private static final int ICON_SIZE = 64;
    private static final int TEXT_WIDTH_IN_CHARS = 62;

    public UpdateNoticeDialog(Shell parent) {
        super(parent);
    }

    /**
     * Open the notice, unless its parent window has gone away in the meantime.
     */
    public static void show(Shell parent) {
        if (parent != null && parent.isDisposed()) {
            return;
        }
        new UpdateNoticeDialog(parent).open();
    }

    @Override
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(UpdateNotice.NAME);
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

        Composite text = new Composite(content, SWT.NONE);
        text.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout textLayout = new GridLayout(1, false);
        textLayout.marginWidth = 0;
        textLayout.marginHeight = 0;
        textLayout.verticalSpacing = 6;
        text.setLayout(textLayout);

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

        Link appStore = new Link(text, SWT.NONE);
        appStore.setText("<a href=\"" + UpdateNotice.APP_STORE_URL + "\">View CSView 2 on the Mac App Store</a>");
        appStore.addListener(SWT.Selection, e -> Program.launch(UpdateNotice.APP_STORE_URL));

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
            // The notice is still perfectly readable without the icon
            System.out.println("Unable to scale application icon: " + e);
        }
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
}

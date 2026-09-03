/*
 * Copyright 2016 - 2019 Kothar Labs
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

import static net.kothar.csview.ui.Adapters.select;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuEvent;
import org.eclipse.swt.events.MenuListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;

import net.kothar.csview.ApplicationActions;
import net.kothar.csview.DocumentActions;
import net.kothar.csview.ui.update.UpdateNotice;
import net.kothar.csview.ui.update.UpdateNoticeDialog;

public class Menus {

    private ApplicationActions actions;
    private DocumentActions docActions;
    private Commands commands;

    public Menus(ApplicationActions actions, Menu menuBar) {
        this.actions = actions;
        commands = new Commands(actions);

        createFileMenu(menuBar);
        createHelpMenu(menuBar);

        if (System.getProperties().containsKey("net.kothar.csview.debug")) {
            createDebugMenu(menuBar);
        }
    }

    public Menus(ApplicationActions actions, DocumentActions docActions, Menu menuBar) {
        this.actions = actions;
        this.docActions = docActions;
        commands = new Commands(actions);

        Menu fileMenu = createFileMenu(menuBar);
        addDocumentFileActions(fileMenu);

        createSearchMenu(menuBar);
        createSelectionMenu(menuBar);
        createWindowMenu(menuBar);
        createHelpMenu(menuBar);

        if (System.getProperties().containsKey("net.kothar.csview.debug")) {
            createDebugMenu(menuBar);
        }
    }

    private void createSearchMenu(Menu menuBar) {
        MenuItem menuItem = new MenuItem(menuBar, SWT.CASCADE);
        menuItem.setText("Search");

        Menu menu = new Menu(menuBar);
        menuItem.setMenu(menu);

        MenuItem gotoRow = new MenuItem(menu, SWT.NORMAL);
        gotoRow.setText("Go to row and column...");
        gotoRow.setAccelerator(SWT.MOD1 + 'G');
        gotoRow.addSelectionListener(select(docActions::gotoCell));

        MenuItem search = new MenuItem(menu, SWT.NORMAL);
        search.setText("Show search sidebar");
        search.setAccelerator(SWT.MOD1 + 'F');
        search.addSelectionListener(select(docActions::toggleSearch));
    }

    private Menu createSelectionMenu(Menu menuBar) {
        MenuItem menuItem = new MenuItem(menuBar, SWT.CASCADE);
        menuItem.setText("Selection");

        Menu menu = new Menu(menuBar);
        menuItem.setMenu(menu);

        MenuItem copy = new MenuItem(menu, SWT.NORMAL);
        copy.setText("Copy");
        copy.setAccelerator(SWT.MOD1 + 'C');

        copy.addSelectionListener(select(docActions::copySelection));

        return menu;
    }

    private void addDocumentFileActions(Menu fileMenu) {

        MenuItem refresh = new MenuItem(fileMenu, SWT.NORMAL);
        refresh.setText("Reload");
        refresh.setAccelerator(SWT.MOD1 + 'R');

        refresh.addSelectionListener(select(docActions::reload));

        MenuItem close = new MenuItem(fileMenu, SWT.NORMAL);
        close.setText("Close file");
        close.setAccelerator(SWT.MOD1 + 'W');

        close.addSelectionListener(select(docActions::close));

    }

    public Menu createFileMenu(Menu menuBar) {
        MenuItem file = new MenuItem(menuBar, SWT.CASCADE);
        file.setText("File");

        Menu fileMenu = new Menu(menuBar);
        file.setMenu(fileMenu);

        MenuItem open = new MenuItem(fileMenu, SWT.NORMAL);
        open.setText("Open file");
        open.setAccelerator(SWT.MOD1 + 'O');

        open.addSelectionListener(select(commands::openFile));

        return fileMenu;
    }

    private Menu createWindowMenu(Menu menuBar) {
        MenuItem menuItem = new MenuItem(menuBar, SWT.CASCADE);
        menuItem.setText("Window");

        Menu menu = new Menu(menuBar);
        menuItem.setMenu(menu);

        menu.addMenuListener(new MenuListener() {

            @Override
            public void menuShown(MenuEvent e) {
                // Remove any earlier items
                for (MenuItem item : menu.getItems()) {
                    item.dispose();
                }

                for (CSView instance : CSView.instances) {
                    MenuItem item = new MenuItem(menu, SWT.NORMAL);
                    item.setText(instance.getShell().getText());
                    item.addSelectionListener(new SelectionAdapter() {
                        @Override
                        public void widgetSelected(SelectionEvent e) {
                            instance.getShell().forceActive();
                        }
                    });
                }
            }

            @Override
            public void menuHidden(MenuEvent e) {
            }
        });

        return menu;
    }

    /**
     * The CSView 2 notice hides itself once it has been read, so keep a permanent way back to it.
     */
    private Menu createHelpMenu(Menu menuBar) {
        if (!UpdateNotice.isSupportedPlatform()) {
            return null;
        }

        MenuItem menuItem = new MenuItem(menuBar, SWT.CASCADE);
        menuItem.setText("Help");

        Menu menu = new Menu(menuBar);
        menuItem.setMenu(menu);

        MenuItem about = new MenuItem(menu, SWT.NORMAL);
        about.setText(UpdateNotice.NAME + "...");
        about.addSelectionListener(select(() -> UpdateNoticeDialog.show(Display.getDefault().getActiveShell())));

        return menu;
    }

    public void createDebugMenu(Menu menuBar) {
        MenuItem debug = new MenuItem(menuBar, SWT.CASCADE);
        debug.setText("Debug");

        Menu debugMenu = new Menu(menuBar);
        debug.setMenu(debugMenu);

        // Throw exception
        MenuItem exception = new MenuItem(debugMenu, SWT.NORMAL);
        exception.setText("Throw exception");
        exception.setAccelerator(SWT.MOD1 + 'E');

        exception.addSelectionListener(select(() -> {
            throw new RuntimeException("Test exception", new IllegalArgumentException("Internal exception"));
        }));

        // Bring back the CSView 2 status line notice, to check its first-run appearance
        MenuItem resetUpdateNotice = new MenuItem(debugMenu, SWT.NORMAL);
        resetUpdateNotice.setText("Reset update notice");
        resetUpdateNotice.addSelectionListener(select(UpdateNotice::reset));

        // Dump CSV indexes
        if (docActions != null) {
            MenuItem dumpIndexes = new MenuItem(debugMenu, SWT.NORMAL);
            dumpIndexes.setText("Dump Indexes");
            dumpIndexes.setAccelerator(SWT.MOD1 + 'D');

            dumpIndexes.addSelectionListener(select(docActions::dumpIndexes));
        }
    }
}

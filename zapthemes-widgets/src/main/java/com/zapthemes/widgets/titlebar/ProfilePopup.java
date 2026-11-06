package com.zapthemes.widgets.titlebar;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Monitor;

import com.zapthemes.core.ThemeManager;
import com.zapthemes.swt.widgets.Canvas;
import com.zapthemes.swt.widgets.Label;
import com.zapthemes.swt.widgets.Link;
import com.zapthemes.swt.widgets.Shell;

/**
 * The profile pop-up under a {@link TitleBar}'s avatar: a large avatar, the name and email, and the
 * profile's actions as links. Themed as other pop-ups (style class {@code popup}; the name has
 * {@code strong}, the email {@code muted}); closes on Escape, deactivation or an action.
 */
final class ProfilePopup {

    static final String POPUP = "popup";

    private ProfilePopup() {
    }

    static Shell open(TitleBar bar, TitleBarProfile profile, Point anchorTopRight) {
        Shell popup = new Shell(bar.getShell(), SWT.NO_TRIM | SWT.ON_TOP);
        popup.setData("zapthemes.titleBar.profile", Boolean.TRUE);
        float u = bar.unit();
        GridLayout layout = new GridLayout(2, false);
        layout.marginWidth = Math.round(16 * u);
        layout.marginHeight = Math.round(14 * u);
        layout.horizontalSpacing = Math.round(12 * u);
        layout.verticalSpacing = Math.round(4 * u);
        popup.setLayout(layout);

        int size = Math.round(56 * u);
        Canvas avatar = new Canvas(popup, SWT.DOUBLE_BUFFERED);
        GridData avatarData = new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 3);
        avatarData.widthHint = size;
        avatarData.heightHint = size;
        avatar.setLayoutData(avatarData);
        avatar.addListener(SWT.Paint, e -> {
            GC gc = e.gc;
            gc.setAntialias(SWT.ON);
            gc.setTextAntialias(SWT.ON);
            gc.setFont(bar.getFont());
            TitleBar.paintAvatarCircle(gc, profile, 0, 0, size - 1, bar.colors());
        });

        Label name = new Label(popup, SWT.NONE);
        name.setText(profile.name());
        name.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label email = new Label(popup, SWT.NONE);
        email.setText(profile.email());
        email.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label spacer = new Label(popup, SWT.NONE);
        spacer.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        ThemeManager manager = ThemeManager.get(bar.getDisplay());
        manager.setThemeClass(popup, POPUP);
        manager.setThemeClass(avatar, POPUP);
        manager.setThemeClass(spacer, POPUP);
        manager.setThemeClass(name, POPUP + " strong");
        manager.setThemeClass(email, POPUP + " muted");

        if (!profile.actions().isEmpty()) {
            Label separator = new Label(popup, SWT.SEPARATOR | SWT.HORIZONTAL);
            GridData separatorData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
            separatorData.verticalIndent = Math.round(6 * u);
            separator.setLayoutData(separatorData);
            manager.setThemeClass(separator, POPUP);
            for (TitleBarProfile.Action action : profile.actions()) {
                Link link = new Link(popup, SWT.NONE);
                link.setText("<a>" + action.text().replace("&", "&&") + "</a>");
                link.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
                link.addListener(SWT.Selection, e -> {
                    popup.close();
                    action.run().run();
                });
                manager.setThemeClass(link, POPUP);
            }
        }

        popup.addListener(SWT.Paint, e -> {
            Color background = popup.getBackground();
            Color foreground = popup.getForeground();
            RGB border = TitleBarColors.blend(foreground.getRGB(), background.getRGB(), 0.25);
            e.gc.setForeground(new Color(popup.getDisplay(), border));
            Rectangle area = popup.getClientArea();
            e.gc.drawRectangle(area.x, area.y, area.width - 1, area.height - 1);
        });
        org.eclipse.swt.widgets.Listener outside = e -> {
            if (!popup.isDisposed() && e.widget instanceof org.eclipse.swt.widgets.Control control
                    && control.getShell() != popup) {
                popup.close();
            }
        };
        popup.getDisplay().addFilter(SWT.MouseDown, outside);
        popup.addListener(SWT.Dispose, e -> popup.getDisplay().removeFilter(SWT.MouseDown, outside));
        popup.addListener(SWT.Traverse, e -> {
            if (e.detail == SWT.TRAVERSE_ESCAPE) {
                e.doit = false;
                popup.close();
            }
        });
        // Close when the user goes elsewhere, but only once the pop-up was active: a desktop that
        // never activates it (CI, a focus-stealing guard) would otherwise close it at once.
        boolean[] wasActive = { false };
        popup.addListener(SWT.Activate, e -> wasActive[0] = true);
        popup.addListener(SWT.Deactivate, e -> popup.getDisplay().asyncExec(() -> {
            if (!popup.isDisposed() && wasActive[0]) {
                popup.close();
            }
        }));
        popup.getAccessible().addAccessibleListener(new org.eclipse.swt.accessibility.AccessibleAdapter() {
            @Override
            public void getName(org.eclipse.swt.accessibility.AccessibleEvent e) {
                e.result = "Account: " + profile.name();
            }
        });

        Point preferred = popup.computeSize(SWT.DEFAULT, SWT.DEFAULT);
        int width = Math.max(preferred.x, Math.round(300 * u));
        Rectangle screen = monitorBounds(bar.getDisplay(), anchorTopRight);
        int x = Math.max(screen.x, Math.min(anchorTopRight.x - width, screen.x + screen.width - width));
        int y = Math.min(anchorTopRight.y + 2, screen.y + screen.height - preferred.y);
        popup.setBounds(x, y, width, preferred.y);
        popup.open();
        popup.setFocus();
        return popup;
    }

    private static Rectangle monitorBounds(Display display, Point point) {
        for (Monitor monitor : display.getMonitors()) {
            if (monitor.getBounds().contains(point)) {
                return monitor.getClientArea();
            }
        }
        return display.getPrimaryMonitor().getClientArea();
    }
}

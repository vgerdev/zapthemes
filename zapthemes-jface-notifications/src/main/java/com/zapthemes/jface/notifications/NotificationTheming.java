package com.zapthemes.jface.notifications;

import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.zapthemes.jface.JFaceTheming;

/**
 * Called from the notifications fork's patch ({@code src/fork/notifications-patches}) at the end of
 * {@code AbstractNotificationPopup.createContents}, where upstream hard-codes a white content area
 * and a dark-shadow title. The parts get style classes instead, so every theme can style them:
 *
 * <table>
 *   <caption>Style classes of a notification popup</caption>
 *   <tr><th>Class</th><th>Where</th></tr>
 *   <tr><td>{@value #NOTIFICATION}</td><td>the shell (its 1-pixel frame) and the line under the title</td></tr>
 *   <tr><td>{@value JFaceTheming#POPUP}</td><td>the content area and the controls in it</td></tr>
 *   <tr><td>{@value JFaceTheming#TITLE}</td><td>title controls still in upstream's title color (not one a
 *       subclass chose with {@code getTitleForeground})</td></tr>
 * </table>
 *
 * The title area itself keeps the theme's default background, as upstream keeps the widget color.
 */
public final class NotificationTheming {

    public static final String NOTIFICATION = "notification";

    private NotificationTheming() {
    }

    public static void apply(Shell shell, Composite titleArea, Composite contentFrame, Composite content) {
        JFaceTheming.classed(NOTIFICATION, shell, contentFrame);
        JFaceTheming.classedTree(JFaceTheming.POPUP, content, List.of());
        Color upstreamTitle = shell.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW);
        for (Control control : titleArea.getChildren()) {
            if (upstreamTitle.equals(control.getForeground())) {
                JFaceTheming.classed(JFaceTheming.TITLE, control);
            }
        }
    }
}

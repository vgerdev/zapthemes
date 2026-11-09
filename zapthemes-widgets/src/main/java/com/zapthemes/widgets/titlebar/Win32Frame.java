package com.zapthemes.widgets.titlebar;

/** The native Windows frame with the caption replaced by the bar: not yet available (step 2). */
final class Win32Frame extends WindowFrame {

    Win32Frame(TitleBar bar) {
        super(bar);
    }

    static boolean available() {
        return false;
    }

    @Override
    TitleBar.FrameMode mode() {
        return TitleBar.FrameMode.NATIVE;
    }
}

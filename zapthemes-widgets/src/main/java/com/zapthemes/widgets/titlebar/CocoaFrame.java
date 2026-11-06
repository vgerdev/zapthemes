package com.zapthemes.widgets.titlebar;

/** The native macOS frame with a full-size content view: not yet available (step 5). */
final class CocoaFrame extends WindowFrame {

    CocoaFrame(TitleBar bar) {
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

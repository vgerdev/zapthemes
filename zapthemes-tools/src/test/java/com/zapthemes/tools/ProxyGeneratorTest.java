package com.zapthemes.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableItem;
import org.junit.jupiter.api.Test;

class ProxyGeneratorTest {

    @Test
    void generatesConstructorsThatApplyTheTheme() {
        String source = ProxyGenerator.generate(Button.class, "com.zapthemes.swt.widgets.Button");

        assertThat(source)
                .contains("package com.zapthemes.swt.widgets;")
                .contains("public class Button extends org.eclipse.swt.widgets.Button {")
                .contains("public Button(org.eclipse.swt.widgets.Composite parent, int style) {")
                .contains("super(parent, style);")
                .contains("com.zapthemes.core.ThemeManager.applyOnCreate(this, \"Button\");")
                .contains("protected void checkSubclass()");
    }

    @Test
    void namesParametersReadably() {
        assertThat(ProxyGenerator.generate(Shell.class, "com.zapthemes.swt.widgets.Shell"))
                .contains("public Shell(org.eclipse.swt.widgets.Display display, int style)")
                .contains("public Shell(org.eclipse.swt.widgets.Shell parent, int style)");
        assertThat(ProxyGenerator.generate(TableItem.class, "com.zapthemes.swt.widgets.TableItem"))
                .contains("public TableItem(org.eclipse.swt.widgets.Table parent, int style, int index)");
    }

    @Test
    void rejectsAbstractClasses() {
        assertThatThrownBy(() -> ProxyGenerator.generate(Control.class, "com.zapthemes.swt.widgets.Control"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void catalogFindsSwtWidgets() {
        assertThat(WidgetCatalog.swtWidgets())
                .contains("org.eclipse.swt.widgets.Button", "org.eclipse.swt.custom.CTabFolder", "org.eclipse.swt.widgets.TableItem")
                .doesNotContain("org.eclipse.swt.layout.GridLayout")
                .hasSizeGreaterThan(60);
    }
}

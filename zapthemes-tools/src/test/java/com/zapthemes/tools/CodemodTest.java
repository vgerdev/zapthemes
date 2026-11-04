package com.zapthemes.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CodemodTest {

    private static final Map<String, String> PROXIES = Map.of(
            "org.eclipse.swt.widgets.Button", "com.zapthemes.swt.widgets.Button",
            "org.eclipse.swt.widgets.Composite", "com.zapthemes.swt.widgets.Composite",
            "org.eclipse.swt.widgets.Shell", "com.zapthemes.swt.widgets.Shell",
            "org.eclipse.swt.widgets.ToolTip", "com.zapthemes.swt.widgets.ToolTip");

    private final Codemod codemod = new Codemod(PROXIES, TypeNames.empty());

    @Test
    void rewritesOnlyTheNewExpression() {
        String source = """
                package demo;

                import org.eclipse.swt.widgets.Button;
                import org.eclipse.swt.widgets.Composite;

                class Dialogs {
                    protected Button createButton(Composite parent, int style) {
                        Button button = new Button(parent,   style); // keep comment
                        return button;
                    }
                }
                """;

        Codemod.Result result = codemod.rewrite(source);

        assertThat(result.changes()).hasSize(1);
        assertThat(result.source())
                .contains("new com.zapthemes.swt.widgets.Button(parent,   style); // keep comment")
                .contains("protected Button createButton(Composite parent, int style)")
                .contains("import org.eclipse.swt.widgets.Button;");
    }

    @Test
    void resolvesWildcardImportsAndAnonymousSubclasses() {
        String source = """
                package demo;

                import org.eclipse.swt.widgets.*;

                class Views {
                    Composite create(Composite parent) {
                        return new Composite(parent, 0) {
                            @Override
                            public void layout() {
                                super.layout();
                            }
                        };
                    }
                }
                """;

        Codemod.Result result = codemod.rewrite(source);

        assertThat(result.changes()).hasSize(1);
        assertThat(result.source()).contains("return new com.zapthemes.swt.widgets.Composite(parent, 0) {")
                .contains("public void layout()");
    }

    @Test
    void leavesLocalClassesThatShadowSwtNamesAlone() {
        String source = """
                package demo;

                import org.eclipse.swt.widgets.*;

                class Tips {
                    static class ToolTip {
                        ToolTip(Shell shell) {
                        }
                    }

                    ToolTip create(Shell parent) {
                        Shell shell = new Shell(parent);
                        return new ToolTip(shell);
                    }
                }
                """;

        Codemod.Result result = codemod.rewrite(source);

        assertThat(result.changes()).extracting(Codemod.Change::to).containsExactly("com.zapthemes.swt.widgets.Shell");
        assertThat(result.source()).contains("return new ToolTip(shell);");
    }

    @Test
    void rewritesConstructorReferences() {
        String source = """
                package demo;

                import java.util.function.BiFunction;
                import org.eclipse.swt.widgets.Button;
                import org.eclipse.swt.widgets.Composite;

                class Suppliers {
                    BiFunction<Composite, Integer, Button> buttons = Button::new;
                }
                """;

        Codemod.Result result = codemod.rewrite(source);

        assertThat(result.changes()).hasSize(1);
        assertThat(result.source()).contains("buttons = com.zapthemes.swt.widgets.Button::new;");
    }

    @Test
    void rewritesSystemColorsWhenAsked() {
        String source = """
                package demo;

                import org.eclipse.swt.SWT;

                class Painter {
                    void paint(org.eclipse.swt.graphics.GC gc, org.eclipse.swt.widgets.Control control) {
                        gc.setBackground(control.getDisplay().getSystemColor(SWT.COLOR_LIST_SELECTION));
                        gc.setForeground(gc.getDevice().getSystemColor(SWT.COLOR_BLACK));
                    }
                }
                """;

        assertThat(codemod.rewrite(source).changes()).isEmpty();
        Codemod.Result result = new Codemod(PROXIES, TypeNames.empty(), true).rewrite(source);

        assertThat(result.changes()).hasSize(2);
        assertThat(result.source())
                .contains("gc.setBackground(com.zapthemes.core.SystemColors.get(control.getDisplay(), SWT.COLOR_LIST_SELECTION));")
                .contains("gc.setForeground(com.zapthemes.core.SystemColors.get(gc.getDevice(), SWT.COLOR_BLACK));");
    }

    @Test
    void forkExcludeGlobsMatchSlashedRelativePaths() {
        assertThat(Fork.glob("**/*ObservableValue.java").matcher("org/x/FooObservableValue.java").matches()).isTrue();
        assertThat(Fork.glob("**/*ObservableValue.java").matcher("FooObservableValue.java").matches()).isTrue();
        assertThat(Fork.glob("**/forms/*.java").matcher("org/x/forms/Kit.java").matches()).isTrue();
        assertThat(Fork.glob("**/forms/*.java").matcher("org/x/forms/sub/Kit.java").matches()).isFalse();
        assertThat(Fork.glob("org/*.java").matcher("org/x/A.java").matches()).isFalse();
    }

    @Test
    void auditFlagsRawWidgetsUnlessAllowed() {
        String source = """
                package demo;

                import org.eclipse.swt.widgets.*;

                class Raw {
                    void create(Composite parent) {
                        new Label(parent, 0);
                        new Label(parent, 0); // zapthemes:allow-raw
                        new com.zapthemes.swt.widgets.Button(parent, 0);
                    }
                }
                """;
        Audit audit = new Audit(Set.of("org.eclipse.swt.widgets.Label", "org.eclipse.swt.widgets.Button"), TypeNames.empty());

        assertThat(audit.scan(source, "Raw.java"))
                .containsExactly(new Audit.Violation("Raw.java", 7, "org.eclipse.swt.widgets.Label"));
    }

    @Test
    void rewritesUpstreamJFaceFiles() throws Exception {
        Path jface = Path.of(System.getProperty("zapthemes.jfaceSrc", "missing"));
        assumeTrue(Files.isDirectory(jface), "JFace upstream not fetched; run scripts/fetch-upstream.sh");

        Codemod fork = new Codemod(PROXIES, TypeNames.index(jface));
        Audit audit = new Audit(PROXIES.keySet(), TypeNames.index(jface));

        // JFace 3.40 creates dialog widgets through org.eclipse.jface.widgets.*Factory, so Dialog.java
        // itself has nothing to rewrite; ButtonFactory holds the `new Button(parent, style)`.
        String dialog = Files.readString(jface.resolve("org/eclipse/jface/dialogs/Dialog.java"));
        assertThat(fork.rewrite(dialog).changes()).isEmpty();
        assertThat(audit.scan(dialog, "Dialog.java")).isEmpty();

        for (String file : new String[] {
                "org/eclipse/jface/widgets/ButtonFactory.java",
                "org/eclipse/jface/window/Window.java",
                "org/eclipse/jface/window/ToolTip.java" }) {
            String original = Files.readString(jface.resolve(file));
            Codemod.Result result = fork.rewrite(original);

            assertThat(result.changes()).as(file).isNotEmpty();
            assertThat(audit.scan(result.source(), file)).as("proxied widgets left raw in " + file).isEmpty();
            // Only the rewritten expressions differ from upstream.
            assertThat(result.source().replace("new com.zapthemes.swt.widgets.", "new "))
                    .as(file).isEqualTo(original);
        }
    }
}

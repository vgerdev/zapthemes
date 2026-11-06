package com.zapthemes.nebula.incubation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.jface.databinding.swt.DisplayRealm;
import org.eclipse.nebula.widgets.datechooser.DateChooser;
import org.eclipse.nebula.widgets.datechooser.DateChooserObservableValue;
import org.eclipse.nebula.widgets.formattedtext.FormattedText;
import org.eclipse.nebula.widgets.formattedtext.FormattedTextObservableValue;
import org.eclipse.nebula.widgets.formattedtext.IntegerFormatter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.zapthemes.core.ThemeKeys;

/** The DateChooser and FormattedText observables bind the forked, themed widgets both ways. */
class DataBindingTest {

    private final Display display = Display.getDefault();
    private final Shell shell = new com.zapthemes.swt.widgets.Shell(display);

    @AfterEach
    void tearDown() {
        shell.dispose();
    }

    @Test
    void dateChooserBindsToAModelValue() {
        Realm.runWithDefault(DisplayRealm.getRealm(display), () -> {
            DateChooser chooser = new com.zapthemes.nebula.datechooser.DateChooser(shell, SWT.NONE);
            WritableValue<Date> model = new WritableValue<>(date(2026, 11, 5), Date.class);
            new DataBindingContext().bindValue(new DateChooserObservableValue(chooser, SWT.Selection), model);

            assertThat(day(chooser.getSelectedDate())).isEqualTo(LocalDate.of(2026, 11, 5));
            model.setValue(date(2026, 12, 24));
            assertThat(day(chooser.getSelectedDate())).isEqualTo(LocalDate.of(2026, 12, 24));
            chooser.setSelectedDate(date(2027, 1, 2));
            chooser.notifyListeners(SWT.Selection, new Event());
            assertThat(day(model.getValue())).isEqualTo(LocalDate.of(2027, 1, 2));
            assertThat(chooser.getData(ThemeKeys.THEME_CONTROL_TYPE)).isNotNull();
        });
    }

    @Test
    void formattedTextBindsToAModelValue() {
        Realm.runWithDefault(DisplayRealm.getRealm(display), () -> {
            FormattedText text = new FormattedText(shell, SWT.BORDER);
            text.setFormatter(new IntegerFormatter());
            WritableValue<Integer> model = new WritableValue<>(42, Integer.class);
            new DataBindingContext().bindValue(new FormattedTextObservableValue(text, SWT.Modify), model);

            assertThat(text.getValue()).isEqualTo(42);
            model.setValue(7);
            assertThat(text.getValue()).isEqualTo(7);
            text.setValue(1234);
            assertThat(model.getValue()).isEqualTo(1234);
            // The Text inside is a themed proxy, made so by the fork's codemod.
            assertThat(text.getControl().getData(ThemeKeys.THEME_CONTROL_TYPE)).isEqualTo("Text");
        });
    }

    private static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate day(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
}

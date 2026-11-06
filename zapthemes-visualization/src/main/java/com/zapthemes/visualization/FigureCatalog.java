package com.zapthemes.visualization;

import java.util.List;
import java.util.Map;

import org.eclipse.nebula.visualization.widgets.figures.GaugeFigure;
import org.eclipse.nebula.visualization.widgets.figures.IntensityGraphFigure;
import org.eclipse.nebula.visualization.widgets.figures.KnobFigure;
import org.eclipse.nebula.visualization.widgets.figures.MeterFigure;
import org.eclipse.nebula.visualization.widgets.figures.ProgressBarFigure;
import org.eclipse.nebula.visualization.widgets.figures.ScaledSliderFigure;
import org.eclipse.nebula.visualization.widgets.figures.TankFigure;
import org.eclipse.nebula.visualization.widgets.figures.ThermometerFigure;
import org.eclipse.nebula.visualization.xygraph.figures.Annotation;
import org.eclipse.nebula.visualization.xygraph.figures.Axis;
import org.eclipse.nebula.visualization.xygraph.figures.Legend;
import org.eclipse.nebula.visualization.xygraph.figures.PlotArea;
import org.eclipse.nebula.visualization.xygraph.figures.ToolbarArmedXYGraph;
import org.eclipse.nebula.visualization.xygraph.figures.XYGraph;

import com.zapthemes.core.catalog.CatalogProvider;
import com.zapthemes.core.catalog.PropertyCatalog;
import com.zapthemes.core.catalog.TypeSpec;

/**
 * The figure types {@link FigureTheming} themes, for the {@link PropertyCatalog}: each class and
 * its Nebula Visualization superclasses (whose rules apply first, so each type
 * {@link TypeSpec#inherits} its superclass), with a property per color or font setter. Trace colors
 * are the application's data colors and are left out.
 */
public final class FigureCatalog implements CatalogProvider {

    private static final String GROUP = "Nebula Visualization";

    private static final List<Class<?>> FIGURES = List.of(XYGraph.class, ToolbarArmedXYGraph.class, PlotArea.class, Axis.class,
            Legend.class, Annotation.class, GaugeFigure.class, MeterFigure.class, KnobFigure.class, TankFigure.class,
            ThermometerFigure.class, ProgressBarFigure.class, ScaledSliderFigure.class, IntensityGraphFigure.class);

    /** Default names, as in zap-dark. */
    private static final Map<String, String[]> DEFAULTS = Map.of(
            "XYGraph", new String[] { "background=surface", "foreground=foreground", "titleColor=foreground" },
            "ToolbarArmedXYGraph", new String[] { "background=background" },
            "PlotArea", new String[] { "background=surface" },
            "Axis", new String[] { "background=surface", "foreground=foreground", "majorGridColor=gridLine", "minorGridColor=raised" },
            "Legend", new String[] { "foreground=foreground" },
            "Annotation", new String[] { "annotationColor=foreground" },
            "AbstractScaledWidgetFigure", new String[] { "foreground=foreground" },
            "AbstractLinearMarkedFigure", new String[] { "fillBackgroundColor=raised", "fillColor=accent" },
            "KnobFigure", new String[] { "thumbColor=hover" });

    @Override
    public void describe(PropertyCatalog.Builder catalog) {
        for (Class<?> figure : FIGURES) {
            String parent = null;
            for (Class<?> k : chain(figure)) {
                String type = k.getSimpleName();
                TypeSpec spec = TypeSpec.reflect(type, GROUP, TypeSpec.Base.FIGURE, k)
                        .defaults(DEFAULTS.getOrDefault(type, new String[0]));
                catalog.type(parent == null ? spec : spec.inheriting(parent));
                parent = type;
            }
        }
    }

    /** {@code figure} and its Nebula Visualization superclasses, most general first. */
    private static List<Class<?>> chain(Class<?> figure) {
        List<String> names = FigureTheming.types(figure);
        List<Class<?>> classes = new java.util.ArrayList<>();
        for (Class<?> k = figure; k != null; k = k.getSuperclass()) {
            if (names.contains(k.getSimpleName())) {
                classes.addFirst(k);
            }
        }
        return classes;
    }
}

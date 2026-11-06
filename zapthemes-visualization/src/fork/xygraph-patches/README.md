# Nebula Visualization fork patches

Hand edits to the fork, applied after the codemod by `forkXygraphSources` (this folder) and
`forkWidgetsSources` (`../widgets-patches`), as in
[the JFace fork](../../../../zapthemes-jface/src/fork/jface-patches/README.md) (same format and
refresh steps).

| Patch | What |
|---|---|
| `0001-theme-when-shown.patch` | `XYGraph`, `ToolbarArmedXYGraph`: `addNotify` attaches the theme's figure rules (`com.zapthemes.visualization.FigureTheming.attach`), `removeNotify` detaches them |
| `../widgets-patches/0001-theme-when-shown.patch` | The same for `AbstractScaledWidgetFigure` (gauge, meter, knob, tank, thermometer, progress bar, slider) and `IntensityGraphFigure` |

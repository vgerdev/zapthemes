# JFace Text fork patches

Hand edits to the fork, applied by `forkTextSources` (this folder) and `forkProjectionSources`
(`../projection-patches`) after the codemod, as in
[the JFace fork](../../../../zapthemes-jface/src/fork/jface-patches/README.md) (same format and
refresh steps, with `:zapthemes-jface-text:forkTextSources`). Each calls
`com.zapthemes.jface.text.TextTheming`; without a theme the controls are only tagged.

| Patch | What |
|---|---|
| `0001-information-controls.patch` | `AbstractInformationControl.create`: hovers and other information controls → `popup`, their status line → `popup muted` |
| `0002-content-assist.patch` | `CompletionProposalPopup(2)`: the proposal list → `popup`; `ContextInformationPopup(2)`: the text and the selector → `popup`, their one-pixel frame (upstream black) → `frame`. Colors set on the `ContentAssistant` are kept |
| `0003-rulers.patch` | Vertical, composite and overview rulers and the ruler columns → `ruler`; line numbers and the column backgrounds paint in its colors unless the app set some; `CompositeRulerCanvas` extends the `Canvas` proxy |
| `../projection-patches/0001-folded-code-hover.patch` | `SourceViewerInformationControl` (the folded-code hover) → `popup`, its status line → `popup muted` |

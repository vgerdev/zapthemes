# JFace notifications fork patches

Hand edits to the fork, applied by `forkNotificationsSources` after the codemod, as in
[the JFace fork](../../../../zapthemes-jface/src/fork/jface-patches/README.md) (same format and
refresh steps, with `:zapthemes-jface-notifications:forkNotificationsSources`).

| Patch | What |
|---|---|
| `0001-popup-style-classes.patch` | `AbstractNotificationPopup`: frame and title line → `notification`, content area → `popup`, a title still in upstream's color → `title` (`com.zapthemes.jface.notifications.NotificationTheming`) |
| `0002-icons-outside-eclipse.patch` | `CommonImages`: outside OSGi, find the close icons at the jar root; upstream looks next to an Eclipse `bin/` folder, so the close button had no image in a plain Java application |

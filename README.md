# ZapThemes

A JSON-themed SWT/JFace library: theme-aware proxy subclasses of every SWT and Nebula control, a
`ThemeManager` that re-themes live UIs, and forks of JFace and Nebula that create their widgets
through the proxies. See [docs/PLAN.md](docs/PLAN.md) and [docs/phase0-findings.md](docs/phase0-findings.md).

## Build

Requires JDK 25 (the Gradle 9.1.0 wrapper is included).

```bash
scripts/fetch-upstream.sh       # pristine JFace/Nebula sources into third_party/
./gradlew build                 # compile, generate proxies, run tests
./gradlew :zapthemes-demo:run   # spike demo with a light/dark switch
```

Other platforms: `-Pswt.platform=gtk.linux.aarch64` etc. FreeBSD needs `-Pswt.localJar=<swt.jar>`.

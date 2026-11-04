# Releasing to Maven Central

Releases go through the [Central Portal](https://central.sonatype.com). The build stages every
published module, signs it, zips it into a Portal bundle and uploads it as a **user-managed**
deployment. Central validates the deployment, but nothing becomes public until someone presses
*Publish* on [central.sonatype.com/publishing/deployments](https://central.sonatype.com/publishing/deployments).

## Published modules

`zapthemes-core`, `-swt`, `-widgets`, `-themes`, `-jface`, `-nebula-fork`, `-nebula`,
`-nebula-incubation-fork`, `-nebula-incubation` and `-draw2d`. The tools, demo and samples are not
published.

- **SWT:** POMs depend on the platform-neutral `org.eclipse.platform:org.eclipse.swt`, never on the
  fragment of the machine that built them (see `SwtPlatformPlugin`). Jars are the same on every OS,
  so one Linux job releases all of them.
- **draw2d:** `zapthemes-draw2d` is GEF Classic draw2d 3.23.100, unchanged except that the Eclipse
  signature files and the OSGi manifest are removed. GEF Classic is not on Maven Central, and
  Nebula Timeline needs it.

## One-time setup

1. **Namespace.** Verify the `com.zapthemes` namespace in the Portal: it needs a DNS TXT record
   on `zapthemes.com`. Without that domain, change `group` in `gradle.properties` to a namespace
   you can verify, such as `io.github.vgerdev`, and update the coordinates in
   [theme-authoring.md](theme-authoring.md).
2. **Token.** Create a Portal user token (Account → Generate User Token).
3. **Signing key.** Create a signing key and publish its public half to a keyserver:
   `gpg --quick-gen-key "Your Name <you@example.com>" rsa4096 sign 2y`, then
   `gpg --keyserver keys.openpgp.org --send-keys <id>`.
4. **Repository secrets** for [release.yml](../.github/workflows/release.yml):
   - `SIGNING_KEY`: `gpg --armor --export-secret-keys <id>`
   - `SIGNING_PASSWORD`: the key's passphrase
   - `CENTRAL_TOKEN_USERNAME`, `CENTRAL_TOKEN_PASSWORD`: the token

## Release

1. Make sure `main` is green on every CI platform.
2. Tag it: `git tag v1.0.0 && git push origin v1.0.0`. You can also run the *release* workflow by
   hand with a version.
3. The workflow runs every test and smoke test, then
   `centralBundle uploadToCentral -Pversion=1.0.0`. The bundle is also attached to the run as an
   artifact.
4. In the Portal, check the deployment's validation, then press *Publish*. Artifacts reach Maven
   Central within about 30 minutes.
5. On `main`, set `version` in `gradle.properties` to the next `-SNAPSHOT`.

## From a workstation

```bash
export ORG_GRADLE_PROJECT_signingKey="$(gpg --armor --export-secret-keys <id>)"
export ORG_GRADLE_PROJECT_signingPassword=...
./gradlew centralBundle -Pversion=1.0.0     # build/central/zapthemes-1.0.0-bundle.zip
export CENTRAL_TOKEN_USERNAME=... CENTRAL_TOKEN_PASSWORD=...
./gradlew uploadToCentral -Pversion=1.0.0   # or upload the zip in the Portal by hand
```

Signing also works with a keyring: set the `signing.keyId`, `signing.password` and
`signing.secretKeyRingFile` Gradle properties.

`centralBundle` refuses a `-SNAPSHOT` version or any unsigned artifact. `./gradlew publishToMavenLocal`
needs no key, which is useful for testing an app against local changes.

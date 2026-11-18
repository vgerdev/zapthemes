# Adding zapthemes-jface-ribbon

Use the same version as the app's other `com.zapthemes` artifacts (`zapthemes-jface`,
`zapthemes-themes`). `zapthemes-jface-ribbon` brings `zapthemes-jface` and `zapthemes-widgets` with
it.

Gradle, Kotlin DSL (`build.gradle.kts`):

```kotlin
dependencies {
    implementation("com.zapthemes:zapthemes-jface-ribbon:$zapthemesVersion")
}
```

Gradle, Groovy DSL (`build.gradle`):

```groovy
dependencies {
    implementation "com.zapthemes:zapthemes-jface-ribbon:$zapthemesVersion"
}
```

Maven (`pom.xml`):

```xml
<dependency>
  <groupId>com.zapthemes</groupId>
  <artifactId>zapthemes-jface-ribbon</artifactId>
  <version>${zapthemes.version}</version>
</dependency>
```

Use whatever the build already calls the version (a property, a version catalog entry such as
`libs.zapthemes.jface`, or a literal), and add the new artifact the same way. With a version
catalog, add an entry next to the others rather than a literal.

The app's existing setup for SWT (the platform fragment in place of the neutral
`org.eclipse.swt`) and for the forked JFace (the capability resolution in favor of `zapthemes-jface`)
covers the new artifact too; change nothing there.

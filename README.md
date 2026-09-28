# Zipper IntelliJ Plugin

Zipper adds the ability to pack the whole project into a single ZIP file.
<br>
Ideal for creating backups really quickly.

Just choose *Menu &gt; Tools &gt; Pack the whole Project* or press *CTRL + SHIFT + P*.

## Build

Requires JDK 21.

```
./gradlew buildPlugin   # plugin ZIP in build/distributions/
./gradlew runIde        # start a sandbox IDE with the plugin
./gradlew verifyPlugin  # run the JetBrains Plugin Verifier
```

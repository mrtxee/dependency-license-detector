# dependency-license-detector

## Use app

1. Load `app.env` to environment, see [SAMPLE.env](SAMPLE.env)
2. Run app

```bash
# "$JAVA_HOME"/bin/java -jar dependency-license-detector-1.0.1.jar
> java -jar target/dependency-license-detector-1.0.1.jar "pkg:maven/junit/junit@4.13.2" "https://repo1.maven.org/maven2/junit/junit/4.13.2/junit-4.13.2.pom"
[main] INFO mr.xe.delid.app.PackageLicenseDetectionScenario - Package pkg:maven/junit/junit@4.13.2: licenses detected: [Eclipse Public License 1.0]

```

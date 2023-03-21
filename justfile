build:
  clojure -T:meta run :alias build

build-native: build
  $GRAALVM_HOME/bin/native-image \
    -jar target/kl.jar \
    --no-fallback \
    -H:Name=target/kl \
    -H:+ReportUnsupportedElementsAtRuntime \
    -H:ResourceConfigurationFiles=resources.json

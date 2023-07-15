run +ARGS:
  clojure -M ./src/kl/cli.clj {{ARGS}}

build:
  clojure -T:meta run :alias build

build-native: build
  $GRAALVM_HOME/bin/native-image \
    -jar target/kl.jar \
    --no-fallback \
    -H:Name=target/kl \
    -H:+ReportUnsupportedElementsAtRuntime \
    -H:+ReportExceptionStackTraces \
    -H:ResourceConfigurationFiles=resources.json \
    --initialize-at-build-time='org.yaml.snakeyaml.DumperOptions$FlowStyle'

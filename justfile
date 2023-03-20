build:
  clojure -T:meta run :alias build

native-image: build
  $GRAALVM_HOME/bin/native-image -jar target/kl.jar -H:Name=target/kl -H:+ReportUnsupportedElementsAtRuntime --no-fallback

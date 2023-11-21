clean:
  clojure -T:build clean

build: clean
  clojure -T:build uber

native-image:
  $GRAALVM_HOME/bin/native-image \
    -jar target/kl.jar \
    --no-fallback \
    --features=clj_easy.graal_build_time.InitClojureClasses \
    --report-unsupported-elements-at-runtime \
    -o target/kl \
    -H:+UnlockExperimentalVMOptions \
    -H:ReflectionConfigurationFiles=./graal/reflect-config.json \
    -H:ResourceConfigurationFiles=./graal/resources.json \
    -H:+ReportExceptionStackTraces

build-native: build native-image

build-and-run: build
  java --enable-preview -jar target/cli.jar

run *args: 
  clojure -M -m k16.kl.cli {{args}}

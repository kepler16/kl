clean:
  clojure -T:build clean

build: clean
  clojure -T:build uber

native-image:
  $GRAALVM_HOME/bin/native-image -jar target/kl.jar target/kl

build-native: build native-image

build-and-run: build
  java --enable-preview -jar target/cli.jar

run *args: 
  clojure -M -m k16.kl.cli {{args}}

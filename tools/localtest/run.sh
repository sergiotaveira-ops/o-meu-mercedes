#!/usr/bin/env bash
# Usage: tools/localtest/run.sh  — compiles app main+test Kotlin that has no Android/OkHttp deps and runs the tests.
# Files listed in tools/localtest/files.txt (one path per line, relative to repo root).
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/../.." && pwd); OUT=/tmp/localtest; rm -rf $OUT; mkdir -p $OUT/gen $OUT/classes
/opt/protoc/bin/protoc -I$ROOT/app/src/main/proto -I/opt/protoc/include --java_out=$OUT/gen $ROOT/app/src/main/proto/*.proto
javac -nowarn -cp /opt/protobuf-java.jar -d $OUT/classes $(find $OUT/gen -name '*.java') 2>&1 | grep -v "^Picked\|^Note" || true
CP=$OUT/classes:/opt/protobuf-java.jar:/opt/kotlinc/lib/kotlin-test.jar:/opt/kotlinc/lib/kotlinx-coroutines-core-jvm.jar
SRCS=$(sed "s#^#$ROOT/#" $ROOT/tools/localtest/files.txt | tr '\n' ' ')
/opt/kotlinc/bin/kotlinc -nowarn -cp $CP -d $OUT/classes $ROOT/tools/localtest/JunitStub.kt $ROOT/tools/localtest/Runner.kt $SRCS 2>&1 | grep -v "^Picked\|^info:" || true
TESTS=$(grep 'src/test/.*Test.kt$' $ROOT/tools/localtest/files.txt | sed 's#.*/java/##; s#\.kt$##; s#/#.#g' | tr '\n' ' ')
java -cp $CP:/opt/kotlinc/lib/kotlin-stdlib.jar RunnerKt $TESTS 2>&1 | grep -v "^Picked"

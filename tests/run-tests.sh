#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p /tmp/chesspro-android-tests
javac -d /tmp/chesspro-android-tests \
    $(find tests/stubs -name '*.java') \
    app/src/main/java/de/chesspro/auto/{StateBus,ChessPosition,BoardDetector,PieceVision}.java \
    tests/ChessPositionTest.java tests/VisionTest.java
java -cp /tmp/chesspro-android-tests de.chesspro.auto.ChessPositionTest
for image in app/src/main/assets/known_board.png tests/phone_a.png tests/phone_b.png tests/phone_c.png; do
    java -Djava.awt.headless=true -cp /tmp/chesspro-android-tests de.chesspro.auto.VisionTest "$image"
done

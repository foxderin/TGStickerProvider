#!/bin/sh
# JDK-only parser fixtures: no Android runtime or network required.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
JAVAC=${JAVA_HOME:+$JAVA_HOME/bin/}javac
JAVA=${JAVA_HOME:+$JAVA_HOME/bin/}java
"$JAVAC" -d "$OUT" "$ROOT"/app/src/main/java/cc/microblock/TGStickerProvider/hook/TelegramTLParser/*.java \
  "$ROOT/tests/java/TLParserSafetyTest.java"
"$JAVA" -cp "$OUT" cc.microblock.TGStickerProvider.hook.TelegramTLParser.TLParserSafetyTest

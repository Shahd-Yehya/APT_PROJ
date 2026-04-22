#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# build.sh  –  Download dependencies and compile Phase 2
# Run from the project root (the folder containing src/)
# ─────────────────────────────────────────────────────────────────────────────

set -e

LIBS_DIR="lib"
SRC_DIR="src"
OUT_DIR="out"

mkdir -p "$LIBS_DIR" "$OUT_DIR"

# ── 1. Download Java-WebSocket (client + server) ─────────────────────────────
WS_JAR="$LIBS_DIR/java-websocket-1.5.3.jar"
if [ ! -f "$WS_JAR" ]; then
  echo "[build] Downloading Java-WebSocket..."
  curl -L -o "$WS_JAR" \
    "https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar"
fi

# ── 2. Download org.json (JSON serialisation) ─────────────────────────────────
JSON_JAR="$LIBS_DIR/json-20231013.jar"
if [ ! -f "$JSON_JAR" ]; then
  echo "[build] Downloading org.json..."
  curl -L -o "$JSON_JAR" \
    "https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar"
fi

# ── 3. Download SLF4J (Java-WebSocket logging dep) ───────────────────────────
SLF_JAR="$LIBS_DIR/slf4j-simple-2.0.9.jar"
SLF_API="$LIBS_DIR/slf4j-api-2.0.9.jar"
if [ ! -f "$SLF_JAR" ]; then
  echo "[build] Downloading SLF4J..."
  curl -L -o "$SLF_API" \
    "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.9/slf4j-api-2.0.9.jar"
  curl -L -o "$SLF_JAR" \
    "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.9/slf4j-simple-2.0.9.jar"
fi

CLASSPATH="$WS_JAR:$JSON_JAR:$SLF_API:$SLF_JAR"

# ── 4. Compile all .java files ────────────────────────────────────────────────
echo "[build] Compiling..."
javac -cp "$CLASSPATH" -d "$OUT_DIR" "$SRC_DIR"/*.java
echo "[build] Done. Compiled to $OUT_DIR/"

# ── 5. Print run instructions ─────────────────────────────────────────────────
echo ""
echo "───────────────────────────────────────────────────────────"
echo " HOW TO RUN"
echo "───────────────────────────────────────────────────────────"
echo " Terminal 1 – Start the relay server:"
echo "   java -cp out:$CLASSPATH CollabServer 8080"
echo ""
echo " Terminal 2 – Start client (site 1):"
echo "   java -cp out:$CLASSPATH EditorPanel 1"
echo ""
echo " Terminal 3 – Start client (site 2):"
echo "   java -cp out:$CLASSPATH EditorPanel 2"
echo ""
echo " Run tests (no server needed):"
echo "   java -cp out:$CLASSPATH Phase2ManualTest"
echo "───────────────────────────────────────────────────────────"

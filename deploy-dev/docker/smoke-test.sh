  #!/usr/bin/env bash
  set -euo pipefail

  BASE_URL="http://localhost:8080/ampli-sync"
  DEVICE_ID="smoke-device-1"
  OUT_DIR="/tmp/ampli-sync-smoke"

  mkdir -p "$OUT_DIR"

  echo "Checking API health:"
  curl -fsS "$BASE_URL/" | grep "Database connected"

  echo "Downloading SQLite database with prepopulate endpoint:"
  curl -fsSLo "$OUT_DIR/database.zip" "$BASE_URL/prepopulate-db/$DEVICE_ID"

  test -s "$OUT_DIR/database.zip"

  echo "Smoke test passed."
  echo "Downloaded database archive: $OUT_DIR/database.zip"

 #!/usr/bin/env bash
  set -euo pipefail

  cd "$(dirname "$0")/.."

  ./deploy-dev/build-dev.sh

  cd deploy-dev/docker
   docker compose up --build
d
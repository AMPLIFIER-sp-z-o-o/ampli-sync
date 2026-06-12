#!/bin/bash
set -euo pipefail

cd "$(dirname "$0")/.."

cd ampli-sync
mvn package war:exploded
cd ..
cp ampli-sync/target/ampli-sync-3.war deploy-dev/docker/webapps/ROOT.war

echo "Built WAR and copied it to deploy-dev/docker/webapps/ROOT.war"

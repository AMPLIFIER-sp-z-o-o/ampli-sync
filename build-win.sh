#!/bin/bash 
export JAVA_HOME="C:\Program Files\Java\jdk1.8.0_291"
cd ampli-sync
rm -r target
git_hash=$(git rev-parse --short HEAD)
echo $git_hash
echo "version=${git_hash}" | tee src/main/resources/project.properties

mvn.cmd package war:exploded
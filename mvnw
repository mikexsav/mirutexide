#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ] && [ "$(uname)" = Darwin ]; then
 export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
MAVEN_VERSION=3.9.9
if [ ! -x ".tools/apache-maven-$MAVEN_VERSION/bin/mvn" ]; then
 mkdir -p .tools
 curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o .tools/maven.tar.gz
 curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz.sha512" -o .tools/maven.sha512
 if command -v sha512sum >/dev/null 2>&1; then
  ACTUAL=$(sha512sum .tools/maven.tar.gz | cut -d " " -f 1)
 else
  ACTUAL=$(shasum -a 512 .tools/maven.tar.gz | cut -d " " -f 1)
 fi
 EXPECTED=$(tr -d "[:space:]" < .tools/maven.sha512)
 [ "$ACTUAL" = "$EXPECTED" ] || { echo "Maven checksum mismatch" >&2; exit 1; }
 tar -xzf .tools/maven.tar.gz -C .tools
fi
exec ".tools/apache-maven-$MAVEN_VERSION/bin/mvn" "$@"

#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
./mvnw -B -ntp test
PYTHONPATH=backend .venv/bin/pytest backend -q
npm --prefix frontend test
npm --prefix frontend run build
.venv/bin/pytest testing/test_integration.py -q -s

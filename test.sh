#!/bin/bash

DATE=`date +%Y-%m-%d`

curl -XDELETE localhost:9200/iocs
curl -XDELETE localhost:9200/logs-$DATE

curl -XPUT localhost:9200/_template/iocs -d @integration-tests/src/test/resources/elastic_iocs.json
curl -XPUT localhost:9200/_template/logs -d @integration-tests/src/test/resources/elastic_logs.json
curl -XPUT localhost:9200/_template/passivedns -d @integration-tests/src/test/resources/elastic_passivedns.json

curl -XPUT localhost:9200/iocs
curl -XPUT localhost:9200/logs-$DATE

mvn integration-test -Parq-wildfly-remote  -Dhotrod_host=127.0.0.1 -Dhotrod_port=11322


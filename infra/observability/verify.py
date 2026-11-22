#!/usr/bin/env python3
"""Verify live ingest parsing and optional log/trace correlation in Elasticsearch."""

import argparse
import json
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRACE_ID = "1234567890abcdef1234567890abcdef"


def request(base, path, payload=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    data = None if payload is None else json.dumps(payload).encode()
    with urllib.request.urlopen(urllib.request.Request(base + path, data, headers), timeout=15) as response:
        return json.load(response)


def verify_ingest(url):
    pipeline = json.loads((ROOT / "infra/elasticsearch/pipelines/paperless-container-logs.json").read_text())
    prefix = "2026-11-15T20:00:00.000Z  INFO "
    suffix = " 7 --- [paperless] [nio-8080-exec-1] a.f.s.paperless.Test : test event"
    messages = [
        prefix + "[trace_id=" + TRACE_ID + " requestId=request-123]" + suffix,
        prefix + "[trace_id=" + TRACE_ID + "]" + suffix.replace("[paperless]", "[paperless-worker]"),
        prefix + "[trace_id=none requestId=none]" + suffix,
        "unstructured container output",
    ]
    docs = [{"_source": {"message": message}} for message in messages]
    result = request(url, "/_ingest/pipeline/_simulate", {"pipeline": pipeline, "docs": docs})
    sources = [doc["doc"]["_source"] for doc in result["docs"]]
    assert sources[0]["@timestamp"].startswith("2026-11-15T20:00:00")
    assert sources[0]["trace"]["id"] == TRACE_ID
    assert sources[0]["http"]["request"]["id"] == "request-123"
    assert sources[0]["service"]["name"] == "paperless"
    assert sources[1]["trace"]["id"] == TRACE_ID
    assert sources[1]["service"]["name"] == "paperless-worker"
    assert "trace" not in sources[2], "Startup's none sentinel must not be a trace ID"
    assert sources[3]["message"] == messages[3]
    print("PASS: backend/worker console parsing and absent startup trace IDs")


def verify_correlation(elasticsearch, trace_id):
    assert re.fullmatch(r"[0-9a-f]{32}", trace_id) and int(trace_id, 16), "Expected a W3C trace ID"
    logs = request(elasticsearch, "/paperless-container-logs,paperless-worker-logs-*/_search", {
        "query": {"term": {"trace.id": trace_id}}, "size": 1,
    })
    assert logs["hits"]["total"]["value"] > 0, "No indexed logs match this trace"
    traces = request(elasticsearch, "/traces-apm*/_search", {
        "query": {"term": {"trace.id": trace_id}}, "size": 1,
    })
    assert traces["hits"]["total"]["value"] > 0, "No indexed traces match this trace"
    print("PASS: indexed log trace ID resolves to Elasticsearch trace documents: " + trace_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--elasticsearch", default="http://localhost:9200")
    parser.add_argument("--trace-id")
    args = parser.parse_args()
    verify_ingest(args.elasticsearch.rstrip("/"))
    if args.trace_id:
        verify_correlation(args.elasticsearch.rstrip("/"), args.trace_id)


if __name__ == "__main__":
    main()

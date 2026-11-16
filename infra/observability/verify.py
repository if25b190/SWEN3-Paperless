#!/usr/bin/env python3
"""Verify live ingest parsing, Grafana provisioning, and optional log/trace correlation."""

import argparse
import base64
import json
import os
import re
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TRACE_ID = "1234567890abcdef1234567890abcdef"


def request(base, path, payload=None, password=None):
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if password is not None:
        credentials = base64.b64encode(("admin:" + password).encode()).decode()
        headers["Authorization"] = "Basic " + credentials
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


def verify_grafana(url, password):
    for uid, index in (("elasticsearch", "paperless-container-logs"),
                       ("elasticsearch-worker", "paperless-worker-logs-*")):
        datasource = request(url, "/api/datasources/uid/" + uid, password=password)
        settings = datasource["jsonData"]
        assert settings["index"] == index
        link = next(link for link in settings["dataLinks"] if link["field"] == "trace.id")
        assert link["datasourceUid"] == "tempo"
        assert link["url"] == "${__value.raw}", "Grafana expanded the trace link prematurely"
    tempo = request(url, "/api/datasources/uid/tempo", password=password)
    correlation = tempo["jsonData"]["tracesToLogsV2"]
    assert correlation["datasourceUid"] == "elasticsearch"
    assert correlation["query"] == 'trace.id:"${__span.traceId}"'
    dashboard = request(url, "/api/dashboards/uid/paperless-observability", password=password)["dashboard"]
    recent = next(panel for panel in dashboard["panels"] if panel["id"] == 2)
    assert isinstance(recent["options"].get("sortBy", []), list), "Table sortBy must be an array"
    selected = next(panel for panel in dashboard["panels"] if panel["id"] == 3)
    assert selected["targets"][0]["queryType"] == "traceql"
    assert selected["targets"][0]["query"] == "$traceId"
    assert selected["targets"][0]["filters"] == []
    query = recent["targets"][0]["query"]
    search = request(url, "/api/datasources/proxy/uid/tempo/api/search?" +
                     urllib.parse.urlencode({"q": query, "limit": 20}), password=password)
    assert "traces" in search or "metrics" in search
    print("PASS: Grafana indices, escaped links, dashboard, and proxied TraceQL search")


def verify_correlation(elasticsearch, tempo, trace_id):
    assert re.fullmatch(r"[0-9a-f]{32}", trace_id) and int(trace_id, 16), "Expected a W3C trace ID"
    logs = request(elasticsearch, "/paperless-container-logs,paperless-worker-logs-*/_search", {
        "query": {"term": {"trace.id": trace_id}}, "size": 1,
    })
    assert logs["hits"]["total"]["value"] > 0, "No indexed logs match this trace"
    trace = request(tempo, "/api/traces/" + trace_id)
    assert trace.get("batches") or trace.get("resourceSpans"), "Tempo returned no spans"
    print("PASS: indexed log trace ID resolves to stored Tempo spans: " + trace_id)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--elasticsearch", default="http://localhost:9200")
    parser.add_argument("--grafana", default="http://localhost:3001")
    parser.add_argument("--tempo", default="http://localhost:3200")
    parser.add_argument("--trace-id")
    args = parser.parse_args()
    with urllib.request.urlopen(args.tempo.rstrip("/") + "/ready", timeout=15) as response:
        assert response.status == 200, "Tempo is not ready"
    print("PASS: Tempo readiness")
    verify_ingest(args.elasticsearch.rstrip("/"))
    verify_grafana(args.grafana.rstrip("/"), os.environ.get("GRAFANA_ADMIN_PASSWORD", "paperless"))
    if args.trace_id:
        verify_correlation(args.elasticsearch.rstrip("/"), args.tempo.rstrip("/"), args.trace_id)


if __name__ == "__main__":
    main()

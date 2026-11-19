#!/usr/bin/env python3
"""Generate the Kibana saved-objects bundle for the Paperless observability stack.

Writes an NDJSON file (default: saved-objects/paperless-observability.ndjson)
with the data views, Lens visualizations, saved search and dashboard that the
`kibana-bootstrap` compose service imports on startup via
``POST /api/saved_objects/_import?overwrite=true``.

The Lens/dashboard schema mirrors saved objects exported from this exact
Kibana build (verify/match it again when bumping the stack version):
* Lens state uses the ``formBased`` datasource; the layer's data view is a
  top-level reference named ``indexpattern-datasource-layer-<layerId>``.
* Lens objects carry NO migrationVersion: with one, Kibana re-runs Lens
  migrations that wipe the datasource layers. Instead they carry
  ``coreMigrationVersion``/``typeMigrationVersion`` like an export.
* Dashboard panels use ``type: "vis"`` / ``type: "discover_session"`` with
  references named ``<panelIndex>:savedObjectRef``.

Run after editing:
    python3 infra/kibana/generate-saved-objects.py [--output FILE]
"""
import argparse
import json
import uuid
from pathlib import Path

DV_LOGS = "paperless-container-logs"   # id == index name
DV_WORKER = "paperless-worker-logs"    # pattern paperless-worker-logs-*
DV_DOCS = "paperless-documents"        # id == index name
DV_TRACES = "traces-apm"               # APM Server data streams

objects = []


def add(obj):
    objects.append(obj)


def uid():
    return str(uuid.uuid4())


# ---------------------------------------------------------------- data views
for oid, title, tf in [
    (DV_LOGS, "paperless-container-logs", "@timestamp"),
    (DV_WORKER, "paperless-worker-logs-*", "@timestamp"),
    (DV_DOCS, "paperless-documents", "indexedAt"),
    (DV_TRACES, "traces-apm*", "@timestamp"),
]:
    add({
        "id": oid,
        "type": "index-pattern",
        "attributes": {
            "title": title,
            "timeFieldName": tf,
            "sourceFilters": "[]",
            "fieldFormatMap": "{}",
            "fieldAttrs": "{}",
            "runtimeFieldMap": "{}",
        },
        "migrationVersion": {"index-pattern": "8.0.0"},
        "references": [],
    })


# ------------------------------------------------------------------- lenses
def col_hist():
    return {
        "dataType": "date",
        "isBucketed": True,
        "label": "@timestamp",
        "operationType": "date_histogram",
        "params": {"dropPartials": False, "includeEmptyRows": True, "interval": "auto"},
        "sourceField": "@timestamp",
    }


def col_count():
    return {
        "dataType": "number",
        "isBucketed": False,
        "label": "Count of records",
        "operationType": "count",
        "params": {"emptyAsNull": True},
        "sourceField": "___records___",
    }


def col_unique(field, label):
    return {
        "dataType": "number",
        "isBucketed": False,
        "label": label,
        "operationType": "unique_count",
        "params": {},
        "sourceField": field,
    }


def col_terms(field, size):
    return {
        "dataType": "string",
        "isBucketed": True,
        "label": f"Top values of {field}",
        "operationType": "terms",
        "params": {
            "exclude": [],
            "excludeIsOpen": False,
            "include": "",
            "includeIsOpen": False,
            "missing": "__other__",
            "orderBy": "__COUNT__}",  # replaced by the count column id below
            "otherBucket": False,
            "otherBucketLabel": "Other",
            "secondaryFields": [],
            "size": size,
        },
        "sourceField": field,
    }


def filter_exists(field):
    return {
        "$state": {"store": "appState"},
        "meta": {"alias": None, "disabled": False, "field": field,
                 "key": "filter-exists-" + field, "negate": False,
                 "params": {}, "type": "exists"},
        "query": {"exists": {"field": field}},
    }


def filter_phrase(field, value, alias=None):
    return {
        "$state": {"store": "appState"},
        "meta": {"alias": alias, "disabled": False, "field": field,
                 "key": "filter-" + field + "-" + value, "negate": False,
                 "params": {}, "type": "phrase", "value": value},
        "query": {"match_phrase": {field: value}},
    }


# Only the parsed application logs: infra container lines (db, rabbitmq, ...)
# do not match the Spring Boot ingest pipeline and never get a service.name.
PAYLOAD_FILTERS = [filter_exists("service.name")]


def color_mapping():
    return {
        "assignments": [],
        "colorMode": {"type": "categorical"},
        "paletteId": "elastic_chart_palette",
        "specialAssignments": [{"color": {"type": "loop"},
                                "rules": [{"type": "other"}]}],
    }


def base_state(layer_id, columns, column_order, filters):
    return {
        "adHocDataViews": {},
        "datasourceStates": {
            "formBased": {
                "layers": {
                    layer_id: {
                        "columnOrder": column_order,
                        "columns": columns,
                        "incompleteColumns": {},
                        # "sampling" omitted: only used by UI-created charts
                    }
                }
            },
            "indexpattern": {"layers": {}},
            "textBased": {"layers": {}},
        },
        "filters": filters or [],
        "internalReferences": [],
        "query": {"language": "kuery", "query": ""},
    }


def finish(object_id, title, vis_type, state, dv_id, layer_id):
    return {
        "id": object_id,
        "type": "lens",
        "attributes": {
            "title": title,
            "description": "",
            "version": 2,
            "state": state,
            "visualizationType": vis_type,
        },
        # no migrationVersion: see module docstring; the version markers below
        # make the importer treat it as an already-migrated export
        "coreMigrationVersion": "8.8.0",
        "typeMigrationVersion": "10.1.0",
        "references": [{
            "id": dv_id,
            "name": f"indexpattern-datasource-layer-{layer_id}",
            "type": "index-pattern",
        }],
    }


def lens_metric(object_id, title, dv_id, column, filters):
    layer_id, col_id = uid(), uid()
    state = base_state(layer_id, {col_id: column}, [col_id], filters)
    # lnsMetric visualization config is flat (mirrors UI-created metrics)
    state["visualization"] = {
        "density": "default",
        "layerId": layer_id,
        "layerType": "data",
        "metricAccessor": col_id,
        "primaryAlign": "right",
        "primaryPosition": "bottom",
        "secondaryAlign": "right",
        "titlesTextAlign": "left",
    }
    return finish(object_id, title, "lnsMetric", state, dv_id, layer_id)


def lens_xy(object_id, title, dv_id, cols, order, series_type, x_col, y_cols,
            split_col=None, legend_visible=True, legend_position="right",
            filters=None):
    """cols: {key: column-def}; cross-references use those keys."""
    layer_id = uid()
    col_id = {k: uid() for k in order}
    columns = {col_id[k]: cols[k] for k in order}
    count_id = col_id[order[-1]]
    for c in columns.values():
        params = c.get("params", {})
        if params.get("orderBy") == "__COUNT__}":
            params["orderBy"] = {"column": count_id, "type": "column"}
    vl = {
        "accessors": [col_id[c] for c in y_cols],
        "colorMapping": color_mapping(),
        "layerId": layer_id,
        "layerType": "data",
        "position": "top",
        "seriesType": series_type,
        "showGridlines": False,
        "xAccessor": col_id[x_col],
    }
    if split_col:
        vl["splitAccessor"] = col_id[split_col]
    state = base_state(layer_id, columns, [col_id[k] for k in order], filters)
    state["visualization"] = {
        "axisTitlesVisibilitySettings": {"x": True, "yLeft": True, "yRight": True},
        "fittingFunction": "None",
        "fillOpacity": 0.5,
        "gridlinesVisibilitySettings": {"x": True, "yLeft": True, "yRight": True},
        "labelsOrientation": {"x": 0, "yLeft": 0, "yRight": 0},
        "layers": [vl],
        "legend": {"isVisible": legend_visible, "layout": "list",
                   "position": legend_position},
        "preferredSeriesType": series_type,
        "tickLabelsVisibilitySettings": {"x": True, "yLeft": True, "yRight": True},
        "valueLabels": "hide",
    }
    return finish(object_id, title, "lnsXY", state, dv_id, layer_id)


# 1) metric row
add(lens_metric("paperless-lens-log-events", "Application log events",
                DV_LOGS, col_count(), list(PAYLOAD_FILTERS)))
add(lens_metric("paperless-lens-errors", "Application errors",
                DV_LOGS, col_count(),
                PAYLOAD_FILTERS + [filter_phrase("log.level", "ERROR", "Errors")]))
add(lens_metric("paperless-lens-traces", "Correlated trace IDs",
                DV_LOGS, col_unique("trace.id", "Unique trace IDs"),
                list(PAYLOAD_FILTERS)))
add(lens_metric("paperless-lens-docs-count", "Documents indexed",
                DV_DOCS, col_count(), []))

# 2) charts (worker fields are dynamically mapped: log.level.keyword)
add(lens_xy(
    "paperless-lens-logs-over-time", "Log events over time by level", DV_LOGS,
    {"col-1": col_hist(), "col-level": col_terms("log.level", 6),
     "col-count": col_count()},
    ["col-1", "col-level", "col-count"], "area",
    "col-1", ["col-count"], split_col="col-level",
    legend_position="bottom", filters=PAYLOAD_FILTERS,
))
add(lens_xy(
    "paperless-lens-by-service", "Log events by service", DV_LOGS,
    {"col-svc": col_terms("service.name", 10), "col-count": col_count()},
    ["col-svc", "col-count"], "bar_horiz",
    "col-svc", ["col-count"], legend_visible=False, filters=PAYLOAD_FILTERS,
))
add(lens_xy(
    "paperless-lens-top-loggers", "Noisiest loggers", DV_LOGS,
    {"col-logger": col_terms("log.logger.keyword", 10), "col-count": col_count()},
    ["col-logger", "col-count"], "bar_horiz",
    "col-logger", ["col-count"], legend_visible=False, filters=PAYLOAD_FILTERS,
))
add(lens_xy(
    "paperless-lens-worker-activity", "Worker OCR runs over time by level", DV_WORKER,
    {"col-1": col_hist(), "col-level": col_terms("log.level.keyword", 6),
     "col-count": col_count()},
    ["col-1", "col-level", "col-count"], "area",
    "col-1", ["col-count"], split_col="col-level",
    legend_position="bottom",
))
add(lens_xy(
    "paperless-lens-traces-by-service", "Traces over time by service", DV_TRACES,
    {"col-1": col_hist(), "col-svc": col_terms("service.name", 10),
     "col-count": col_count()},
    ["col-1", "col-svc", "col-count"], "area",
    "col-1", ["col-count"], split_col="col-svc",
    legend_position="bottom",
))

# ------------------------------------------------------------ saved search
add({
    "id": "paperless-search-errors",
    "type": "search",
    "attributes": {
        "title": "[Paperless] Application error logs",
        "description": "ERROR-level application logs across backend and worker",
        "tabs": [{
            "id": "tab-0",
            "label": "Untitled",
            "attributes": {
                "kibanaSavedObjectMeta": {
                    "searchSourceJSON": json.dumps({
                        "indexRefName": "kibanaSavedObjectMeta.searchSourceJSON.index",
                        "query": {"query": "log.level : \"ERROR\" and _exists_:service.name",
                                  "language": "kuery"},
                        "filter": [],
                    })
                },
            },
        }],
    },
    "references": [{
        "type": "index-pattern",
        "name": "kibanaSavedObjectMeta.searchSourceJSON.index",
        "id": DV_LOGS,
    }],
})

# --------------------------------------------------------------- dashboard
LENS_PANELS = [
    ("paperless-lens-log-events", 0, 0, 6, 8),
    ("paperless-lens-errors", 6, 0, 6, 8),
    ("paperless-lens-traces", 12, 0, 6, 8),
    ("paperless-lens-docs-count", 18, 0, 6, 8),
    ("paperless-lens-logs-over-time", 0, 8, 16, 18),
    ("paperless-lens-by-service", 16, 8, 8, 18),
    ("paperless-lens-top-loggers", 0, 26, 12, 18),
    ("paperless-lens-worker-activity", 12, 26, 12, 18),
    ("paperless-lens-traces-by-service", 0, 44, 24, 18),
]

panels, references = [], []
for i, (lens_id, x, y, w, h) in enumerate(LENS_PANELS, start=1):
    panel_id = f"panel-{i}"
    panels.append({
        "type": "vis",
        "embeddableConfig": {},
        "panelIndex": panel_id,
        "gridData": {"x": x, "y": y, "w": w, "h": h, "i": panel_id},
    })
    references.append({"id": lens_id, "name": f"{panel_id}:savedObjectRef", "type": "lens"})

panels.append({
    "type": "discover_session",
    "embeddableConfig": {},
    "panelIndex": "panel-errors-table",
    "gridData": {"x": 0, "y": 62, "w": 24, "h": 18, "i": "panel-errors-table"},
})
references.append({"id": "paperless-search-errors",
                   "name": "panel-errors-table:savedObjectRef", "type": "search"})

add({
    "id": "paperless-logs-dashboard",
    "type": "dashboard",
    "attributes": {
        "title": "[Paperless] Logs",
        "description": "Predefined view over the Paperless observability data: "
                       "backend and worker log events, error stream, distributed "
                       "traces and indexed documents.",
        "esqlApproximation": False,
        "kibanaSavedObjectMeta": {
            "searchSourceJSON": json.dumps({"query": {"query": "", "language": "kuery"},
                                           "filter": []})
        },
        "optionsJSON": json.dumps({
            "autoApplyFilters": True,
            "hidePanelBorders": False,
            "hidePanelTitles": False,
            "syncColors": False,
            "syncCursor": True,
            "syncTooltips": False,
            "useMargins": True,
        }),
        "panelsJSON": json.dumps(panels),
        "timeRestore": False,
    },
    "references": references,
})


def main():
    default = Path(__file__).resolve().parent / "saved-objects" / "paperless-observability.ndjson"
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--output", type=Path, default=default)
    args = parser.parse_args()
    with args.output.open("w") as f:
        for obj in objects:
            f.write(json.dumps(obj, separators=(",", ":")) + "\n")
    print(f"wrote {len(objects)} saved objects to {args.output}")


if __name__ == "__main__":
    main()

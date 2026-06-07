# Configuration: Observability

This page covers actuator and Prometheus integration.

## Default Actuator Endpoints

| Path | Description |
|------|-------------|
| `/actuator/health` | Health endpoint |
| `/actuator/prometheus` | Prometheus scrape endpoint |
| `/actuator/info` | Build/runtime info |

All of the above are exposed by default.

To customize exposure, set:

```properties
management.endpoints.web.exposure.include=health,info,prometheus
```

## Prometheus Scrape Example

```yaml
scrape_configs:
  - job_name: kairos
    static_configs:
      - targets: ["kairos:8080"]
    metrics_path: /actuator/prometheus
```

## Helm Chart Integration

The Helm chart annotates the Kairos `Service` for Prometheus scraping by default:

```yaml
prometheus.io/scrape: "true"
prometheus.io/path: /actuator/prometheus
prometheus.io/port: "8080"
```

This works for Prometheus installations that scrape annotated services.

If you use Prometheus Operator, enable the chart's `ServiceMonitor`:

```yaml
metrics:
  serviceMonitor:
    enabled: true
```

Optional chart values:

```yaml
metrics:
  path: /actuator/prometheus
  serviceAnnotations:
    enabled: true
  podAnnotations:
    enabled: false
  serviceMonitor:
    enabled: false
    namespace: ""
    interval: 30s
    scrapeTimeout: 10s
```

## Grafana Dashboard

A ready-to-import Grafana dashboard is available at:

```text
docs/assets/kairos-grafana-dashboard.json
```

Import it in Grafana through **Dashboards -> New -> Import**, upload the JSON file, and select the Prometheus datasource that scrapes Kairos.

The dashboard includes:

- Resource availability, available/down/unknown totals, and current status mix
- Resource status timeline and most volatile resources over the selected range
- Resource type breakdown for HTTP, Docker, TCP, and any future resource types
- Check latency overview, p95/p99 latency, and latest DNS/connect/TLS phase latency
- Check outcome rates and failure reasons by normalized error code
- Active outages, active outage duration, outage events, and resolved outage duration
- Prometheus scrape health and scrape duration
- Spring Boot/Micrometer runtime panels for process uptime, HTTP traffic, JVM memory, CPU, threads, and GC pause pressure

The dashboard uses Kairos-specific metrics for resource health, check latency, and outages, plus standard Spring Boot actuator metrics for runtime panels.

## Kairos Metrics

| Metric | Type | Labels | Meaning |
|--------|------|--------|---------|
| `kairos_resource_status` | Gauge | `resource_name`, `resource_type` | Current resource status: `1` available, `0` not available, `-1` unknown |
| `kairos_resource_last_check_timestamp_seconds` | Gauge | `resource_name`, `resource_type` | Unix timestamp of the latest persisted check |
| `kairos_resource_last_check_latency_seconds` | Gauge | `resource_name`, `resource_type`, `phase` | Latest latency for `total`, `dns`, `connect`, or `tls`; optional phases appear only when measured |
| `kairos_resource_checks_total` | Counter | `resource_name`, `resource_type`, `status`, `error_code` | Persisted check results by status and normalized error code |
| `kairos_resource_check_duration_seconds` | Timer / histogram | `resource_name`, `resource_type`, `status` | Total check duration distribution |
| `kairos_resource_check_phase_duration_seconds` | Timer / histogram | `resource_name`, `resource_type`, `phase` | DNS, connect, and TLS phase duration distribution |
| `kairos_active_outages` | Gauge | none | Total active outages |
| `kairos_resource_outage_active` | Gauge | `resource_name`, `resource_type` | `1` when the resource has an active outage, otherwise `0` |
| `kairos_resource_active_outage_duration_seconds` | Gauge | `resource_name`, `resource_type` | Duration of the active outage, or `0` when inactive |
| `kairos_resource_outage_started_total` | Counter | `resource_name`, `resource_type` | Outage start events |
| `kairos_resource_outage_resolved_total` | Counter | `resource_name`, `resource_type` | Outage resolution events |
| `kairos_resource_outage_duration_seconds` | Timer / histogram | `resource_name`, `resource_type` | Resolved outage duration distribution |

All resource metrics use `resource_type` values such as `HTTP`, `DOCKER`, and `TCP`.

Timer metrics expose Prometheus `_count`, `_sum`, and `_bucket` series so Grafana can calculate averages and quantiles with `histogram_quantile(...)`.

## PromQL Examples

Current active outages:

```promql
kairos_active_outages
```

p95 check latency by resource:

```promql
histogram_quantile(
  0.95,
  sum by (le, resource_name) (
    rate(kairos_resource_check_duration_seconds_bucket[5m])
  )
)
```

Failure rate by resource:

```promql
sum by (resource_name, error_code) (
  rate(kairos_resource_checks_total{status="NOT_AVAILABLE"}[5m])
)
```

Latest phase latency:

```promql
kairos_resource_last_check_latency_seconds{phase=~"dns|connect|tls|total"}
```

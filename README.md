# Observability-as-code demo

This repository contains a testing setup for Grafana and an example of how to create a dashboard using the [Grafana Foundation SDK](https://github.com/grafana/grafana-foundation-sdk).

See the instructions below for instructions on how to run the demo environment and generate test data.

The test data is simulating a web app. 
The simulated web app has:
- Replicated database (one active and two passive)
- Backend servers (three instances)
- Frontend servers (three instances)

The demo includes a `cpu_usage` metric and a `db_is_active` metric.
(Note: The `db_is_active` metric could be extrapolated from the data due to the active DB always having the highest CPU usage, but it's included for simplicity.)

## Project structure

The project consists of two main components:
- *Test data generator*: (`testdata/1-cpu-usage.js`) A k6 script that generates CPU usage metrics for the simulated web app and sends them to Prometheus.
- *Dashboard generator*: (`dashboard-generator`) A Java (Gradle) project that uses the Grafana Foundation SDK to create a Grafana dashboard with panels based on the generated test data.

(Below is copied, and slightly changed, from the [Grafana demo prometheus and grafana alerts](https://github.com/grafana/demo-prometheus-and-grafana-alerts) repository)

## Run the demo environment

This repository includes a [Docker Compose setup](./docker-compose.yaml) that runs Grafana, Prometheus, Prometheus Alertmanager, Loki, and an SMTP server for testing email notifications.

To run the demo environment:

```bash
docker compose up -d
```

You can then access:
- Grafana: [http://localhost:3000](http://localhost:3000/)
- Prometheus web UI: [http://localhost:9090](http://localhost:9090/)
- Alertmanager web UI: [http://localhost:9093](http://localhost:9093/)

### Generating test data

This demo uses [Grafana k6](https://grafana.com/docs/k6) to generate test data for Prometheus.

To run k6 tests and store logs in Loki and time series data in Prometheus, you'll need a k6 version with the `xk6-client-prometheus-remote` and `xk6-loki` extensions.
(I don't think `xk6-loki` is strictly necessary, but I didn't test without it.)

You can build the k6 version using the [`xk6` instructions](https://grafana.com/docs/k6/latest/extensions/build-k6-binary-using-go/) or Docker as follows:

<details>
  <summary>macOS</summary>

  ```bash
  docker run --rm -it -e GOOS=darwin -u "$(id -u):$(id -g)" -v "${PWD}:/xk6" \
    grafana/xk6 build v0.55.0 \
    --with github.com/grafana/xk6-client-prometheus-remote@v0.3.2 \
    --with github.com/grafana/xk6-loki@v1.0.0
  ```
</details>

<details>
  <summary>Linux</summary>

  ```bash
  docker run --rm -it -u "$(id -u):$(id -g)" -v "${PWD}:/xk6" \
    grafana/xk6 build v0.55.0 \
    --with github.com/grafana/xk6-client-prometheus-remote@v0.3.2 \
    --with github.com/grafana/xk6-loki@v1.0.0
  ```
</details>

<details>
  <summary>Windows</summary>

  ```bash
docker run --rm -it -e GOOS=windows -u "$(id -u):$(id -g)" -v "${PWD}:/xk6" `
  grafana/xk6 build v0.55.0 --output k6.exe `
  --with github.com/grafana/xk6-client-prometheus-remote@v0.3.2 `
  --with github.com/grafana/xk6-loki@v1.0.0
```

</details>


Once you've built the necessary k6 version, you can pre-populate data by running the [scripts from the `testdata` folder](./testdata/) as follows:

```bash
./k6 run testdata/1-cpu-usage.js
```

The `testdata` scripts inject Prometheus metric, which can be used to define alert queries and conditions. You can then modify and run the scripts to test the alerts.

## Building the dashboard
The dashboard generator requires:
- Java 25+
- Gradle

To generate the dashboard JSON run:
```bash
./gradlew :dashboard-generator:jar
java -jar dashboard-generator/build/libs/dashboard-generator.jar > resources/dashboard.json
```
This outputs the dashboard in a Kubernetes-manifest like format to `resources/dashboard.json`.
To get the raw JSON file, you can run something like:
```bash
java -jar dashboard-generator/build/libs/dashboard-generator.jar | jq '.spec' > resources/dashboard-raw.json
```

To upload the generated dashboard to Grafana you need to do:
```bash
grafanactl resources push dashboards -p ./resources/dashboard.json
```

Or all in one:
```bash
./gradlew :dashboard-generator:jar && java -jar dashboard-generator/build/libs/dashboard-generator.jar > resources/dashboard.json && grafanactl resources push dashboards -p ./resources/dashboard.json
```

This requires the following environment variables to be set:
- `GRAFANA_SERVER`
- `GRAFANA_TOKEN`
- `GRAFANA_ORG_ID` (on self-hosted OSS / Enterprise) / `GRAFANA_STACK_ID` (on Grafana Cloud) 
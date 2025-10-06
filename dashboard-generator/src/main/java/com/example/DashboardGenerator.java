package com.example;


import module java.base;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grafana.foundation.common.*;
import com.grafana.foundation.dashboard.Dashboard;
import com.grafana.foundation.dashboard.DashboardBuilder;
import com.grafana.foundation.dashboard.DashboardDashboardTimeBuilder;
import com.grafana.foundation.dashboard.RowBuilder;
import com.grafana.foundation.prometheus.DataqueryBuilder;
import com.grafana.foundation.prometheus.PromQueryFormat;

public class DashboardGenerator {


    // These methods exist to make the code nicer since multiple different `PanelBuilder`s
    // are used in a single file. They are just a wrapper around the relevant constructor
    // with transparency added (since I think it looks nicer)

    static com.grafana.foundation.timeseries.PanelBuilder timeseriesPanelBuilder() {
        return new com.grafana.foundation.timeseries.PanelBuilder().transparent(true);
    }

    static com.grafana.foundation.stat.PanelBuilder statPanelBuilder() {
        return new com.grafana.foundation.stat.PanelBuilder().transparent(true);
    }

    static com.grafana.foundation.gauge.PanelBuilder gaugePanelBuilder() {
        return new com.grafana.foundation.gauge.PanelBuilder().transparent(true);
    }

    static com.grafana.foundation.bargauge.PanelBuilder bargaugePanelBuilder() {
        return new com.grafana.foundation.bargauge.PanelBuilder().transparent(true);
    }

    static com.grafana.foundation.bargauge.PanelBuilder graphPanelBuilder() {
        return new com.grafana.foundation.bargauge.PanelBuilder().transparent(true);
    }


    // unused since the heatmap is commented out
    static com.grafana.foundation.heatmap.PanelBuilder heatmapPanelBuilder() {
        return new com.grafana.foundation.heatmap.PanelBuilder().transparent(true);
    }

    static com.grafana.foundation.table.PanelBuilder tablePanelBuilder() {
        return new com.grafana.foundation.table.PanelBuilder().transparent(true);
    }

    /// Nicer exception handling might make sense,
    /// but I don't think it's particularly useful
    /// for such a simple application
    ///
    /// Especially since these errors would mostly
    /// happen in CI/CD either way
    void main() throws Exception {
        var name = "hardest-problem-in-computer-science";
        var builder =
                // "Node CPU usage" isn't terribly descriptive, but naming is hard...
                new DashboardBuilder("Node CPU usage gen")
                        .uid("cpu-usage-dashboard")
                        .tags(List.of(
                                "generated",
                                "foundation-sdk"
                                /*, Instant.now().toString()*/
                        ))
                        .refresh("10s")
                        .time(new DashboardDashboardTimeBuilder()
                                .from("now-30m").to("now"));

        // in a more complex project, I'd split this up into multiple (hopefully reusable) classes
        // right now I'm not even sure this is necessary, but it does make glancing at the code simpler
        addOverviewRow(builder);
        addDatabaseRow(builder);
        addTrendsRow(builder);
        addAlertsRow(builder);
        addRoleComparisonRow(builder);
        addDetailsRow(builder);

        var dashboard = builder.build();

        // whether to do the wrapping or not could be a CLI flag, same with whether to pretty-print
        //
        //if (someFLag) {
        //   IO.println(dashboard.toJSON());
        // } else {
        //
        var wrapper = new DashboardWrapper("dashboard.grafana.app/v1beta1", "Dashboard", new Metadata(name), dashboard);
        var objectMapper = new ObjectMapper();
        var json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper);
        IO.println(json);

        // }
    }


    private void addOverviewRow(DashboardBuilder builder) {
            builder.withRow(new RowBuilder("Overview"))
                .withPanel(timeseriesPanelBuilder()
                        .title("Highest load")
                        .tooltip(new VizTooltipOptionsBuilder()
                                .mode(TooltipDisplayMode.MULTI)
                                .sort(SortOrder.DESCENDING))
                        .showPoints(VisibilityMode.NEVER)
                        .withTarget(new DataqueryBuilder()
                                .expr("topk(3, cpu_usage)")
                                .legendFormat("{{instance}}")
                        )
                )
                .withPanel(statPanelBuilder()
                        .title("Running instances")
                        .justifyMode(BigValueJustifyMode.CENTER)
                        .textMode(BigValueTextMode.VALUE_AND_NAME)
                        .withTarget(new DataqueryBuilder()
                                .expr("count(cpu_usage) by (role)")
                                .legendFormat("{{role}}")
                                .instant())
                )
                .withPanel(gaugePanelBuilder()
                        .title("Active DB usage")
                        .min(0.)
                        .max(100.)
                        .withTarget(new DataqueryBuilder()
                                .expr("""
                                            cpu_usage{role="database"}
                                              and on(instance)
                                            db_is_active{role="database"} == 1
                                            """)
                                .legendFormat("{{instance}}")
                                .instant()
                        )
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Average CPU usage")
                        .withTarget(new DataqueryBuilder()
                                .expr("avg(cpu_usage) by (role)")
                                .legendFormat("{{role}}"))
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Stddev CPU usage")
                        .withTarget(new DataqueryBuilder()
                                .expr("stddev(cpu_usage) by (role)")
                                .legendFormat("{{role}}"))
                );
    }

    private void addDatabaseRow(DashboardBuilder builder) {
        builder.withRow(new RowBuilder("Database"))
                .withPanel(graphPanelBuilder()
                        .title("Database CPU usage")
                        .min(0.)
                        .max(100.)
                        .withTarget(new DataqueryBuilder()
                                .expr("cpu_usage{role=\"database\"}")
                                .legendFormat("{{instance}}"))
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Active DB")
                        .axisGridShow(false)
                        .scaleDistribution(new ScaleDistributionConfigBuilder().type(ScaleDistribution.SYMLOG))
                        .gradientMode(GraphGradientMode.OPACITY)
                        .lineStyle(new LineStyleBuilder()
                                .fill(LineStyleFill.SOLID))
                        .fillOpacity(75.)
                        .lineInterpolation(LineInterpolation.STEP_AFTER)
                        .showPoints(VisibilityMode.NEVER)
                        .withTarget(new DataqueryBuilder()
                                .expr("db_is_active")
                                .legendFormat("{{instance}}"))
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Failover count")
                        // Note: realistically, if you had this many failovers you'd change
                        // 1) your servers
                        // 2) your database
                        // 3) your development team
                        // But it's perhaps an interesting metric to track
                        // since it can be used as an alerting condition
                        // if changes > 0 then alert
                        .transparent(true)
                        .description("Number of database failovers in the last 15 seconds")
                        .withTarget(new DataqueryBuilder()
                                .expr("changes(db_is_active[15s])")
                                .format(PromQueryFormat.TIME_SERIES)
                                .legendFormat("{{instance}}"))

                )
                // This can be used to guesstimate the relative cost of inserts vs reads
                // assuming a standard 2PC setup with the active DB handling all writes
                // and all databases handling reads (of course, query timings would be better)
                .withPanel(timeseriesPanelBuilder()
                        .title("Active CPU Usage / Passive CPU Usage")
                        .description("How much more CPU does the active database use compared to the passive ones?")
                        .withTarget(new DataqueryBuilder()
                                .expr("""
                                            avg(cpu_usage{role="database"} and on(instance) db_is_active == 1)
                                              /
                                            avg(cpu_usage{role="database"} and on(instance) db_is_active == 0)
                                            """)
                                .legendFormat("{{instance}}"))
                );
    }
    private void addTrendsRow(DashboardBuilder builder) {
        builder.withRow(new RowBuilder("Trends and Predictions"))
                .withPanel(timeseriesPanelBuilder()
                        .title("CPU usage change")
                        .description("How uniform is our uniform-ish distribution?")
                        .withTarget(new DataqueryBuilder()
                                .expr("deriv(cpu_usage[15]) * 15")
                                .legendFormat("{{instance}}"))


                );
    }

    private void addAlertsRow(DashboardBuilder builder) {
        builder
                .withRow(new RowBuilder("Alerts & Thresholds"))
                .withPanel(statPanelBuilder()
                        .title("Servers > 80%")
                        .colorMode(BigValueColorMode.BACKGROUND)
                        .graphMode(BigValueGraphMode.NONE)
                        // Red background when count > 0
                        .withTarget(new DataqueryBuilder()
                                .expr("count(cpu_usage > 80)")
                                .instant())
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Overloaded servers (>80%)")
                        .description("Servers currently above 80% CPU usage")
                        .noValue("0")
                        .withTarget(new DataqueryBuilder()
                                .expr("cpu_usage > 80")
                                .legendFormat("{{instance}} ({{role}})"))
                )
                .withPanel(statPanelBuilder()
                        .title("Servers > 90% (Critical)")
                        .colorMode(BigValueColorMode.BACKGROUND)
                        .graphMode(BigValueGraphMode.NONE)
                        .noValue("0")
                        .withTarget(new DataqueryBuilder()
                                .expr("count(cpu_usage > 90)")
                                .instant())
                );
    }

    public void addRoleComparisonRow(DashboardBuilder builder) {
        builder.withRow(new RowBuilder("Role Comparison"))
                .withPanel(timeseriesPanelBuilder()
                        .title("Frontend vs Backend")
                        .description("Relative CPU usage comparison")
                        .withTarget(new DataqueryBuilder()
                                .expr("avg(cpu_usage{role=\"frontend\"}) / avg(cpu_usage{role=\"backend\"})")
                                .legendFormat("Frontend/Backend ratio"))
                )
                .withPanel(bargaugePanelBuilder()
                        .title("Top 2 per role")
                        .displayMode(BarGaugeDisplayMode.BASIC)
                        .orientation(VizOrientation.HORIZONTAL)
                        .withTarget(new DataqueryBuilder()
                                .expr("topk(2, cpu_usage) by (role)")
                                .legendFormat("{{instance}}")
                                .instant())
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("Role utilization %")
                        .description("Average CPU usage as percentage of capacity per role")
                        .min(0.)
                        .max(100.)
                        .withTarget(new DataqueryBuilder()
                                .expr("(sum(cpu_usage) by (role) / (count(cpu_usage) by (role) * 100)) * 100")
                                .legendFormat("{{role}}"))
                );
    }
    private void addDetailsRow(DashboardBuilder builder) {
        builder
                .withRow(new RowBuilder("Detailed Views"))
                .withPanel(tablePanelBuilder()
                        .title("All servers")
                        .description("Complete server inventory with current CPU usage")
                        .withTarget(new DataqueryBuilder()
                                .expr("cpu_usage")
                                .format(PromQueryFormat.TABLE)
                                .instant())
                )
//                        .withPanel(heatmapPanelBuilder()
//                                  // I couldn't figure out how to properly do heatmap, so they are not included
                // do note that this doesn't mean that I couldn't get a heatmap to show up,
                // the issue was that after any change to the heatmap it seems like for whatever
                // reason the default formatting that the Foundation SDK has gets overwritten
                // and there is no data.
                // if this code gets uncommented there will be a heatmap in the dashboard.
//                                .title("CPU distribution heatmap")
//                                .description("CPU usage distribution across all servers over time")
//                                .color(new HeatmapColorOptionsBuilder()
//                                        .scheme("RdYiBu")
//                                )
//                                        .withTarget(new DataqueryBuilder()
//                                        .expr("cpu_usage")
//                                        .format(PromQueryFormat.HEATMAP))
//                        )
                .withPanel(timeseriesPanelBuilder()
                        .title("Peak CPU (last hour)")
                        .description("Maximum CPU usage recorded in the past hour")
                        .withTarget(new DataqueryBuilder()
                                .expr("max_over_time(cpu_usage[1h])")
                                .legendFormat("{{instance}}"))
                )
                .withPanel(timeseriesPanelBuilder()
                        .title("CPU spike detection")
                        .description("Shows sudden increases >20% in 1 minute")
                        .withTarget(new DataqueryBuilder()
                                .expr("(cpu_usage - cpu_usage offset 1m) > 20")
                                .legendFormat("{{instance}} spike"))
                );
    }
}


/// `grafanactl` expects a Kubernetes manifest-like structure.
/// So we need to wrap the dashboard
record DashboardWrapper(
        String apiVersion,
        String kind,
        Metadata metadata,
        Dashboard spec
) {
}
record Metadata(String name) {
}
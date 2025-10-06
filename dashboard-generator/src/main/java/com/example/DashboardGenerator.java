package com.example;


import module java.base;
import com.grafana.foundation.dashboard.DashboardBuilder;
import com.grafana.foundation.dashboard.DashboardDashboardTimeBuilder;

public class DashboardGenerator {


    /// Nicer exception handling might make sense,
    /// But I don't think it's particularly useful for such a
    /// simple application
    ///
    /// Especially since these errors would mostly happen in CI/CD either way
    void main() throws Exception {
        IO.println("Hello World");

        var dashboard =
                new DashboardBuilder("Node CPU usage")
                        .uid("cpu-usage-dashboard")
                        .tags(List.of("generated"))
                        .refresh("5m")
                        .time(new DashboardDashboardTimeBuilder().from("now-30m").to("now"))
                        .build();

        IO.println(dashboard.toJSON());
    }
}

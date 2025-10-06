
plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.grafana:grafana-foundation-sdk:next-1746458649")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}


application {
    mainClass = "com.example.DashboardGenerator"
}

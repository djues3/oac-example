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

tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.example.DashboardGenerator"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(" ")
    }
}

application {
    mainClass = "com.example.DashboardGenerator"
}

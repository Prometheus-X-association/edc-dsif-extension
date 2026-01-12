/*
 * DSIF Independent Catalog Extension for Eclipse Dataspace Connector
 */

plugins {
    `java-library`
    `maven-publish`
    checkstyle
}

val edcGroup: String by project
val edcVersion: String by project
val projectGroup: String by project
val projectVersion: String by project

allprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "checkstyle")

    group = projectGroup
    version = projectVersion

    repositories {
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
        mavenCentral()
        mavenLocal()
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    checkstyle {
        toolVersion = "10.12.0"
        configFile = rootProject.file("config/checkstyle/checkstyle.xml")
        isIgnoreFailures = false
        maxWarnings = 0
    }

    publishing {
        publications {
            create<MavenPublication>(project.name) {
                from(components["java"])
            }
        }
    }
}

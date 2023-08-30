plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "ksmn"
version = "1.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to "MainKt"
    )

    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    configurations["compileClasspath"].forEach { file : File ->
        from(zipTree(file.absoluteFile))
    }
}
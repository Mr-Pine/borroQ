plugins {
    application
    id("org.checkerframework") version "1.0.2"
}

repositories {
    mavenCentral()
}

dependencies {
    checkerFramework("io.github.eisop:checker:3.49.3-eisop1")
    checkerFramework("de.mr-pine:borroQ:1.0")
    checkerQual("de.mr-pine:borroQ:1.0")
}

checkerFramework {
    checkers.add("de.mr_pine.borroq.BorroQChecker")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

application {
    mainClass.set("Main")
}
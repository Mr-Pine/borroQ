plugins {
    java
    kotlin("jvm") version libs.versions.kotlin
    id("org.jetbrains.dokka") version libs.versions.dokka
}

group = "de.mr-pine"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.bundles.checker)
    implementation(libs.plumelib.util)
    implementation(kotlin("reflect"))
    implementation(libs.logging)

    testImplementation(kotlin("test"))
    testImplementation(libs.eisop.test)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xnested-type-aliases")
        freeCompilerArgs.add("-Xdata-flow-based-exhaustiveness")
    }
}

tasks.compileJava {
    options.compilerArgs.add("-Xlint:all")
}

tasks.test {
    layout.buildDirectory.dir("cfgraphs").get().asFile.mkdirs()

    for (dir in sourceSets.test.get().java.srcDirs.map { it.resolve("testcases") }.filter { it.exists() }) {
        inputs.files(dir)
    }

    // A list of add-export and add-open arguments to be used when running the Checker Framework.
    // Keep this list in sync with the list in the Checker Framework manual.
    val compilerArgsForRunningCF = listOf(
        // These are required in Java 16+ because the --illegal-access option is set to deny
        // by default.  None of these packages are accessed via reflection, so the module
        // only needs to be exported, but not opened.
        "--add-exports", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
        "--add-exports", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
        // Required because the Checker Framework reflectively accesses private members in com.sun.tools.javac.comp.
        "--add-opens", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
        "-Dtests.outputDir=${layout.buildDirectory.dir("testclasses").get().asFile.path}"
    )
    jvmArgs(compilerArgsForRunningCF)
    testLogging {
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        events("failed")
    }
}

sourceSets {
    main {
        resources {
            srcDirs("src/main/kotlin")
            srcDirs("src/main/java")
        }
    }
}
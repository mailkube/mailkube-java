plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    pmd
    id("com.diffplug.spotless") version "8.9.0"
}

group = "com.mailkube"
// Supplied as `-Pversion=X.Y.Z` by the release, and read from gradle.properties (a permanent
// `0.0.0` placeholder) otherwise. There is no version literal in the tree. See .rules/RELEASE.md.
version = providers.gradleProperty("version").getOrElse("0.0.0")

repositories { mavenCentral() }

java {
    // A toolchain, not `sourceCompatibility`: it makes the build use a JDK 25 regardless of
    // whatever the developer happens to have on PATH, and downloads one if the environment
    // provides a resolver. The floor is load-bearing rather than a preference — see
    // .rules/SDK_DESIGN.md on JEP 491.
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
    withSourcesJar()
    withJavadocJar()
}

// This library has NO runtime dependencies, and that is a feature: it is installed into other
// people's applications, where every dependency is a version conflict waiting to happen. Java is
// the only language in this SDK family with no JSON in its standard library, so the cost is one
// internal encoder/parser rather than a Jackson (or Gson) dependency on every consumer's
// classpath. See .rules/SDK_DESIGN.md before adding the first one.
dependencies {
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// main/ is a JPMS module; tests are not, and they reach into the unexported `internal` package
// on purpose. Compiling and running them on the classpath is what makes that legal. Without this,
// Gradle infers a module path for the test source set and the internal package stops being visible.
tasks.named<JavaCompile>("compileTestJava") { modularity.inferModulePath = false }

tasks.withType<JavaCompile>().configureEach {
    // `-Werror` is the cheap half of what Error Prone would give, without hooking javac internals
    // or needing `--add-exports`. See .rules/SDK_DESIGN.md.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:all,-missing", "-quiet")
    options.encoding = "UTF-8"
}

// The published jar carries its version in the manifest, which is what `Version` reads back at
// runtime for the User-Agent. One source of truth: the Gradle version property.
tasks.jar {
    manifest {
        attributes(
            "Implementation-Title" to "mailkube-java",
            "Implementation-Version" to project.version,
        )
    }
}

// ...and it carries the same version as a resource, because the manifest route DOES NOT WORK on the
// module path. `java.lang.Package`'s own @implNote says builtin loaders define no Package object for
// a package in a named module, so `getImplementationVersion()` is null there and every JPMS consumer
// would report `mailkube-java/0.0.0`. This is not a second source of truth: both the manifest
// attribute and this file are derived from the one Gradle version property, and neither is committed.
val versionResourceDir = layout.buildDirectory.dir("generated/version")

// `tasks.register`, not the `by tasks.registering` delegate: that delegate is deprecated from
// Gradle 9.6 and incompatible with Gradle 10, the same reason the signing block avoids `by project`.
val generateVersionResource =
    tasks.register<WriteProperties>("generateVersionResource") {
        destinationFile = versionResourceDir.map { it.file("com/mailkube/version.properties") }
        comment = "Generated from the Gradle version property. Do not edit, and do not commit."
        property("version", providers.provider { project.version.toString() })
    }

sourceSets.main { resources.srcDir(versionResourceDir) }

tasks.processResources { dependsOn(generateVersionResource) }

spotless {
    java {
        // palantir-java-format, NOT google-java-format: the latter needs
        // `--add-exports jdk.compiler/...` since JEP 396, which on a build means JVM arguments
        // that affect everything. Verified: palantir formats Java 25 sources with no JVM args.
        palantirJavaFormat("2.97.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle { ktlint() }
}

pmd {
    // PMD rather than Error Prone: it gives cyclomatic AND cognitive complexity, unused
    // parameters and required Javadoc from one config. Pinned exactly, like every other gate here.
    toolVersion = "7.26.0"
    isConsoleOutput = true
    ruleSets = emptyList()
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

// Tests are exempt from the documentation rule: a test method's name is its documentation.
tasks.named<Pmd>("pmdTest") { ruleSetFiles = files("config/pmd/ruleset-test.xml") }

jacoco { toolVersion = "0.8.15" }

tasks.test {
    modularity.inferModulePath = false
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            // LINE and BRANCH at BUNDLE scope. JaCoCo's COMPLEXITY counter is deliberately not
            // used: it measures how much complexity is *covered*, not a maximum. Complexity is
            // PMD's job.
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

// Not automatic: without this, `check` runs the report and never the gate.
tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "mailkube-java"
            pom {
                name = "mailkube-java"
                description = "The official Java SDK for mailkube"
                url = "https://github.com/mailkube/mailkube-java"
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        name = "Mailtactic, Corp."
                        organization = "Mailtactic, Corp."
                    }
                }
                scm {
                    url = "https://github.com/mailkube/mailkube-java"
                    connection = "scm:git:https://github.com/mailkube/mailkube-java.git"
                }
            }
        }
    }

    // WITHOUT this block `./gradlew publish` has nowhere to upload to, so it does nothing and
    // EXITS 0: a release that looks green and ships no artifact. A publication says WHAT to
    // publish; a repository says WHERE. Both are required.
    //
    // Maven Central's Portal exposes no plain Maven endpoint. This is its OSSRH Staging API,
    // which accepts an ordinary Maven deploy and turns it into a Portal deployment.
    //
    // The repository NAME is what binds the credentials: `PasswordCredentials` on a repository
    // called `central` reads the `centralUsername` / `centralPassword` project properties, which
    // release.yml supplies as ORG_GRADLE_PROJECT_* environment variables. Rename the repository
    // and the secrets stop being found. Resolution is lazy, so `check` still runs with none set.
    repositories {
        maven {
            name = "central"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials(PasswordCredentials::class)
        }
    }
}

signing {
    // Maven Central requires signed artifacts and has NO OIDC trusted publishing, so unlike PyPI,
    // npm and RubyGems this repo needs a GPG key and a Portal token as secrets. See .rules/RELEASE.md.
    // `findProperty`, not the `by project` delegate: that delegate is deprecated from Gradle 9.6
    // and incompatible with Gradle 10.
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = project.findProperty("signingPassword") as String?
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenJava"])
    }
}

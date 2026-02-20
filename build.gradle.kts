plugins {
    id("com.android.library") version "9.0.0-rc02"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("org.jetbrains.dokka") version "2.0.0"
    id("maven-publish")
    id("signing")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

group = (findProperty("GROUP") as String?) ?: "ai.synheart"
version = (findProperty("VERSION_NAME") as String?) ?: "0.3.0"

android {
    namespace = "ai.synheart.wear"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    // Core library desugaring for Java 8+ APIs on older Android versions
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Health Connect
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    // Encryption
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // HTTP Client (Retrofit + OkHttp)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("org.mockito:mockito-core:5.14.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    val dokkaGenerate = tasks.named("dokkaJavadoc")
    dependsOn(dokkaGenerate)
    from(dokkaGenerate.map { it.outputs.files })
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "synheart-wear"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            artifact(javadocJar)

            pom {
                name.set("Synheart Wear")
                description.set("Unified wearable SDK for Android - Stream biometric data from multiple devices")
                url.set("https://github.com/synheart-ai/synheart-wear-android")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("isrugeek")
                        name.set("Israel Goytom")
                        email.set("dev@synheart.ai")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/synheart-ai/synheart-wear-android.git")
                    developerConnection.set("scm:git:ssh://github.com/synheart-ai/synheart-wear-android.git")
                    url.set("https://github.com/synheart-ai/synheart-wear-android")
                }
            }
        }
    }
}

signing {
    val signingKeyId =
        (findProperty("SIGNING_KEY_ID") as String?) ?: System.getenv("SIGNING_KEY_ID")
    val signingKey =
        (findProperty("SIGNING_KEY") as String?)
            ?: System.getenv("SIGNING_KEY")
            ?: (findProperty("GPG_PRIVATE_KEY") as String?)
            ?: System.getenv("GPG_PRIVATE_KEY")
    val signingPassword =
        (findProperty("SIGNING_PASSWORD") as String?)
            ?: System.getenv("SIGNING_PASSWORD")
            ?: (findProperty("GPG_PASSPHRASE") as String?)
            ?: System.getenv("GPG_PASSPHRASE")

    setRequired {
        val isPublishingOrSigning = gradle.taskGraph.allTasks.any { task ->
            task.name.contains("publish", ignoreCase = true) ||
                task.name.contains("close", ignoreCase = true) ||
                task.name.contains("release", ignoreCase = true) ||
                task.name.contains("sign", ignoreCase = true)
        }
        isPublishingOrSigning && signingKey != null && signingPassword != null
    }

    if (signingKey != null && signingPassword != null) {
        val normalizedKeyId = signingKeyId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            // Gradle expects a hex key id (typically 8 or 16 hex chars), optionally prefixed with 0x
            ?.takeIf { Regex("^(0x)?[0-9A-Fa-f]{8,16}$").matches(it) }

        if (normalizedKeyId != null) {
            useInMemoryPgpKeys(normalizedKeyId, signingKey, signingPassword)
        } else {
            useInMemoryPgpKeys(signingKey, signingPassword)
        }
    }

    sign(publishing.publications)
}

nexusPublishing {
    repositories {
        // Sonatype Central (OSSRH staging API) - see:
        // https://central.sonatype.org/publish/publish-portal-ossrh-staging-api/#configuration
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}

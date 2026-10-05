// Plain Kotlin JVM module (runtime.md, API client). The Retrofit client is
// generated from the pinned contract into the build folder and never committed.
// The generator validates the contract first, and a contract that fails
// validation fails the build (Danny, #43 C5).
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

val contract = rootProject.layout.projectDirectory.file("../../contracts/zzthis-v1.openapi.yaml")
val generatedDir = layout.buildDirectory.dir("generated/openapi")
val generatedPackage = "com.zer0state.zzthat.api.generated"

// The pinned OpenAPI Generator (gradle/libs.versions.toml), generator kotlin,
// library jvm-retrofit2, kotlinx.serialization, coroutines on (runtime.md, API
// client step 2). Its Gradle plugin is published only to the Gradle Plugin
// Portal, so the same generator version runs from its CLI artifact on Maven
// Central. The task keeps the plugin's name, openApiGenerate.
val openApiGenerator: Configuration by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
}

val openApiGenerate = tasks.register<JavaExec>("openApiGenerate") {
    group = "openapi tools"
    description = "Generates the Kotlin Retrofit client from contracts/zzthis-v1.openapi.yaml."
    classpath = openApiGenerator
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    inputs.file(contract).withPropertyName("contract").withPathSensitivity(PathSensitivity.NONE)
    outputs.dir(generatedDir).withPropertyName("generated")
    val output = generatedDir.get().asFile
    args(
        "generate",
        "--generator-name", "kotlin",
        "--library", "jvm-retrofit2",
        "--input-spec", contract.asFile.absolutePath,
        "--output", output.absolutePath,
        "--package-name", generatedPackage,
        "--additional-properties",
        listOf(
            "serializationLibrary=kotlinx_serialization",
            "useCoroutines=true",
            "dateLibrary=java8",
            "sourceFolder=src/main/kotlin",
            "omitGradleWrapper=true",
        ).joinToString(","),
        "--global-property", "apiTests=false,modelTests=false,apiDocs=false,modelDocs=false",
        // Generator configuration, not hand-written types (runtime.md, API
        // client step 6). The app builds its own Retrofit and OkHttp
        // (ZZApiFactory). The generated ApiClient pulls in an HTTP logging
        // interceptor, which could print a token, so it and its bearer helper
        // are not generated.
        "--openapi-generator-ignore-list",
        "src/main/kotlin/com/zer0state/zzthat/api/generated/infrastructure/ApiClient.kt," +
            "src/main/kotlin/com/zer0state/zzthat/api/generated/auth/**",
    )
    doFirst {
        // Nothing stale survives a regeneration.
        delete(output)
    }
}

sourceSets {
    main {
        kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })
    }
}

tasks.named("compileKotlin") {
    dependsOn(openApiGenerate)
}

dependencies {
    openApiGenerator(libs.openapi.generator.cli)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.retrofit.converter.scalars)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
}

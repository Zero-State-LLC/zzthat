// Plain Kotlin JVM module (runtime.md, API client). The Retrofit client is
// generated from the pinned contract into the build folder and never committed.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.openapi.generator)
}

kotlin {
    jvmToolchain(21)
}

val contract = rootProject.layout.projectDirectory.file("../../contracts/zzthis-v1.openapi.yaml")
val generatedDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    library.set("jvm-retrofit2")
    inputSpec.set(contract.asFile.absolutePath)
    outputDir.set(generatedDir)
    packageName.set("com.zer0state.zzthat.api.generated")
    // Generator configuration, not hand-written types (runtime.md, API client
    // step 6). The pinned file fails one OpenAPI 3.1 metadata rule,
    // "info.license.identifier is missing", which does not touch a wire shape.
    // That failure is reported to zzThis as a bug; until the pin carries the
    // fix, spec validation is skipped (skipValidateSpec, on the task below) so
    // generation runs on the pinned file as is.
    generateApiTests.set(false)
    generateModelTests.set(false)
    generateApiDocumentation.set(false)
    generateModelDocumentation.set(false)
    configOptions.set(
        mapOf(
            "serializationLibrary" to "kotlinx_serialization",
            "useCoroutines" to "true",
            "dateLibrary" to "java8",
            "sourceFolder" to "src/main/kotlin",
            "omitGradleWrapper" to "true",
        ),
    )
}

sourceSets {
    main {
        kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })
    }
}

tasks.named<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("openApiGenerate") {
    skipValidateSpec.set(true)
    // The app builds its own Retrofit and OkHttp (ZZApiFactory). The generated
    // ApiClient pulls in an HTTP logging interceptor, which could print a
    // token, so it and its bearer helper are not generated.
    openapiGeneratorIgnoreList.set(
        listOf(
            "src/main/kotlin/com/zer0state/zzthat/api/generated/infrastructure/ApiClient.kt",
            "src/main/kotlin/com/zer0state/zzthat/api/generated/auth/**",
        ),
    )
    cleanupOutput.set(true)
}

tasks.named("compileKotlin") {
    dependsOn(tasks.named("openApiGenerate"))
}

dependencies {
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

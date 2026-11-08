plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.11.0"
    kotlin("kapt") version "2.3.21"
}

group = "at.fhtw.swen3"
version = "0.0.3-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-jackson")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.mapstruct:mapstruct:1.6.3")
    implementation("io.swagger.core.v3:swagger-annotations-jakarta:2.2.55")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.h2database:h2")

    kapt("org.mapstruct:mapstruct-processor:1.6.3")

    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(kotlin("test"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }

    sourceSets {
        main {
            kotlin.srcDir(layout.buildDirectory.dir("generated/openapi/src/main/kotlin"))
        }
    }
}

openApiGenerate {
    generatorName.set("kotlin-spring")
    inputSpec.set("$projectDir/src/main/resources/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated/openapi").get().asFile.path)
    apiPackage.set("at.fhtw.swen3.paperless.ocr.api")
    modelPackage.set("at.fhtw.swen3.paperless.ocr.dto")
    // Models only: the worker has no REST API, so restrict generation to the
    // single wire DTO and skip the generated API support files
    // (ApiUtil, Exceptions, SpringDocConfiguration).
    globalProperties.set(mapOf("models" to "OcrRequestDTO"))
    configOptions.set(
        mapOf(
            "interfaceOnly" to "true",
            "useSpringBoot3" to "true",
            "useBeanValidation" to "true",
            "enumPropertyNaming" to "UPPERCASE",
            "openApiNullable" to "false",
            "useTags" to "true"
        )
    )
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate")
}

tasks.matching { it.name == "kaptGenerateStubsKotlin" }.configureEach {
    dependsOn("openApiGenerate")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10")
        classpath("org.springframework.boot:spring-boot-gradle-plugin:3.5.0")
        classpath("io.spring.gradle:dependency-management-plugin:1.1.7")
        classpath("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    }
}

apply(plugin = "org.jetbrains.kotlin.jvm")
apply(plugin = "org.jetbrains.kotlin.plugin.spring")
apply(plugin = "org.springframework.boot")
apply(plugin = "io.spring.dependency-management")
apply(plugin = "com.diffplug.spotless")

group = "com.example"
version = "0.0.1-SNAPSHOT"

extensions.configure(JavaPluginExtension::class.java) {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    add("implementation", "org.springframework.boot:spring-boot-starter-web")
    add("implementation", "com.fasterxml.jackson.module:jackson-module-kotlin")
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect")

    add("testImplementation", "org.springframework.boot:spring-boot-starter-test")
    add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher")
}

extensions.configure(SpotlessExtension::class.java) {
    kotlin {
        target("src/**/*.kt")
        ktlint("1.3.1")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.3.1")
    }
}

tasks.withType(KotlinCompile::class.java).configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType(Test::class.java).configureEach {
    useJUnitPlatform()
}

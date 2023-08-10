
fun properties(key: String) = providers.gradleProperty(key)
fun environment(key: String) = providers.environmentVariable(key)

plugins {
    id("java")
    id("maven-publish")
    alias(libs.plugins.gradleIntelliJPlugin)
    id("com.github.gradle-git-version-calculator") version("1.1.0")
}

// Keep these in sync with whatever the oldest IDE version we're targeting in gradle.properties needs
val javaLangVersion: JavaLanguageVersion? = JavaLanguageVersion.of(17)
val javaVersion = JavaVersion.VERSION_17

java {
    toolchain {
        languageVersion.set(javaLangVersion)
    }
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

group = "org.wso2.lsp4intellij"
version = gitVersionCalculator.calculateVersion("v")


// Configure project's dependencies
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.0")
    implementation("com.vladsch.flexmark:flexmark:0.34.60")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:3.9.0")
    testImplementation("org.powermock:powermock-api-mockito2:2.0.9")
    testImplementation("org.powermock:powermock-module-junit4:2.0.9")
}

intellij {
    version = properties("platformVersion")
    type = properties("platformType")
}

tasks {
    task<Exec>("nixos_jbr") {
        description = "Create a symlink to package jetbrains.jdk"
        group = "build setup"
        commandLine("nix-build", "<nixpkgs>", "-A", "jetbrains.jdk", "-o", "jbr")
    }

    withType<org.jetbrains.intellij.tasks.RunIdeBase> {
        project.file("jbr/bin/java")
                .takeIf { it.exists() }
                ?.let { projectExecutable.set(it.toString()) }
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components.getByName("java"))
            groupId = project.group.toString()
            artifactId = "lsp4intellij"
            version = project.version.toString()
        }
    }
}
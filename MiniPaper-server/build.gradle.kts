plugins {
    java
    id("com.gradleup.shadow") version("8.3.4")
}

dependencies {
    implementation(project(":MiniPaper-api"))
    compileOnly("org.jetbrains:annotations:24.1.0")
}

tasks.register<JavaExec>("runServer") {
    group = "softspoon"
    mainClass.set("net.minecraft.server.Main")
    standardInput = System.`in`
    workingDir = rootProject.layout.projectDirectory
        .dir(providers.gradleProperty("paper.runWorkDir").getOrElse("run"))
        .asFile
    javaLauncher.set(project.javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    })
    jvmArgs("-XX:+AllowEnhancedClassRedefinition", "-XX:+AllowRedefinitionToAddDeleteMethods")

    args("--nogui")

    val memoryGb = providers.gradleProperty("paper.runMemoryGb").getOrElse("2")
    minHeapSize = "${memoryGb}G"
    maxHeapSize = "${memoryGb}G"

    doFirst {
        workingDir.mkdirs()
    }

    classpath(sourceSets.main.map { it.runtimeClasspath })
}

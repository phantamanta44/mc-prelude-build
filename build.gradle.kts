plugins {
    id("java")
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("maven-publish")
}

group = "st.evening.mc.prelude"
version = "1.0.0"

repositories {
    mavenLocal()
    maven {
        name = "GTNH Maven"
        url = uri("https://nexus.gtnewhorizons.com/repository/public/")
        mavenContent {
            includeGroupByRegex("com\\.gtnewhorizons\\..+")
            includeGroup("com.gtnewhorizons")
        }
    }
    gradlePluginPortal()
}

dependencies {
    fun plugin(plugin: Provider<PluginDependency>) = plugin.map {
        val id = it.pluginId
        return@map "$id:$id.gradle.plugin:${it.version}"
    }

    compileOnly(gradleKotlinDsl())
    implementation(plugin(libs.plugins.kotlin.jvm))
    implementation(plugin(libs.plugins.retro.futura.gradle))
    implementation(plugin(libs.plugins.kt.invoke.control))
    implementation(plugin(libs.plugins.idea.ext))
}

abstract class InjectDependencyVersionsTask : DefaultTask() {
    @get:Input
    abstract val versions: MapProperty<String, Dependency>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun inject() {
        val classDir = outputDir.get().file("st/evening/mc/prelude/build").asFile
        classDir.mkdirs()
        classDir.resolve("PreludeDeps.java").writeText(
            buildString {
                appendLine("package st.evening.mc.prelude.build;")
                appendLine("public final class PreludeDeps {")
                appendLine("    private PreludeDeps() {}")
                versions.get().forEach { (name, dep) ->
                    val varName = name.replace(Regex("""\W+"""), "_")
                    dep.group?.let { appendLine("    public static final String $varName = \"$it:${dep.name}\";") }
                    dep.version?.let { appendLine("    public static final String ${varName}_VERSION = \"$it\";") }
                }
                appendLine("}")
            }
        )
    }
}

val injectDepVersionsDir: Provider<Directory> = layout.buildDirectory.dir("generated/sources/injectDepVersions")
val injectDepVersionsTask = tasks.register<InjectDependencyVersionsTask>("injectDepVersions") {
    versions.set(
        libs.bundles.downstream.map { bundle ->
            buildMap<String, Dependency> {
                bundle.forEach { put(it.name.replace('-', '_').uppercase(), it) }
            }
        }
    )
    outputDir.set(injectDepVersionsDir)
}

val injectedDepVersions: SourceSet = sourceSets.create("injectedDepVersions") {
    java {
        srcDirs(objects.fileCollection().from(injectDepVersionsDir).builtBy(injectDepVersionsTask))
    }
}
tasks.named(injectedDepVersions.compileJavaTaskName) {
    dependsOn(injectDepVersionsTask)
}

sourceSets.main {
    compileClasspath += injectedDepVersions.output
    runtimeClasspath += injectedDepVersions.output
}

tasks.jar {
    from(injectedDepVersions.output.asFileTree)
}

gradlePlugin {
    plugins.create("preludeBuild") {
        id = "st.evening.mc.prelude.build"
        implementationClass = "st.evening.mc.prelude.build.PreludeBuildPlugin"
    }
}

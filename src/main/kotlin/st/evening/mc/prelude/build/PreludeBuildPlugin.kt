package st.evening.mc.prelude.build

import com.gtnewhorizons.retrofuturagradle.MinecraftExtension
import com.gtnewhorizons.retrofuturagradle.UserDevPlugin
import com.gtnewhorizons.retrofuturagradle.mcp.DeobfuscateTask
import com.gtnewhorizons.retrofuturagradle.mcp.InjectTagsTask
import com.gtnewhorizons.retrofuturagradle.mcp.JSTTransformerTask
import com.gtnewhorizons.retrofuturagradle.minecraft.RunMinecraftTask
import com.gtnewhorizons.retrofuturagradle.modutils.ModUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.kotlin.dsl.add
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.expand
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.gradle.ext.compiler
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPluginWrapper
import org.jetbrains.kotlin.gradle.utils.API
import org.jetbrains.kotlin.gradle.utils.IMPLEMENTATION
import org.jetbrains.kotlin.gradle.utils.RUNTIME_ONLY
import st.evening.kt.invokecontrol.ICExtension
import java.net.URI

private const val PLUGIN_INVOKE_CONTROL: String = "st.evening.kt.invokecontrol"
private const val PLUGIN_MAVEN_PUBLISH: String = "maven-publish"

private const val ANNOTATION_PROCESSOR: String = "annotationProcessor"

private fun buildDepNotation(dependency: String, version: String): String = "$dependency:$version"

private fun DependencyHandler.add(configurationName: String, dependency: String, version: String) {
    add(configurationName, buildDepNotation(dependency, version))
}

private fun buildDepNotation(dependency: String, version: Provider<String>): String =
    buildDepNotation(dependency, version.get())

private fun DependencyHandler.inc(configurationName: String, dependency: String, version: Provider<String>) {
    add(configurationName, buildDepNotation(dependency, version)) // only used in afterEvaluate, so no need for laziness
}

@Suppress("unused")
class PreludeBuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val deferredTasks = mutableListOf<() -> Unit>()
        fun defer(task: () -> Unit) {
            deferredTasks += task
        }
        // set this up now so it runs before all the other plugins' afterEvaluate blocks
        // in particular, this ensures that rfg picks up our config correctly
        project.afterEvaluate {
            deferredTasks.forEach { it() }
        }

        val versionProvider = project.provider { project.version.toString() }

        val plugins = project.pluginManager
        val extensions = project.extensions
        val tasks = project.tasks
        val dependencies = project.dependencies

        project.apply<JavaLibraryPlugin>()
        project.apply<UserDevPlugin>()
        project.apply<KotlinPluginWrapper>()
        project.apply<IdeaExtPlugin>()

        val ext = extensions.create(
            PreludeBuildExtension::class,
            "preludeBuild",
            PreludeBuildExtensionImpl::class,
            project.objects
        )
        ext.configureConventions(project)

        // Project configuration

        extensions.configure<BasePluginExtension> {
            archivesName.set(ext.modId.flatMap { id -> ext.minecraft.mcVersion.map { mcv -> "$id-$mcv" } })
        }

        // Minecraft configuration

        extensions.configure<MinecraftExtension> {
            mcVersion.set(ext.minecraft.mcVersion)
            mcpMappingChannel.set(ext.minecraft.deobf.mappingsChannel)
            mcpMappingVersion.set(ext.minecraft.deobf.mappingsVersion)

            useDependencyAccessTransformers.set(true)

            injectedTags.run {
                put("MOD_ID", ext.modId)
                put("VERSION", versionProvider)
            }

            username.set("Player")
            extraRunJvmArguments.add(ext.basePackage.map { "-ea:$it" })
        }

        plugins.withPlugin(PLUGIN_INVOKE_CONTROL) {
            extensions.configure<ICExtension> {
                annotation("net/minecraftforge/fml/relauncher/SideOnly") { spec ->
                    spec.permissions.addAll(
                        ext.presets.prelude.enable.map {
                            if (it) listOf("prelude.side.physical.!{value}") else emptyList()
                        }
                    )
                }
            }
        }

        tasks.withType<DeobfuscateTask> {
            accessTransformerFiles.from(
                ext.mod.accessTransformerFile.map {
                    if (it.isBlank()) emptyList() else listOf("src/main/resources/META-INF/$it")
                }
            )
        }

        tasks.named<JSTTransformerTask>("applyJST") {
            // https://github.com/GTNewHorizons/RetroFuturaGradle/issues/101
            javaLauncher.set(project.extensions.getByType<MinecraftExtension>().getToolchainLauncher(project, 25))
        }

        val injectTagsTask = tasks.named<InjectTagsTask>("injectTags") {
            outputClassName.set(ext.basePackage.flatMap { bp -> ext.mod.constantsClass.map { cc -> "$bp.$cc" } })
        }

        tasks.named("setupDecompWorkspace") {
            it.dependsOn(injectTagsTask)
        }

        fun RunMinecraftTask.configureDevRun() {
            extraJvmArgs.addAll(
                ext.basePackage.flatMap { bp ->
                    ext.mod.loadingPluginClass.map { lp ->
                        if (lp.isNotBlank()) listOf("-Dfml.coreMods.load=$bp.$lp") else emptyList()
                    }
                }
            )
            extraArgs.addAll(
                ext.presets.mixin.enable.flatMap { enabled ->
                    ext.presets.mixin.mixinConfigs.map { mixinConfigs ->
                        if (enabled) mixinConfigs.flatMap { listOf("--mixin", it) } else emptyList()
                    }
                }
            )
        }
        tasks.named<RunMinecraftTask>("runClient") { configureDevRun() }
        tasks.named<RunMinecraftTask>("runServer") { configureDevRun() }

        fun RunMinecraftTask.configureObfRun() {
            val mcExt = extensions.getByType<MinecraftExtension>()
            username.set(mcExt.username)
            userUUID.set(mcExt.userUUID)
        }
        tasks.named<RunMinecraftTask>("runObfClient") { configureObfRun() }
        tasks.named<RunMinecraftTask>("runObfServer") { configureObfRun() }

        // Compiler configuration

        extensions.configure<JavaPluginExtension> {
            toolchain {
                it.languageVersion.set(JavaLanguageVersion.of(8))
                it.vendor.set(JvmVendorSpec.AZUL) // RFG requires an Azul JVM for its build tasks
            }
        }

        extensions.configure<KotlinJvmExtension> {
            jvmToolchain(8)
            compilerOptions {
                freeCompilerArgs.addAll(
                    ext.presets.prelude.enable.map {
                        if (it) listOf("-Xcontext-parameters", "-Xdont-warn-on-error-suppression") else emptyList()
                    }
                )
            }
        }

        val compileJavaTask = tasks.named<JavaCompile>("compileJava") {
            options.encoding = "UTF-8"
        }

        tasks.named<ProcessResources>("processResources") {
            inputs.property("version", versionProvider)
            filesMatching("mcmod.info") {
                it.expand(
                    "modId" to ext.modId.get(),
                    "modVersion" to versionProvider.get(),
                    "mcVersion" to ext.minecraft.mcVersion.get()
                )
            }
        }

        // some rfg java compile tasks have dependencies that aren't replicated in the associated kotlin compile tasks
        // we manually add these dependencies here to stop gradle from complaining
        extensions.getByType<SourceSetContainer>().forEach { sourceSet ->
            val javaTask = tasks.findByName(sourceSet.compileJavaTaskName) ?: return@forEach
            val kotlinTask = tasks.findByName(sourceSet.getCompileTaskName("kotlin")) ?: return@forEach
            javaTask.dependsOn.forEach { kotlinTask.dependsOn(it) }
        }

        tasks.named<Jar>("jar") {
            manifest { mf -> // not lazy, but the laziness of task evaluation *should* save us here
                val atFile = ext.mod.accessTransformerFile.get()
                if (atFile.isNotBlank()) {
                    mf.attributes("FMLAT" to atFile)
                }
                val lpClass = ext.mod.loadingPluginClass.get()
                if (lpClass.isNotBlank()) {
                    mf.attributes(
                        "FMLCorePlugin" to "${ext.basePackage.get()}.$lpClass",
                        "FMLCorePluginContainsFMLMod" to "true"
                    )
                }
                if (ext.presets.mixin.enable.get()) {
                    val configs = ext.presets.mixin.mixinConfigs.get()
                    if (configs.isNotEmpty()) {
                        mf.attributes(
                            "MixinConfigs" to configs.joinToString(",")
                        )
                    }
                    val cxClass = ext.presets.mixin.mixinConnectorClass.get()
                    if (cxClass.isNotEmpty()) {
                        mf.attributes(
                            "MixinConnector" to "${ext.basePackage.get()}.$cxClass"
                        )
                    }
                }
            }
        }

        // Dependency configuration

        project.repositories {
            mavenLocal()
            maven {
                it.name = "CleanroomMC"
                it.url = URI.create("https://maven.cleanroommc.com")
            }
        }

        defer {
            val depExt = ext.dependencies
            dependencies.inc(API, PreludeDeps.JSPECIFY, depExt.jspecifyVersion)
            dependencies.add(IMPLEMENTATION, PreludeDeps.KOTLIN_REFLECT, PreludeDeps.KOTLIN_REFLECT_VERSION)
            dependencies.inc(RUNTIME_ONLY, PreludeDeps.FORGELIN_CONTINUOUS, depExt.forgelinVersion)

            if (ext.presets.mixin.enable.get()) {
                val mixinBooter = buildDepNotation(PreludeDeps.MIXINBOOTER, depExt.mixinBooterVersion)
                extensions.getByType<ModUtils>().enableMixins(mixinBooter, ext.presets.mixin.refMapFileName.get())
                if (ext.presets.mixin.includeDeps.get()) {
                    dependencies.add(API, mixinBooter) { isTransitive = false }
                    dependencies.add(ANNOTATION_PROCESSOR, mixinBooter)
                }
            }

            if (ext.presets.prelude.enable.get() && ext.presets.prelude.includeDeps.get()) {
                if (plugins.hasPlugin(PLUGIN_INVOKE_CONTROL)) {
                    dependencies.inc(API, PreludeDeps.KT_INVOKE_CONTROL_LIB, depExt.ktInvokeControlVersion)
                }
                dependencies.inc(API, PreludeDeps.PRELUDE_1_12_2, depExt.preludeVersion)
            }
        }

        // Publication configuration

        plugins.withPlugin(PLUGIN_MAVEN_PUBLISH) {
            extensions.configure<PublishingExtension> {
                publications { pubs ->
                    defer {
                        pubs.create<MavenPublication>("mod") {
                            artifactId = "${ext.modId.get()}-${ext.minecraft.mcVersion.get()}"
                            from(project.components.getByName("java"))
                        }
                    }
                }
            }
        }

        // IDE configuration

        project.configure<IdeaModel> {
            module {
                it.isDownloadJavadoc = true
                it.isDownloadSources = true
                it.inheritOutputDirs = true // Fix resources in IJ-Native runs
            }
            project { ijProject ->
                ijProject.settings {
                    runConfigurations {
                        add(Gradle("1. Run Client").apply {
                            setProperty("taskNames", listOf("runClient"))
                        })
                        add(Gradle("2. Run Server").apply {
                            setProperty("taskNames", listOf("runServer"))
                        })
                        add(Gradle("3. Run Obfuscated Client").apply {
                            setProperty("taskNames", listOf("runObfClient"))
                        })
                        add(Gradle("4. Run Obfuscated Server").apply {
                            setProperty("taskNames", listOf("runObfServer"))
                        })
                    }
                    compiler {
                        defer {
                            javac { jcConfig ->
                                jcConfig.javacAdditionalOptions = "-encoding utf8"
                                jcConfig.moduleJavacAdditionalOptions = mapOf(
                                    Pair(
                                        (project.name + ".main"),
                                        compileJavaTask.get().options.compilerArgs.joinToString(" ") { "\"$it\"" }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

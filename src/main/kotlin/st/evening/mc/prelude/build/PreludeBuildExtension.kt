package st.evening.mc.prelude.build

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

interface ModSpec {
    val constantsClass: Property<String>
    val accessTransformerFile: Property<String>
    val loadingPluginClass: Property<String>
}

interface DeobfuscationSpec {
    val mappingsChannel: Property<String>
    val mappingsVersion: Property<String>
}

interface MinecraftSpec {
    val mcVersion: Property<String>
    val deobf: DeobfuscationSpec

    fun deobf(action: Action<DeobfuscationSpec>)
}

interface DependenciesSpec {
    val jspecifyVersion: Property<String>
    val forgelinVersion: Property<String>
    val mixinBooterVersion: Property<String>
    val ktInvokeControlVersion: Property<String>
    val preludeVersion: Property<String>
}

interface PresetSpec {
    val enable: Property<Boolean>
    val includeDeps: Property<Boolean>
}

interface MixinPresetSpec : PresetSpec {
    val refMapFileName: Property<String>
    val mixinConfigs: ListProperty<String>
    val mixinConnectorClass: Property<String>
}

interface PresetsSpec {
    val mixin: MixinPresetSpec
    val prelude: PresetSpec

    fun mixin(action: Action<MixinPresetSpec>)
    fun prelude(action: Action<PresetSpec>)
}

interface PreludeBuildExtension {
    val modId: Property<String>
    val basePackage: Property<String>

    val mod: ModSpec
    val minecraft: MinecraftSpec
    val dependencies: DependenciesSpec
    val presets: PresetsSpec

    fun mod(action: Action<ModSpec>)
    fun minecraft(action: Action<MinecraftSpec>)
    fun dependencies(action: Action<DependenciesSpec>)
    fun presets(action: Action<PresetsSpec>)
}

private fun PresetSpec.configureConventions() {
    enable.convention(false)
    includeDeps.convention(true)
}

internal fun PreludeBuildExtension.configureConventions(project: Project) {
    basePackage.convention(project.provider { project.group.toString() })
    mod.run {
        constantsClass.convention(modId.map { "${it.snakeToPascal()}Consts" })
        accessTransformerFile.convention("")
        loadingPluginClass.convention("")
    }
    minecraft.run {
        mcVersion.convention("1.12.2")
        deobf.run {
            mappingsChannel.convention("stable")
            mappingsVersion.convention("39")
        }
    }
    dependencies.run {
        jspecifyVersion.convention(PreludeDeps.JSPECIFY_VERSION)
        forgelinVersion.convention(PreludeDeps.FORGELIN_CONTINUOUS_VERSION)
        mixinBooterVersion.convention(PreludeDeps.MIXINBOOTER_VERSION)
        ktInvokeControlVersion.convention(PreludeDeps.KT_INVOKE_CONTROL_LIB_VERSION)
        preludeVersion.convention(PreludeDeps.PRELUDE_1_12_2_VERSION)
    }
    presets.run {
        mixin.configureConventions()
        mixin.refMapFileName.convention(modId.map { "mixins.$it.refmap.json" })
        mixin.mixinConfigs.convention(modId.map { listOf("$it.mixin.json") })
        mixin.mixinConnectorClass.convention("")
        prelude.configureConventions()
    }
}

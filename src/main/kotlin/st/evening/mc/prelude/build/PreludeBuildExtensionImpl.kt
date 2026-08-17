package st.evening.mc.prelude.build

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property

internal abstract class PreludeBuildExtensionImpl(objects: ObjectFactory) : PreludeBuildExtension {
    override val modId: Property<String> = objects.property()
    override val basePackage: Property<String> = objects.property()

    private class ModSpecImpl(objects: ObjectFactory) : ModSpec {
        override val constantsClass: Property<String> = objects.property()
        override val accessTransformerFile: Property<String> = objects.property()
        override val loadingPluginClass: Property<String> = objects.property()
    }

    private class MinecraftSpecImpl(objects: ObjectFactory) : MinecraftSpec {
        override val mcVersion: Property<String> = objects.property()
        override val deobf: DeobfuscationSpec = objects.newInstance()

        override fun deobf(action: Action<DeobfuscationSpec>) {
            action.execute(deobf)
        }
    }

    private class DependenciesSpecImpl(objects: ObjectFactory) : DependenciesSpec {
        override val jspecifyVersion: Property<String> = objects.property()
        override val forgelinVersion: Property<String> = objects.property()
        override val mixinBooterVersion: Property<String> = objects.property()
        override val ktInvokeControlVersion: Property<String> = objects.property()
        override val preludeVersion: Property<String> = objects.property()
    }

    private open class PresetSpecImpl(objects: ObjectFactory) : PresetSpec {
        override val enable: Property<Boolean> = objects.property()
        override val includeDeps: Property<Boolean> = objects.property()
    }

    private class MixinPresetSpecImpl(objects: ObjectFactory) : PresetSpecImpl(objects), MixinPresetSpec {
        override val refMapFileName: Property<String> = objects.property()
        override val mixinConfigs: ListProperty<String> = objects.listProperty()
        override val mixinConnectorClass: Property<String> = objects.property()
    }

    private class PresetsSpecImpl(objects: ObjectFactory) : PresetsSpec {
        override val mixin: MixinPresetSpec = MixinPresetSpecImpl(objects)
        override val prelude: PresetSpec = PresetSpecImpl(objects)

        override fun mixin(action: Action<MixinPresetSpec>) {
            action.execute(mixin)
        }

        override fun prelude(action: Action<PresetSpec>) {
            action.execute(prelude)
        }
    }

    override val mod: ModSpec = ModSpecImpl(objects)
    override val minecraft: MinecraftSpec = MinecraftSpecImpl(objects)
    override val dependencies: DependenciesSpec = DependenciesSpecImpl(objects)
    override val presets: PresetsSpec = PresetsSpecImpl(objects)

    override fun mod(action: Action<ModSpec>) {
        action.execute(mod)
    }

    override fun minecraft(action: Action<MinecraftSpec>) {
        action.execute(minecraft)
    }

    override fun dependencies(action: Action<DependenciesSpec>) {
        action.execute(dependencies)
    }

    override fun presets(action: Action<PresetsSpec>) {
        action.execute(presets)
    }
}

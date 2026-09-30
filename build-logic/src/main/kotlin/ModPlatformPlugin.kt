@file:Suppress("unused", "DuplicatedCode")

import dev.kikugie.fletching_table.extension.FletchingTableExtension
import dev.kikugie.stonecutter.StonecutterExperimentalAPI
import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.extensions.stdlib.toDefaultLowerCase
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import javax.inject.Inject

val Project.sc: StonecutterBuildExtension
	get() = extensions.getByType<StonecutterBuildExtension>()

@OptIn(StonecutterExperimentalAPI::class)
fun Project.prop(name: String): String = (project.sc.properties.get<String>(name))

fun Project.env(variable: String): String? = providers.environmentVariable(variable).orNull

fun Project.envTrue(variable: String): Boolean = env(variable)?.toDefaultLowerCase() == "true"

fun RepositoryHandler.strictMaven(
	url: String, vararg groups: String, configure: MavenArtifactRepository.() -> Unit = {}
) = exclusiveContent {
	forRepository { maven(url) { configure() } }
	filter { groups.forEach(::includeGroup) }
}

abstract class GenerateModManifestTask : DefaultTask() {
	@get:Input
	abstract val content: Property<String>

	@get:OutputFile
	abstract val outputFile: RegularFileProperty

	@TaskAction
	fun generate() {
		val file = outputFile.get().asFile
		file.parentFile.mkdirs()
		file.writeText(content.get())
	}
}

abstract class ModPlatformPlugin @Inject constructor() : Plugin<Project> {
	override fun apply(project: Project) = with(project) {
		val inferredLoader = Loader.of(project.buildFile.name.substringAfter('.').replace(".gradle.kts", ""))

		val extension = extensions.create("platform", ModPlatformExtension::class.java).apply {
			loader.convention(inferredLoader.id)
			jarTask.convention(inferredLoader.jarTask)
			sourcesJarTask.convention(inferredLoader.sourcesJarTask)
		}

		listOf("org.jetbrains.kotlin.jvm", "com.google.devtools.ksp", "dev.kikugie.fletching-table").forEach {
			apply(
				plugin = it
			)
		}

		afterEvaluate {
			val ctx = Context(
				project = this,
				extension = extension,
				loader = Loader.of(extension.loader.get()),
				stonecutter = project.sc
			)
			configureProject(ctx)
		}
	}

	private fun Project.configureProject(ctx: Context) {
		listOf("java", "me.modmuss50.mod-publish-plugin", "idea").forEach { apply(plugin = it) }

		version = ctx.fullVersion
		ctx.extension.requiredJava.set(ctx.javaVersion)

		if (ctx.loader.isFabricLike) {
			ctx.extension.dependencies {
				required("java") { fabricLikeVersionRange = ">=${ctx.javaVersion.majorVersion}" }
			}
		}

		configureFletchingTable(ctx)
		registerGenerateManifestTask(ctx)
		configureJarTask(ctx)
		configureIdea()
		configureProcessResources(ctx)
		configureJava(ctx)
		registerBuildAndCollectTask(ctx)

		configureModPublishing(ctx)

		if (envTrue("PUB_MAVEN_ENABLE")) {
			configureMavenPublishing(ctx)
		}
	}

	private fun Project.configureJava(ctx: Context) {
		extensions.configure<JavaPluginExtension>("java") {
			withSourcesJar()
			withJavadocJar()
			sourceCompatibility = ctx.javaVersion
			targetCompatibility = ctx.javaVersion
		}
	}

	private fun Project.registerGenerateManifestTask(ctx: Context) {
		val manifestOutputDir = layout.buildDirectory.dir("generated/modManifest")
		val generateTask = tasks.register<GenerateModManifestTask>("generateModManifest") {
			content.set(ctx.loader.generateManifest(ctx))
			outputFile.set(layout.buildDirectory.file("generated/modManifest/${ctx.loader.modManifestPath}"))
		}

		the<JavaPluginExtension>().sourceSets.named("main") { resources.srcDir(manifestOutputDir) }
		tasks.named<ProcessResources>("processResources") { dependsOn(generateTask) }
		tasks.withType<Jar>().configureEach {
			if (name == ctx.loader.sourcesJarTask) {
				dependsOn(generateTask)
			}
		}
	}

	private fun Project.configureProcessResources(ctx: Context) {
		tasks.named<ProcessResources>("processResources") {
			dependsOn(tasks.named("stonecutterGenerate"), "kspKotlin")
			filesMatching("*.mixins.json") {
				expand("java" to "JAVA_${ctx.javaVersion.majorVersion}")
				// Forge runs on SRG names, and a mixin config only finds the names it was compiled against through a
				// refmap it names itself. Every config shares the one the annotation processor writes.
				if (ctx.loader is Loader.Forge) {
					filter { line ->
						line.replace("\"package\":", "\"refmap\": \"${ctx.modId}.mixins.refmap.json\", \"package\":")
					}
				}
			}
			exclude(ctx.loader.excludedResources)
			// Polytone has no shadow map before 1.21.1, so nothing there lists the Polytone mixins.
			if (!ctx.stonecutter.eval(ctx.currentMcVersion, ">=1.21.1")) {
				exclude("*.polytone.mixins.json")
			}
			// Nor is there Polytone support on 26.3 yet.
			if (ctx.stonecutter.eval(ctx.currentMcVersion, ">=26.3")) {
				exclude("*.polytone.mixins.json")
			}
			// Nor, where there is no Iris to write it against, the shader pack support.
			if (!ctx.shaderPackSupport) {
				exclude("*.iris.mixins.json")
			}
			// Snow! Real Magic draws through its own copy of Indigo only on Forge.
			if (ctx.loader !is Loader.Forge) {
				exclude("*.snowrealmagic.mixins.json")
			}
			// And through the one it carries on NeoForge, where Sway bends by model data, before 1.21.11.
			if (ctx.loader !is Loader.NeoForge || ctx.stonecutter.eval(ctx.currentMcVersion, ">=1.21.11")) {
				exclude("*.snowrealmagic_neoforge.mixins.json")
			}
			// Forgified Fabric API's block getter interface on the level's views, which Iris keeps from them, from 26.1.2.
			if (ctx.loader !is Loader.NeoForge || !ctx.stonecutter.eval(ctx.currentMcVersion, ">=26.1.2")) {
				exclude("*.blockgetter.mixins.json")
			}
			// Vanilla's render pass is only handed to the foliage from 26.3.
			if (!ctx.stonecutter.eval(ctx.currentMcVersion, ">=26.3")) {
				exclude("*.pass.mixins.json")
			}
			// The GPU foliage renderer has one set of shaders per generation of Minecraft's renderer, each in
			// the shading language that generation speaks; a jar only carries the set its version loads. Before
			// 1.21.1 the legacy set has a vertex shader of its own, and shares the legacy fragment shader.
			// From 26.3 the language has includes and a location on every input: the modern set, and only that set,
			// with the sway shared by all of them and the foliage's uniform blocks in an include of their own. The terrain's spliced shaders are not carried there yet.
			if (ctx.stonecutter.eval(ctx.currentMcVersion, ">=26.3")) {
				exclude("assets/*/shaders/core/foliage.*", "assets/*/shaders/core/foliage_terrain.vsh",
						"assets/*/shaders/core/foliage_legacy.*", "assets/*/shaders/core/foliage_legacy_1_20.*")
			} else if (ctx.stonecutter.eval(ctx.currentMcVersion, ">=1.21.11")) {
				exclude("assets/*/shaders/core/foliage_modern.*", "assets/*/shaders/include/foliage_uniforms.glsl",
						"assets/*/shaders/core/foliage_legacy.*", "assets/*/shaders/core/foliage_legacy_1_20.*")
			} else if (ctx.stonecutter.eval(ctx.currentMcVersion, ">=1.21.1")) {
				exclude("assets/*/shaders/core/foliage.*", "assets/*/shaders/core/foliage_modern.*",
						"assets/*/shaders/include/foliage_uniforms.glsl", "assets/*/shaders/core/foliage_legacy_1_20.*")
			} else {
				exclude("assets/*/shaders/core/foliage.*", "assets/*/shaders/core/foliage_modern.*",
						"assets/*/shaders/include/foliage_uniforms.glsl",
						"assets/*/shaders/core/foliage_legacy.json", "assets/*/shaders/core/foliage_legacy.vsh")
			}
		}
	}

	private fun Project.configureJarTask(ctx: Context) {
		val generateTask = tasks.named("generateModManifest")
		tasks.withType<Jar>().configureEach {
			archiveBaseName.set(ctx.modId)
			dependsOn(generateTask)
			if (ctx.loader is Loader.Forge) {
				// Forge reads a jar's mixin configs from its manifest, not from mods.toml.
				manifest.attributes(ctx.loader.mixinConfigAttribute to
						"${ctx.modId}.mixins.json,${ctx.modId}.gpu.mixins.json,${ctx.modId}.iris.mixins.json,"
								+ "${ctx.modId}.snowrealmagic.mixins.json")
			}
		}
	}

	private fun Project.configureIdea() {
		extensions.configure<IdeaModel>("idea") {
			module {
				isDownloadJavadoc = true
				isDownloadSources = true
			}
		}
	}

	private fun Project.configureFletchingTable(ctx: Context) {
		extensions.configure<FletchingTableExtension> {
			// No mixin list is generated: every mixin configuration names its mixins itself, and the Iris and Polytone ones
			// are applied only through their plugins, which a generated list would go around.
			j52j.register("main") { extension("json", "**/*.json5") }
		}
	}

	private fun Project.registerBuildAndCollectTask(ctx: Context) {
		tasks.register<Copy>("buildAndCollect") {
			from(
				tasks.named(ctx.extension.jarTask.get()),
				tasks.named(ctx.extension.sourcesJarTask.get()),
				tasks.named("javadocJar")
			)
			into(rootProject.layout.buildDirectory.file("libs/${ctx.basicVersion}"))
			dependsOn("build")
		}
	}
}

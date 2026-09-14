plugins {
	id("mod-platform")
	id("net.fabricmc.fabric-loom-remap")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

// Minecraft before 1.20.5 ships LWJGL 3.3.1, which cannot run on Java 19 or later. Loom launches the dev
// client on Gradle's own Java and, seeing a newer one, swaps in LWJGL 3.3.2 to cope -- which Sodium 0.5
// refuses to start with. Those versions are run on the Java they were made for instead, and Loom is told
// so, which leaves their own LWJGL in place.
if (stonecutter.eval(stonecutter.current.version, "<1.20.5")) {
	val runtimeJava = 17
	extra["fabric.loom.runtimeJavaCompatibilityVersion"] = runtimeJava.toString()
	tasks.withType<JavaExec>().configureEach {
		javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(runtimeJava) }
	}
}

platform {
	loader = "fabric-o"
	dependencies {
		required("minecraft") {
			fabricLikeVersionRange = prop("deps.minecraft")
		}
		required("fabric-api") {
			slug("fabric-api")
			fabricLikeVersionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			fabricLikeVersionRange = ">=${prop("deps.fabric-loader")}"
		}
		required("sway") {
			slug("sway")
			fabricLikeVersionRange = "*"
		}
		optional("modmenu") {}
		// Sodium 0.5 meshes blocks without Fabric's rendering API, which both Sway and the GPU renderer work through,
		// so under Sodium alone the mod does nothing at all; Indium adds that API back. Indium itself requires Sodium.
		// Required only where the mod is published, so launchers install both, while the jar still runs without them.
		if (stonecutter.eval(stonecutter.current.version, "<1.21.1")) {
			publishRequired("indium") {
				slug("indium")
			}
		}
	}
}

loom {
	accessWidenerPath = rootProject.file("src/main/resources/aw/${sc.current.version}.accesswidener")
	runs.named("client") {
		client()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "client"
		programArgs("--username=Dev")
		configName = "Fabric Client"
	}
	runs.named("server") {
		server()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "server"
		configName = "Fabric Server"
	}
}

fabricApi {
	configureDataGeneration {
		outputDirectory = file("${rootDir}/versions/datagen/${sc.current.version.split("-")[0]}/src/main/generated")
		client = true
	}
}

repositories {
	mavenCentral()
	strictMaven("https://maven.terraformersmc.com/", "com.terraformersmc") { name = "TerraformersMC" }
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
}

// Third-party mods for the dev client live in devmods/<version>-<loader>/ rather than run/mods.
// Two reasons they cannot just be dropped into the mods folder:
//  - production jars reference intermediary names, so without Loom's build-time remap the game
//    dies during bootstrap with NoClassDefFoundError on class_* names;
//  - mods bundle their libraries as jar-in-jar, and Fabric Loader only unpacks those for mods in
//    the mods folder, not for classpath mods, so they are extracted here by hand.
val devModJars = rootProject.file("devmods/${stonecutter.current.project}")
	.listFiles()?.filter { it.extension == "jar" }.orEmpty()

val devModNestedDir = layout.buildDirectory.dir("devmods-nested").get().asFile

val devModNestedJars = if (devModJars.isEmpty()) emptyList() else {
	devModNestedDir.deleteRecursively()
	devModJars.forEach { jar ->
		copy {
			// Fabric API modules a mod bundles are already here through fabric-api itself; unremapped, they would shadow it.
			from(zipTree(jar)) { include("META-INF/jars/*.jar"); exclude("META-INF/jars/fabric-*.jar") }
			into(devModNestedDir)
			eachFile { path = name }
			includeEmptyDirs = false
		}
	}
	devModNestedDir.listFiles()?.filter { it.extension == "jar" }.orEmpty()
}

configurations.all {
	resolutionStrategy {
		force("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	mappings(
		loom.layered {
			officialMojangMappings()
			if (hasProperty("deps.parchment")) parchment("org.parchmentmc.data:parchment-${prop("deps.parchment")}@zip")
		})
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	// implementation(libs.moulberry.mixinconstraints)
	// include(libs.moulberry.mixinconstraints)
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	// Optional dependency: compiled against for the Mod Menu entrypoint, shipped by nobody.
	modCompileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
	modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
	modImplementation("maven.modrinth:sway:${prop("deps.sway")}")
	// Optional dependencies: compiled against for the shader pack support, where it has been written.
	if (stonecutter.eval(stonecutter.current.version, ">=1.21.1")) {
		modCompileOnly("maven.modrinth:iris:${prop("deps.iris")}")
		modCompileOnly("maven.modrinth:sodium:${prop("deps.sodium")}")
	}

	// Third-party mods dropped into devmods/<version>-<loader>/, remapped by Loom.
	devModJars.forEach { modLocalRuntime(files(it)) }
	// ...plus the libraries they bundle as jar-in-jar, unpacked above.
	devModNestedJars.forEach { runtimeOnly(files(it)) }
}

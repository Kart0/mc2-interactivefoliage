plugins {
	id("mod-platform")
	id("net.fabricmc.fabric-loom-remap")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
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
			from(zipTree(jar)) { include("META-INF/jars/*.jar") }
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

	// Third-party mods dropped into devmods/<version>-<loader>/, remapped by Loom.
	devModJars.forEach { modLocalRuntime(files(it)) }
	// ...plus the libraries they bundle as jar-in-jar, unpacked above.
	devModNestedJars.forEach { runtimeOnly(files(it)) }
}

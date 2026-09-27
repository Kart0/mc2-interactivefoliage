plugins {
	id("mod-platform")
	id("net.fabricmc.fabric-loom")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

platform {
	loader = "fabric-m"
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
	accessWidenerPath = rootProject.file("src/main/resources/aw/${stonecutter.current.version}.accesswidener")
	runs.named("client") {
		client()
		ideConfigGenerated(true)
		runDir = "run/"
		environment = "client"
		programArgs("--username=Dev")
		configName = "Fabric Client"
		// From 26.3 the game compiles its shaders with native code (shaderc) on its worker threads, and Mojang's launcher
		// starts it with the JVM arguments that needs; Loom does not pass them. Without the extra stack shadow pages the
		// compiler overflows a worker's stack now and then while the resources load, and the client dies natively
		// (0xC0000005) with no crash report.
		if (stonecutter.eval(stonecutter.current.version, ">=26.3")) {
			vmArgs("-XX:StackShadowPages=32", "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")
		}
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

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	implementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	// implementation(libs.moulberry.mixinconstraints)
	// include(libs.moulberry.mixinconstraints)
	implementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	// Optional dependency: compiled against for the Mod Menu entrypoint, shipped by nobody.
	compileOnly("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
	localRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
	implementation("maven.modrinth:sway:${prop("deps.sway")}")
	// Optional dependencies: compiled against for the shader pack support, where it has been written.
	if (stonecutter.eval(stonecutter.current.version, ">=26.1.2")) {
		if (hasProperty("deps.iris")) {
			compileOnly("maven.modrinth:iris:${prop("deps.iris")}")
		}
		compileOnly("maven.modrinth:sodium:${prop("deps.sodium")}")
	}
}

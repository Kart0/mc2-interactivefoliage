plugins {
	id("mod-platform")
	id("net.neoforged.moddev.legacyforge")
}

stonecutter {
	val (version, loader) = current.project.split('-', limit = 2)
	properties.tags(version, loader)
}

platform {
	loader = "forge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("forge") {
			forgeLikeVersionRange.set("[1,)")
		}
	}
}

legacyForge {
	version = "${prop("deps.minecraft")}-${prop("deps.forge")}"

	validateAccessTransformers = true

	accessTransformers.from(
		rootProject.file("src/main/resources/aw/${sc.current.version}.cfg")
	)

	runs {
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "Forge Client (${sc.current.version})"
			programArgument("--username=Dev")
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "Forge Server (${sc.current.version})"
		}
	}


	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
}

mixin {
	add(sourceSets.main.get(), "${prop("mod.id")}.mixins.refmap.json")
	config("${prop("mod.id")}.mixins.json")
	config("${prop("mod.id")}.gpu.mixins.json")
}

repositories {
	mavenCentral()
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
}

dependencies {
	annotationProcessor("org.spongepowered:mixin:${libs.versions.mixin.get()}:processor")
	modImplementation("maven.modrinth:sway:${prop("deps.sway")}")
	// implementation(libs.moulberry.mixinconstraints)
	// jarJar(libs.moulberry.mixinconstraints)
}

// To exercise ModCompatRegistry against real blocks in the dev client, add third-party mods here
// rather than dropping them into run/mods:
//
//     dependencies { modRuntimeOnly("maven.modrinth:farmers-delight:1.20.1-1.3.4") }
//
// Production jars ship SRG names while the Forge dev runtime uses named mappings, and only the
// "mod" prefixed configurations get remapped; an unremapped jar fails to apply its own mixins and
// kills the client during the FML loading screen, before the main menu appears.
dependencies {
	// Biomes O' Plenty and the two libraries it needs, for foliage from another mod.
	modRuntimeOnly("maven.modrinth:biomes-o-plenty:jxUqRzSD") // 19.0.0.96
	modRuntimeOnly("maven.modrinth:glitchcore:pYPZ5MNI") // 0.0.1.1
	modRuntimeOnly("maven.modrinth:terrablender:zGconCHG") // 3.0.1.10
	// Embeddium, the chunk mesher Forge players use instead of Sodium. Left out with -PwithoutEmbeddium, to
	// compare against vanilla's own. The name has no dot in it: PowerShell splits a bare argument at one.
	if (!hasProperty("withoutEmbeddium")) {
		modRuntimeOnly("maven.modrinth:embeddium:UTbfe5d1") // 0.3.31+mc1.20.1
	}
}

sourceSets {
	main {
		resources.srcDir(
			"${rootDir}/versions/datagen/${sc.current.version.split("-")[0]}/src/main/generated"
		)
	}
}

tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}

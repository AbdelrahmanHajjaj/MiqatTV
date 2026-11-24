plugins { kotlin("multiplatform") version "2.2.10" }
group = "tv.miqat"
kotlin {
    jvmToolchain(17)
    jvm()
    // Opt-in keeps ordinary Android/ES5 builds independent of Apple toolchains.
    if (providers.gradleProperty("miqatApple").orNull == "true") {
        macosArm64()
        listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
            target.binaries.framework {
                baseName = "MiqatKit"
                isStatic = true
                export("tv.miqat:core")
            }
        }
    }
    sourceSets.commonMain.dependencies { api("tv.miqat:core") }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}

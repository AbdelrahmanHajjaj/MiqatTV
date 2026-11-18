plugins { kotlin("multiplatform") version "2.2.10" }
group = "tv.miqat"
kotlin {
    jvmToolchain(17)
    jvm()
    // Opt-in keeps ordinary Android/ES5 builds independent of Apple toolchains.
    if (providers.gradleProperty("miqatApple").orNull == "true") {
        macosArm64()
        iosArm64()
        iosSimulatorArm64()
    }
    sourceSets.commonMain.dependencies { implementation("tv.miqat:core") }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}

plugins { kotlin("multiplatform") version "2.2.10" }
group = "tv.miqat"
kotlin {
    jvmToolchain(17)
    jvm()
    sourceSets.commonMain.dependencies { implementation("tv.miqat:core") }
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}

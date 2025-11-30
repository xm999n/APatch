@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.tasks.PackageAndroidArtifact
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import org.gradle.api.Project
import java.io.File

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.lsplugin.apksign)
    alias(libs.plugins.lsplugin.resopt)
    alias(libs.plugins.lsplugin.cmaker)
    id("kotlin-parcelize")
}

val managerVersionCode: Int by rootProject.extra
val managerVersionName: String by rootProject.extra
val branchname: String by rootProject.extra
val kernelPatchVersion: String by rootProject.extra

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

android {
    namespace = "me.bmax.apatch"

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            multiDexEnabled = true
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-debuginfo-remove.pro",
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "NONE"
            }
        }
    }

    // 把 dependenciesInfo 放在 android 下
    dependenciesInfo.includeInApk = false

    // https://stackoverflow.com/a/77745844
    // 注意：PackageAndroidArtifact 在不同 AGP 版本可能不存在或不同包名 -> 若构建报错请改为按任务名或删除此段
    tasks.withType<PackageAndroidArtifact> {
        doFirst { appMetadata.asFile.orNull?.writeText("") }
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        compose = true
        prefab = true
    }

    defaultConfig {
        buildConfigField("String", "buildKPV", "\"$kernelPatchVersion\"")

        base.archivesName = "APatch_${managerVersionCode}_${managerVersionName}_on_${branchname}"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
        merges += "META-INF/MANIFEST.MF"  
        merges += "META-INF/ALIAS_AP.SF"  
        merges += "META-INF/ALIAS_AP.RSA"  
        merges += "META-INF/com/google/android/**"  
        excludes += "kotlin/**"  
        excludes += "okhttp3/**"  
        excludes += "org/**"  
        excludes += "DebugProbesKt.bin"  
        excludes += "kotlin-tooling-metadata.json"  
        excludes += "assets/dexopt/**"  
        excludes += "META-INF/**"  
        pickFirst("META-INF/MANIFEST.MF")  
        pickFirst("META-INF/ALIAS_AP.SF")  
        pickFirst("META-INF/ALIAS_AP.RSA")
        }
        jniLibs {
            useLegacyPackaging = true
            exclude("lib/armeabi-v7a/**")
            exclude("lib/x86/**")
            exclude("lib/x86_64/**")
            exclude("lib/armeabi/**")
        }
    }

    splits {
        abi {
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    externalNativeBuild {
        cmake {
            version = "3.28.0+"
            path("src/main/cpp/CMakeLists.txt")
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    sourceSets["main"].jniLibs.srcDir("libs")

    // 为每个 application variant 注册 kotlin 生成目录
    applicationVariants.all {
        kotlin.sourceSets {
            // name 是 variant 名称（如 debug/release），这里保留原逻辑
            getByName(name) {
                kotlin.srcDir("build/generated/ksp/$name/kotlin")
            }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

fun registerDownloadTask(
    taskName: String, srcUrl: String, destPath: String, project: Project
) {
    project.tasks.register(taskName) {
        val destFile = File(destPath)

        doLast {
            if (!destFile.exists() || isFileUpdated(srcUrl, destFile)) {
                println(" - Downloading $srcUrl to ${destFile.absolutePath}")
                downloadFile(srcUrl, destFile)
                println(" - Download completed.")
            } else {
                println(" - File is up-to-date, skipping download.")
            }
        }
    }
}

fun isFileUpdated(url: String, localFile: File): Boolean {
    val connection = URI.create(url).toURL().openConnection()
    val remoteLastModified = connection.getHeaderFieldDate("Last-Modified", 0L)
    return remoteLastModified > localFile.lastModified()
}

fun downloadFile(url: String, destFile: File) {
    URI.create(url).toURL().openStream().use { input ->
        destFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

registerDownloadTask(
    taskName = "downloadKpimg",
    srcUrl = "https://github.com/bmax121/KernelPatch/releases/download/$kernelPatchVersion/kpimg-android",
    destPath = "${project.projectDir}/src/main/assets/kpimg",
    project = project
)

registerDownloadTask(
    taskName = "downloadKptools",
    srcUrl = "https://github.com/bmax121/KernelPatch/releases/download/$kernelPatchVersion/kptools-android",
    destPath = "${project.projectDir}/libs/arm64-v8a/libkptools.so",
    project = project
)

// Compat kp version less than 0.10.7
// TODO: Remove in future
registerDownloadTask(
    taskName = "downloadCompatKpatch",
    srcUrl = "https://github.com/bmax121/KernelPatch/releases/download/0.10.7/kpatch-android",
    destPath = "${project.projectDir}/libs/arm64-v8a/libkpatch.so",
    project = project
)

tasks.register<Copy>("mergeScripts") {
    into("${project.projectDir}/src/main/resources/META-INF/com/google/android")
    from(rootProject.file("${project.rootDir}/scripts/update_binary.sh")) {
        rename { "update-binary" }
    }
    from(rootProject.file("${project.rootDir}/scripts/update_script.sh")) {
        rename { "updater-script" }
    }
}

// 更稳健的方式绑定 preBuild 依赖
tasks.named("preBuild") {
    dependsOn(
        "downloadKpimg",
        "downloadKptools",
        "downloadCompatKpatch",
        "mergeScripts",
    )
}

// cargo / apd 相关任务（保留原结构）
tasks.register<Exec>("cargoBuild") {
    executable("cargo")
    args("ndk", "-t", "arm64-v8a", "build", "--release")
    workingDir("${project.rootDir}/apd")
}

tasks.register<Copy>("buildApd") {
    dependsOn("cargoBuild")
    from("${project.rootDir}/apd/target/aarch64-linux-android/release/apd")
    into("${project.projectDir}/libs/arm64-v8a")
    rename("apd", "libapd.so")
}

tasks.configureEach {
    if (name == "mergeDebugJniLibFolders" || name == "mergeReleaseJniLibFolders") {
        dependsOn("buildApd")
    }
}

tasks.register<Exec>("cargoClean") {
    executable("cargo")
    args("clean")
    workingDir("${project.rootDir}/apd")
}

tasks.register<Delete>("apdClean") {
    dependsOn("cargoClean")
    delete(file("${project.projectDir}/libs/arm64-v8a/libapd.so"))
}

tasks.clean {
    dependsOn("apdClean")
}

ksp {
    arg("compose-destinations.defaultTransitions", "none")
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.runtime.livedata)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.compose.destinations.core)
    ksp(libs.compose.destinations.ksp)

    implementation(libs.com.github.topjohnwu.libsu.core)
    implementation(libs.com.github.topjohnwu.libsu.service)
    implementation(libs.com.github.topjohnwu.libsu.nio)
    implementation(libs.com.github.topjohnwu.libsu.io)

    implementation(libs.dev.rikka.rikkax.parcelablelist)

    implementation(libs.io.coil.kt.coil.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.me.zhanghai.android.appiconloader.coil)

    implementation(libs.sheet.compose.dialogs.core)
    implementation(libs.sheet.compose.dialogs.list)
    implementation(libs.sheet.compose.dialogs.input)

    implementation(libs.markdown)

    implementation(libs.ini4j)

    compileOnly(libs.cxx)
}

cmaker {
    default {
        arguments += "-DANDROID_STL=none"
        arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
        abiFilters("arm64-v8a")
        cppFlags += "-std=c++2b"
        cFlags += "-std=c2x"
    }
}
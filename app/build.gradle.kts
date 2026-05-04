import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val ksFile = rootProject.file("signing.properties")
val props = Properties()
val hasSigningConfig = ksFile.canRead()

// 只在有签名文件时才创建 sign 配置
if (hasSigningConfig) {
    props.load(FileInputStream(ksFile))
    android.signingConfigs.create("sign").apply {
        storeFile = file(props["KEYSTORE_FILE"] as String)
        storePassword = props["KEYSTORE_PASSWORD"] as String
        keyAlias = props["KEYSTORE_ALIAS"] as String
        keyPassword = props["KEYSTORE_ALIAS_PASSWORD"] as String
    }
}

val gitCommitCountInt = rootProject.extra["gitCommitCountInt"] as Int
val baseVersionName = "2.1.1"
val versionNameSuffixProvider = providers.gradleProperty("versionNameSuffix")
    .orElse("-alpha$gitCommitCountInt")
val finalVersionNameProvider = versionNameSuffixProvider.map { suffix ->
    baseVersionName + suffix
}

android {
    namespace = "yangfentuozi.batteryrecorder"
    compileSdk = 36

    defaultConfig {
        applicationId = "yangfentuozi.batteryrecorder"
        minSdk = 31
        targetSdk = 36
        versionCode = gitCommitCountInt
        versionName = finalVersionNameProvider.get()
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles("proguard-rules.pro")
            // 只在有签名配置时才设置 signingConfig
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("sign")
            } else {
                // 不设置 signingConfig，release 构建将不会被签名
                // 如果想要 debug 签名，可以取消下面的注释
                // signingConfig = signingConfigs.getByName("debug")
            }
        }
        debug {
            // debug 默认使用 debug 证书，不需要显式设置
            // 如果你想 debug 也不签名，可以注释掉下面这行
            // signingConfig = signingConfigs.getByName("sign")
            
            // 如果之前 debug 使用了 sign 配置，现在移除即可
            // debug 会自动使用 Android 的 debug 证书
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val outputName = if (buildType.name == "release") {
                "batteryrecorder-v${versionName}.apk"
            } else {
                "batteryrecorder-v${versionName}-${name}.apk"
            }
            (this as BaseVariantOutputImpl).outputFileName = outputName
            assembleProvider.get().doLast {
                val outDir = File(rootDir, "out")
                val mappingDir = File(outDir, "mapping").absolutePath
                val apkDir = File(outDir, "apk").absolutePath

                if (buildType.isMinifyEnabled) {
                    copy {
                        from(mappingFileProvider.get())
                        into(mappingDir)
                        rename { _ -> "mapping-${versionName}.txt" }
                    }
                    copy {
                        from(outputFile)
                        into(apkDir)
                    }
                }
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

tasks.register("printVersionMetadata") {
    group = "help"
    description = "打印 app 最终版本信息，供 CI 使用"
    doLast {
        val finalVersionName = finalVersionNameProvider.get()
        println("versionName=$finalVersionName")
        println("versionCode=$gitCommitCountInt")
        println("releaseTag=v$finalVersionName")
        println("releaseTitle=v$finalVersionName")
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":server"))

    // Compose
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.capsule)
    implementation(libs.commonmark)
    debugImplementation(composeBom)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
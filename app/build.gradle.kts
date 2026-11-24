import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Every merge to `main` is uploaded to Google Play, which takes each version code once. So the code
// counts itself: the commits behind the build, a number that only grows on `main`. It needs the
// whole history; a shallow checkout counts too few, and the upload refuses a code that is not new.
val commitCount: Int = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }.getOrElse(1)

// This history began as one commit. 1298 was the highest version code used before it, nearly all
// of them by the private repository it came from. Google Play takes only a code above every
// earlier one, and an installed app updates only to a higher one, so the count goes on from there.
val archiveVersionCodes = 1298

// The upload key stays outside the repository. Without it a release build still compiles, unsigned.
val uploadKeyFile = File(System.getProperty("user.home"), ".config/momentum/upload-key.properties")
val uploadKey: Properties? = uploadKeyFile.takeIf { it.isFile }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "de.nicolasmahn.momentum"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.workouttracker"
        minSdk = 24
        targetSdk = 36
        versionCode = archiveVersionCodes + commitCount
        versionName = "2.3.1-alpha"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getProperty("storeFile"))
                storePassword = uploadKey.getProperty("storePassword")
                keyAlias = uploadKey.getProperty("keyAlias")
                keyPassword = uploadKey.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Connected tests use a disposable package and can never replace Momentum's personal data.
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        create("personal") {
            initWith(getByName("debug"))
            applicationIdSuffix = null
            versionNameSuffix = null
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    androidResources {
        // Lists every translated locale for Android's per-app language settings (API 33+).
        generateLocaleConfig = true
    }
    testOptions {
        // Robolectric resolves real resources, so language tests read the strings the app ships.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            test.jvmArgs("--add-exports", "java.base/jdk.internal.access=ALL-UNNAMED")
            // scripts/mo_acts.py renders Mo's acts through the unit tests; it says what and where.
            providers.gradlePropertiesPrefixedBy("mo.acts.").get().forEach { (name, value) -> test.systemProperty(name, value) }
        }
    }
    lint {
        // StringTranslationTest is the one check that German is complete, listing every gap by file.
        informational += "MissingTranslation"
    }
}

androidComponents {
    // Only the store build takes the public ID, so the owner's `personal` installation and the
    // disposable debug package keep theirs. Google Play never lets a published ID change.
    onVariants(selector().withBuildType("release")) { variant ->
        variant.applicationId.set("de.nicolasmahn.momentum")
    }
}

// A bundle is what goes to Google Play. It must not carry a privacy policy or legal notice with a
// blank still in it, such as `<postal code>`; an APK for trying the build out may.
val checkPrivacyPolicyIsFinal = tasks.register("checkPrivacyPolicyIsFinal") {
    group = "verification"
    description = "Fails while a shipped legal text still holds a blank to fill in."
    val policies = fileTree("src/main/assets/privacy")
    inputs.files(policies)
    doLast {
        val blank = Regex("<[a-z][a-z ]*>")
        val open = policies.files.mapNotNull { file -> blank.find(file.readText())?.let { "${file.name}: ${it.value}" } }
        check(open.isEmpty()) { "The legal texts are not final:\n" + open.joinToString("\n") { "  $it" } }
    }
}

tasks.matching { it.name == "bundleRelease" }.configureEach { dependsOn(checkPrivacyPolicyIsFinal) }

abstract class GuardPhysicalAndroidDevice : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val adbExecutable: RegularFileProperty

    /**
     * Phones kept for testing only, which connected tests may use: one adb serial per line, then a
     * name. The list is each developer's own, so it lives outside the repository and may be missing.
     */
    @get:Internal
    abstract val testPhones: RegularFileProperty

    @TaskAction
    fun guard() {
        val process = ProcessBuilder(
            adbExecutable.get().asFile.absolutePath,
            "devices",
            "-l",
        )
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        check(process.waitFor() == 0) { "Could not inspect connected Android devices." }

        val testPhoneSerials = testPhones.get().asFile.takeIf { it.isFile }?.readLines().orEmpty()
            .filterNot { it.isBlank() || it.startsWith("#") }
            .map { it.trim().split(Regex("\\s+")).first() }
            .toSet()
        val physicalDevices = output
            .lineSequence()
            .drop(1)
            .filter { line -> line.trim().split(Regex("\\s+")).getOrNull(1) == "device" }
            .filterNot { line ->
                val serial = line.trim().substringBefore(" ").substringBefore("\t")
                serial.startsWith("emulator-") ||
                    serial in testPhoneSerials ||
                    "product:sdk_gphone" in line ||
                    "model:sdk_gphone" in line
            }
            .map { line -> line.trim().substringBefore(" ").substringBefore("\t") }
            .toList()

        check(physicalDevices.isEmpty()) {
            "Connected Android tests are blocked while a physical device is attached. " +
                "Use an emulator or a phone listed in ~/.config/momentum/test_phones.txt; never test against a personal installation of Momentum."
        }
    }
}

val guardPhysicalAndroidDevice = tasks.register<GuardPhysicalAndroidDevice>(
    "guardPhysicalAndroidDevice",
) {
    group = "verification"
    description = "Stops connected tests from running on a physical Android device."
    adbExecutable.set(androidComponents.sdkComponents.adb)
    testPhones.set(File(System.getProperty("user.home"), ".config/momentum/test_phones.txt"))
}

tasks.matching { task ->
    task.name.startsWith("connected") && task.name.endsWith("AndroidTest")
}.configureEach {
    dependsOn(guardPhysicalAndroidDevice)
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// The About page's list of open-source software is checked against what the release app ships.
apply(from = rootProject.file("gradle/open-source-licenses.gradle.kts"))

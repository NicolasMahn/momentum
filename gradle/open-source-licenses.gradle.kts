import groovy.json.JsonSlurper
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

/**
 * Keeps the app's list of open-source software honest. `components.json` is what the About page
 * shows, and each of its components names the Maven modules it stands for under `maven`. This
 * fails when the release app packages a module the list does not cover, so whoever adds a library
 * is told to add its license, and when the list still names one the app no longer ships.
 *
 * A `maven` entry covers a module `group:name` that equals it or continues it after a `.`, `:` or
 * `-`: `androidx` covers every AndroidX group, `org.jetbrains.kotlin` does not cover
 * `org.jetbrains.kotlinx`, and `com.android.tools:desugar_jdk_libs` covers that one module only.
 *
 * Applied from `app/build.gradle.kts`. It lives outside `app/` because Android lint crashes on a
 * second Kotlin build script in a module's folder.
 */
abstract class CheckOpenSourceLicenses : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val components: RegularFileProperty

    /** `group:name` of every module that ends up in the release app. */
    @get:Input
    abstract val shippedModules: SetProperty<String>

    @TaskAction
    fun check() {
        val file = components.get().asFile
        val listed = ((JsonSlurper().parse(file) as Map<*, *>)["components"] as List<*>)
            .flatMap { component -> (component as Map<*, *>)["maven"] as List<*>? ?: emptyList<Any>() }
            .map { it as String }
        val shipped = shippedModules.get()

        val unlisted = shipped.filter { module -> listed.none { covers(it, module) } }.sorted()
        check(unlisted.isEmpty()) {
            "The release app ships modules that $file names no license for:\n" +
                unlisted.joinToString("\n") { "  $it" } +
                "\nCheck each one's license at its source, then add it to a component's \"maven\" list or add a component."
        }

        val unused = listed.filter { entry -> shipped.none { covers(entry, it) } }
        check(unused.isEmpty()) {
            "$file names Maven modules the release app no longer ships: ${unused.joinToString()}. " +
                "Remove them, and their component if nothing else of it ships."
        }
    }

    private fun covers(entry: String, module: String): Boolean =
        module == entry || (module.startsWith(entry) && module[entry.length] in ".:-")
}

val checkOpenSourceLicenses = tasks.register<CheckOpenSourceLicenses>("checkOpenSourceLicenses") {
    group = "verification"
    description = "Fails when the release app ships a module that the open-source license list does not cover."
    components.set(layout.projectDirectory.file("src/main/assets/licenses/components.json"))
    // The app's own dependencies, and the library that core library desugaring compiles into it.
    shippedModules.set(
        provider {
            listOf("releaseRuntimeClasspath", "coreLibraryDesugaring")
                .flatMap { configurations.getByName(it).incoming.resolutionResult.allComponents }
                .mapNotNull { (it.id as? ModuleComponentIdentifier)?.let { id -> "${id.group}:${id.module}" } }
                .toSet()
        },
    )
}

tasks.named("check") { dependsOn(checkOpenSourceLicenses) }

// Every release build, an APK or a bundle, starts with this task.
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(checkOpenSourceLicenses) }

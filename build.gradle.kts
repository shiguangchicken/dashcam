import com.android.build.gradle.BaseExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

subprojects {
    plugins.withId("com.android.base") {
        extensions.configure<BaseExtension>("android") {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

tasks.register("ktlintCheck") {
    group = "verification"
    description = "Runs the repository formatting baseline check."

    doLast {
        val ignoredDirs = setOf(".gradle", "build")
        val violations = fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            ignoredDirs.forEach { exclude("**/$it/**") }
        }.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                when {
                    line.contains('\t') -> "${file.relativeTo(rootDir)}:${index + 1}: tab character"
                    line.endsWith(" ") -> "${file.relativeTo(rootDir)}:${index + 1}: trailing space"
                    else -> null
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(violations.joinToString(separator = "\n"))
        }
    }
}

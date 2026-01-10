import libdage.gradle.AbstractCmakeTask
import org.apache.tools.ant.taskdefs.condition.Os
import java.nio.file.Files

plugins {
    base
}

val buildSharedLibs = true
val opensslManifest: RegularFile = layout.projectDirectory.file("vcpkg-alts/boringssl")
val toolchainFile: RegularFile = layout.projectDirectory.file("vcpkg/scripts/buildsystems/vcpkg.cmake")
val cmakeBuildDir: Provider<Directory> = layout.buildDirectory.dir("cmake")

fun AbstractCmakeTask.applyCommonBuildOptions(architecture: String) {
    environment.put("VCPKG_BUILD_TYPE", "release")

    option("-B${buildDir.get().asFile.absolutePath}")
    option("-DVCPKG_MANIFEST_DIR=${opensslManifest.asFile.absolutePath}")
    option("-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.asFile.absolutePath}")
    option("-DBUILD_SHARED_LIBS=ON")
    option("-DREQUIRE_BORINGSSL=ON")
    option("-DCMAKE_BUILD_TYPE=Release")

    option("-DVCPKG_TARGET_ARCHITECTURES=$architecture")
}

val cmakeClean by tasks.registering(Delete::class) {
    delete(cmakeBuildDir)
}

val cmakePrepareShared by tasks.registering(AbstractCmakeTask::class) {
    inputs.file("CMakeLists.txt")
    buildDir = cmakeBuildDir.get().dir("shared")

    applyCommonBuildOptions("x86_64")

    option("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${layout.buildDirectory.dir("libs").get()}")
    option("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${layout.buildDirectory.dir("libs").get()}")

    if (Os.isFamily(Os.FAMILY_WINDOWS)) {
        option("-DVCPKG_TARGET_TRIPLET=x64-windows-static-md")
    }
}

val cmakeBuildShared by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakePrepareShared)

    buildDir = cmakePrepareShared.get().buildDir

    option("--build", buildDir.get().asFile.absolutePath)
    option("--target", "libdave")
    option("--config", "Release")
}

val cmakePrepareDarwinX64 by tasks.registering(AbstractCmakeTask::class) {
    inputs.file("CMakeLists.txt")
    buildDir = cmakeBuildDir.get().dir("darwin-x64")

    applyCommonBuildOptions("x86_64")

    option("-DCMAKE_OSX_ARCHITECTURES=x86_64")
}
val cmakeBuildDarwinX64 by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakePrepareDarwinX64)

    buildDir = cmakePrepareDarwinX64.get().buildDir

    option("--build", buildDir.get().asFile.absolutePath)
    option("--target", "libdave")
    option("--config", "Release")
}

val cmakePrepareDarwinArm by tasks.registering(AbstractCmakeTask::class) {
    inputs.file("CMakeLists.txt")
    buildDir = cmakeBuildDir.get().dir("darwin-arm")

    applyCommonBuildOptions("arm64")

    option("-DCMAKE_OSX_ARCHITECTURES=arm64")
}

val cmakeBuildDarwinArm by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakePrepareDarwinArm)

    buildDir = cmakePrepareDarwinArm.get().buildDir

    option("--build", buildDir.get().asFile.absolutePath)
    option("--target", "libdave")
    option("--config", "Release")
}

val cmakeBuildDarwin by tasks.registering(Exec::class) {
    dependsOn(cmakeBuildDarwinX64, cmakeBuildDarwinArm)

    doFirst {
        Files.createDirectory(
            layout.buildDirectory.dir("libs").get().asFile.toPath()
        )
    }

    commandLine(
        "lipo", "-create",
        "-output", layout.buildDirectory.file("libs/libdave.dylib").get().asFile.absolutePath,
        cmakeBuildDarwinX64.get().buildDir.file("libdave.dylib").get().asFile.absolutePath,
        cmakeBuildDarwinArm.get().buildDir.file("libdave.dylib").get().asFile.absolutePath,
    )
}

tasks.clean {
    dependsOn(cmakeClean)
}

tasks.assemble {
    if (Os.isFamily(Os.FAMILY_MAC)) {
        dependsOn(cmakeBuildDarwin)
    } else {
        dependsOn(cmakeBuildShared)
    }
}

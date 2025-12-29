import libdage.gradle.AbstractCmakeTask
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    base
}

/*

From Makefile:

    BUILD_DIR=build
    TEST_DIR=build/test
    CLANG_FORMAT=clang-format -i -style=file:.clang-format
    BORINGSSL_MANIFEST=vcpkg-alts/boringssl
    OPENSSL_1_1_MANIFEST=vcpkg-alts/openssl_1.1
    OPENSSL_3_MANIFEST=vcpkg-alts/openssl_3
    WASM_MANIFEST=vcpkg-alts/wasm
    TOOLCHAIN_FILE=vcpkg/scripts/buildsystems/vcpkg.cmake
    EMSCRIPTEN_TOOLCHAIN_FILE=${EMSDK}/upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake
*/

val buildSharedLibs = true
val opensslManifest: RegularFile = layout.projectDirectory.file("vcpkg-alts/openssl_3")
val toolchainFile: RegularFile = layout.projectDirectory.file("vcpkg/scripts/buildsystems/vcpkg.cmake")
val cmakeBuildDir: Provider<Directory> = layout.buildDirectory.dir("cmake")

val cmakeClean by tasks.registering(Delete::class) {
    delete(cmakeBuildDir)
}

val cmakePrepareShared by tasks.registering(AbstractCmakeTask::class) {
    inputs.file("CMakeLists.txt")
    inputs.file("test/CMakeLists.txt")
    outputDir = cmakeBuildDir.get().dir("shared")

    option("-B${outputDir.get().asFile.absolutePath}")
    option("-DVCPKG_MANIFEST_DIR=${opensslManifest.asFile.absolutePath}")
    option("-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.asFile.absolutePath}")
    option("-DBUILD_SHARED_LIBS=ON")

    if (!Os.isFamily(Os.FAMILY_WINDOWS)) {
        option("-DCMAKE_INSTALL_PREFIX=${System.getProperty("user.home")}/.local")
    }
}

val cmakeBuildShared by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakePrepareShared)

    outputDir = cmakePrepareShared.get().outputDir

    option("--build", outputDir.get().asFile.absolutePath)
    option("--target", "libdave")
}

val cmakeInstallShared by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakeBuildShared)

    outputDir = cmakePrepareShared.get().outputDir
    outputs.upToDateWhen { false }

    option("--install", outputDir.get().asFile.absolutePath)

    onlyIf { !Os.isFamily(Os.FAMILY_WINDOWS) }
}

val cmakeAssemble by tasks.registering(Copy::class) {
    dependsOn(cmakeBuildShared)

    from(cmakeBuildShared.get().outputDir) {
        include {
            it.name.endsWith("dave.so") || it.name.endsWith("dave.dll") || it.name.endsWith("dave.dylib")
        }
    }

    into(layout.buildDirectory.dir("libs"))
}

tasks.clean {
    dependsOn(cmakeClean)
}

tasks.build {
    dependsOn(cmakeAssemble)
}

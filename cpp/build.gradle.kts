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
val opensslManifest: RegularFile = layout.projectDirectory.file("vcpkg-alts/boringssl")
val toolchainFile: RegularFile = layout.projectDirectory.file("vcpkg/scripts/buildsystems/vcpkg.cmake")
val cmakeBuildDir: Provider<Directory> = layout.buildDirectory.dir("cmake")

val cmakeClean by tasks.registering(Delete::class) {
    delete(cmakeBuildDir)
}

val cmakePrepareShared by tasks.registering(AbstractCmakeTask::class) {
    environment.put("VCPKG_BUILD_TYPE", "release")

    inputs.file("CMakeLists.txt")
    buildDir = cmakeBuildDir.get().dir("shared")

    option("-B${buildDir.get().asFile.absolutePath}")
    option("-DVCPKG_MANIFEST_DIR=${opensslManifest.asFile.absolutePath}")
    option("-DCMAKE_TOOLCHAIN_FILE=${toolchainFile.asFile.absolutePath}")
    option("-DBUILD_SHARED_LIBS=ON")
    option("-DREQUIRE_BORINGSSL=ON")
    option("-DCMAKE_BUILD_TYPE=Release")
    option("-DCMAKE_LIBRARY_OUTPUT_DIRECTORY_RELEASE=${layout.buildDirectory.dir("libs").get()}")
    option("-DCMAKE_RUNTIME_OUTPUT_DIRECTORY_RELEASE=${layout.buildDirectory.dir("libs").get()}")

    when {
        Os.isFamily(Os.FAMILY_WINDOWS) -> option("-DVCPKG_TARGET_TRIPLET=x64-windows-static-md")
        Os.isFamily(Os.FAMILY_MAC) -> option("-DCMAKE_OSX_ARCHITECTURES=arm64;x86_64")
    }
}

val cmakeBuildShared by tasks.registering(AbstractCmakeTask::class) {
    dependsOn(cmakePrepareShared)

    buildDir = cmakePrepareShared.get().buildDir

    option("--build", buildDir.get().asFile.absolutePath)
    option("--target", "libdave")
    option("--config", "Release")
}

tasks.clean {
    dependsOn(cmakeClean)
}

tasks.assemble {
    dependsOn(cmakeBuildShared)
}

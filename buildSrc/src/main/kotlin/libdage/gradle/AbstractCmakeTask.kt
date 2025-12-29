package libdage.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.*
import org.gradle.process.internal.ExecActionFactory
import javax.inject.Inject

abstract class AbstractCmakeTask : DefaultTask() {
    @get:Input
    abstract val options: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val environment: MapProperty<String, String>

    @get:Inject
    protected abstract val execActionFactory: ExecActionFactory

    fun option(option: String, value: String? = null) {
        options.add(option)
        if (value != null) {
            options.add(value)
        }
    }

    @TaskAction
    protected fun execute() {
        val action = execActionFactory.newExecAction()
        if (environment.isPresent) {
            action.environment(environment.get())
        }

        action.commandLine(listOf("cmake") + options.get())
        action.execute()
    }
}
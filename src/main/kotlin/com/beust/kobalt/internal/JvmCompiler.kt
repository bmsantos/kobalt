package com.beust.kobalt.internal

import com.beust.kobalt.api.KobaltContext
import com.beust.kobalt.api.Project
import com.beust.kobalt.maven.DependencyManager
import com.beust.kobalt.maven.IClasspathDependency
import com.beust.kobalt.maven.KobaltException
import com.google.inject.Inject
import java.io.File

/**
 * Abstract the compilation process by running an ICompilerAction parameterized  by a CompilerActionInfo.
 * Also validates the classpath and run all the contributors.
 */
class JvmCompiler @Inject constructor(val dependencyManager: DependencyManager) {
    fun doCompile(project: Project?, context: KobaltContext?, action: ICompilerAction, info: CompilerActionInfo)
            : TaskResult {
        File(info.outputDir).mkdirs()

        val allDependencies = arrayListOf<IClasspathDependency>()
        allDependencies.addAll(info.dependencies)
        allDependencies.addAll(calculateDependencies(project, context, info.dependencies))
        validateClasspath(allDependencies.map { it.jarFile.get().absolutePath })
        return action.compile(info.copy(dependencies = allDependencies))
    }

    private fun validateClasspath(cp: List<String>) {
        cp.forEach {
            if (! File(it).exists()) {
                throw KobaltException("Couldn't find $it")
            }
        }
    }

    /**
     * @return the classpath for this project, including the IClasspathContributors.
     */
    fun calculateDependencies(project: Project?, context: KobaltContext?,
            vararg allDependencies: List<IClasspathDependency>): List<IClasspathDependency> {
        var result = arrayListOf<IClasspathDependency>()
        allDependencies.forEach { dependencies ->
            result.addAll(dependencyManager.transitiveClosure(dependencies))
        }
        if (project != null) {
            result.addAll(runClasspathContributors(context, project))
        }

        return result
    }

    private fun runClasspathContributors(context: KobaltContext?, project: Project) :
            Collection<IClasspathDependency> {
        val result = arrayListOf<IClasspathDependency>()
        context?.classpathContributors?.forEach {
            result.addAll(it.entriesFor(project))
        }
        return result
    }

}

data class CompilerActionInfo(val dependencies: List<IClasspathDependency>,
        val sourceFiles: List<String>, val outputDir: String, val compilerArgs: List<String>)

interface ICompilerAction {
    fun compile(info: CompilerActionInfo): TaskResult
}
/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir

import com.intellij.util.io.delete
import org.jetbrains.kotlin.cli.common.*
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.fir.TableTimeUnit.MS
import org.jetbrains.kotlin.fir.TableTimeUnit.S
import org.jetbrains.kotlin.fir.scopes.ProcessorAction
import org.jetbrains.kotlin.util.PerformanceCounter
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Files

class FullPipelineModularizedTest : AbstractModularizedTest() {
    override fun beforePass() {
    }

    override fun afterPass(pass: Int) {
    }

    private fun createReport(manager: CompilerPerformanceManager) {
        manager.formatReport(System.out)

        PrintStream(
            FileOutputStream(
                reportDir().resolve("report-$reportDateStr.log"),
                true
            )
        ).use { stream ->
            manager.formatReport(stream)
            stream.println()
            stream.println()
        }
    }

    override fun processModule(moduleData: ModuleData): ProcessorAction {
        val compiler = K2JVMCompiler()
        val args = compiler.createArguments()
        args.reportPerf = true
        args.jvmTarget = "1.8"
        args.useFir = true
        args.classpath = moduleData.classpath.joinToString(separator = ":") { it.absolutePath }
        args.javaSourceRoots = moduleData.javaSourceRoots.map { it.absolutePath }.toTypedArray()
        args.freeArgs = moduleData.sources.map { it.absolutePath }
        val tmp = Files.createTempDirectory("compile-output")
        args.destination = tmp.toAbsolutePath().toFile().toString()
        val manager = CompilerPerformanceManager()
        val services = Services.Builder().register(CommonCompilerPerformanceManager::class.java, manager).build()
        val result = compiler.exec(PrintingMessageCollector(System.out, MessageRenderer.GRADLE_STYLE, false), services, args)
        createReport(manager)
        PerformanceCounter.resetAllCounters()

        tmp.delete(recursively = true)

        return when (result) {
            ExitCode.OK -> ProcessorAction.NEXT
            else -> ProcessorAction.STOP
        }
    }


    private inner class CompilerPerformanceManager : CommonCompilerPerformanceManager("Modularized test performance manager") {
        fun formatReport(stream: PrintStream) {
            for (measurement in measurements) {
                stream.println(measurement.render())
            }

            var totalTimeMs = 0L
            var lines = 0
            var files = 0
            var totalGcTimeMs = 0L
            var totalGcCount = 0L
            printTable(stream) {
                row("Name", "Time", "Count")
                separator()
                fun gcRow(name: String, timeMs: Long, count: Long) {
                    row {
                        cell(name, align = LEFT)
                        timeCell(timeMs, inputUnit = MS)
                        cell(count.toString())
                    }
                }
                for (measurement in measurements) {
                    if (measurement !is GarbageCollectionMeasurement) continue
                    totalGcTimeMs += measurement.milliseconds
                    totalGcCount += measurement.count
                    gcRow(measurement.garbageCollectionKind, measurement.milliseconds, measurement.count)
                }
                separator()
                gcRow("Total", totalGcTimeMs, totalGcCount)

            }

            printTable(stream) {
                row("Phase", "Time", "Files", "L/S")
                separator()

                fun phase(name: String, timeMs: Long, files: Int, lps: Double) {
                    row {
                        cell(name, align = LEFT)
                        timeCell(timeMs, inputUnit = MS)
                        cell(files.toString())
                        linePerSecondCell(lps)
                    }
                }
                for (measurement in measurements) {
                    when (measurement) {
                        is CodeAnalysisMeasurement -> {
                            lines = measurement.lines
                            files = measurement.files
                            totalTimeMs += measurement.milliseconds
                            phase("Analysis", measurement.milliseconds, measurement.files, measurement.lps)
                        }
                        is IRMeasurement -> {
                            totalTimeMs += measurement.milliseconds
                            phase("Analysis", measurement.milliseconds, measurement.files, measurement.lps)
                        }
                        is GarbageCollectionMeasurement -> {
                            totalGcTimeMs += measurement.milliseconds
                            totalGcCount += measurement.count
                        }
                        else -> continue
                    }
                }
                separator()
                phase("Total", totalTimeMs, files, lines / (totalTimeMs from MS to S))
            }
        }
    }

    fun testTotalKotlin() {
        for (i in 0 until PASSES) {
            println("Pass $i")
            runTestOnce(i)
        }
    }
}
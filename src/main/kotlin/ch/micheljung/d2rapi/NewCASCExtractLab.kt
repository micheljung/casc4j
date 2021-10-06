package ch.micheljung.d2rapi

import ch.micheljung.d2rapi.blizzard.casc.ConfigurationFile.Companion.lookupConfigurationFile
import ch.micheljung.d2rapi.blizzard.casc.info.Info
import ch.micheljung.d2rapi.blizzard.casc.storage.Storage
import ch.micheljung.d2rapi.blizzard.casc.vfs.VirtualFileSystem
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

fun main() {
    val dataFolder = Paths.get("C:/Program Files (x86)/Diablo II Resurrected/Data")
    val extractFolder = Paths.get("build/d2r")

    println("Opening info")
    val infoFile = dataFolder.parent.resolve(Info.BUILD_INFO_FILE_NAME)
    val info = Info(ByteBuffer.wrap(Files.readAllBytes(infoFile)))

    println("Extracting build configuration key")
    check(info.recordCount >= 1) { "Info contains no records" }
    val fieldIndex = info.getFieldIndex("Build Key")
    check(fieldIndex != -1) { "Info missing field" }
    val buildKey = info.getField(0, fieldIndex)

    println("Opening configuration")
    val buildConfiguration = lookupConfigurationFile(dataFolder, buildKey)

    println("Opening store")
    Storage(dataFolder, useOld = false, useMemoryMapping = true).let { storage ->
        println("Mounting VFS")
        val vfs = VirtualFileSystem(storage, buildConfiguration.configuration)

        println("Getting all paths")
        val allFilePaths = vfs.allFiles
        val startTime = System.nanoTime()
        val totalExtracted = AtomicLong(0L)
        val jobList: MutableCollection<Callable<Void>> = ArrayList()

        allFilePaths.filter { it.path.startsWith("data\\data\\global\\excel\\") && it.path.endsWith(".txt") }
            .forEach {
                val filePath = it.path
                val outputPath = extractFolder.resolve(filePath)
                val fileSize = it.fileSize
                val exists = it.existsInStorage()
                if (exists && !it.isTVFS) {
                    val job: Callable<Void> = Callable {
                        Files.createDirectories(outputPath.parent)
                        FileChannel.open(
                            outputPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE,
                            StandardOpenOption.READ,
                            StandardOpenOption.TRUNCATE_EXISTING
                        ).use { fileChannel ->
                            val fileBuffer = fileChannel.map(MapMode.READ_WRITE, 0L, fileSize)
                            it.readFile(fileBuffer)
                            totalExtracted.addAndGet(fileSize)
                        }
                        val resolveResult = vfs.resolvePath(it.pathFragments)
                        totalExtracted.addAndGet(fileSize)
                        null
                    }
                    jobList.add(job)
                }
                println(filePath + " : " + fileSize + " : " + if (exists) "yes" else "no")
            }

        println("Extracting files")
        val executor = Executors.newFixedThreadPool(8)
        try {
            val jobFutures = executor.invokeAll(jobList)
            for (jobFuture in jobFutures) {
                jobFuture.get()
            }
        } finally {
            executor.shutdownNow()
        }

        println("Shutting down thread pool")
        executor.awaitTermination(30, TimeUnit.SECONDS)
        val endTime = System.nanoTime()

        println("Total path string count: " + allFilePaths.size)
        val runtime = (endTime - startTime) / 1000000000.0

        println("Running time to process all files: " + runtime.toLong() + "s")
        println("Average process speed: " + (totalExtracted.get() / runtime).toLong() + "B/sec")
        println("Success")
    }
    println("End")
}
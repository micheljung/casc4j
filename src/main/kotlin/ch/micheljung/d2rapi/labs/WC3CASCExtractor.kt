package ch.micheljung.d2rapi.labs

import ch.micheljung.d2rapi.blizzard.casc.io.WarcraftIIICASC
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * A simple application for extracting a single file from the Warcraft III CASC
 * archive.
 *
 *
 * First argument is a file path to the Warcraft III install folder. Second
 * argument is then a flag.
 *
 *
 * -l to list all files.
 *
 *
 * -e to extract a specific file. With -e the a source file to extract and a
 * destination folder must be specified.
 *
 *
 * -b or -branch to list the active branch.
 *
 *
 * -bi or -buildinfo to retrieve a field from the active record of the build
 * information file which was mounted.
 */
object WC3CASCExtractor {
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size < 1) {
            println("No install path specified.")
            return
        }
        val installPathString = args[0]
        val installPath = Paths.get(installPathString)
        if (!Files.isDirectory(installPath)) {
            println("Install path is not a folder.")
            return
        }
        println("Mounting.")
        try {
            WarcraftIIICASC(installPath, true).let { dataSource ->
                val root = dataSource.rootFileSystem
                if (args.size < 2) {
                    println("No operation specified.")
                    return
                }
                val operationString = args[1]
                when (operationString) {
                    "-l" -> {
                        println("Enumerating files.")
                        val filePaths = root.enumerateFiles()
                        for (filePath in filePaths) {
                            println(filePath)
                        }
                    }
                    "-e" -> {
                        if (args.size < 4) {
                            println("Not enough operands.")
                            return
                        }
                        val sourceFilePathString = args[2]
                        val destinationFolderPathString = args[3]
                        if (!root.isFile(sourceFilePathString)) {
                            println("Specified file path does not exist.")
                            return
                        } else if (!root.isFileAvailable(sourceFilePathString)) {
                            println("Specified file is not available.")
                            return
                        }
                        val destinationFilePath = Paths.get(destinationFolderPathString, sourceFilePathString)
                        val destinationFolderPath = destinationFilePath.parent
                        Files.createDirectories(destinationFolderPath)
                        FileChannel.open(
                            destinationFilePath, StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING
                        ).use { destinationChannel ->
                            println("Reading file data.")
                            val fileBuffer = dataSource.rootFileSystem.readFileData(sourceFilePathString)
                            println("Writing file data.")
                            while (fileBuffer.hasRemaining()) {
                                destinationChannel.write(fileBuffer)
                            }
                        }
                    }
                    "-b", "-branch" -> println("Active branch: " + dataSource.branch)
                    "-bi", "-buildinfo" -> {
                        if (args.size < 3) {
                            println("Not enough operands.")
                            return
                        }
                        val fieldName = args[2]
                        val value = dataSource.buildInfo.getField(dataSource.activeRecordIndex, fieldName)
                        println("Field name: $fieldName")
                        println("Value: $value")
                    }
                    else -> println("Unknown operation.")
                }
                println("Done.")
            }
        } catch (e: IOException) {
            println("An exception occured.")
            e.printStackTrace(System.out)
        } finally {
            println("Unmounted.")
        }
    }
}
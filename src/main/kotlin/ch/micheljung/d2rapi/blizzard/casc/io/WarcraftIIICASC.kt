package ch.micheljung.d2rapi.blizzard.casc.io

import ch.micheljung.d2rapi.blizzard.casc.ConfigurationFile
import ch.micheljung.d2rapi.blizzard.casc.info.Info
import ch.micheljung.d2rapi.blizzard.casc.nio.MalformedCASCStructureException
import ch.micheljung.d2rapi.blizzard.casc.storage.Storage
import ch.micheljung.d2rapi.blizzard.casc.vfs.VirtualFileSystem
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

/**
 * A convenient access to locally stored Warcraft III data files. Intended for use with CASC versions of Warcraft III
 * including classic and Reforged.
 */
class WarcraftIIICASC(installFolder: Path, useMemoryMapping: Boolean) : AutoCloseable {

    /** File system view for accessing files from file paths. */
    inner class FileSystem {
        /**
         * Enumerate all file paths contained in this file system.
         *
         * This operation might be quite slow.
         *
         * @return A list containing all file paths contained in this file system.
         * @throws IOException In an exception occurs when resolving files.
         */
        fun enumerateFiles(): List<String> {
            val pathResults = vfs!!.allFiles
            val filePathStrings = ArrayList<String>(pathResults.size)
            for (pathResult in pathResults) {
                filePathStrings.add(pathResult.path)
            }
            return filePathStrings
        }

        /**
         * Test if the specified file path is a file.
         *
         * @param filePath Path of file to test.
         * @return True if path represents a file, otherwise false.
         * @throws IOException In an exception occurs when resolving files.
         */
        fun isFile(filePath: String): Boolean {
            val pathFragments = VirtualFileSystem.convertFilePath(filePath)
            return try {
                val resolveResult = vfs.resolvePath(pathFragments)
                resolveResult.isFile
            } catch (e: FileNotFoundException) {
                false
            }
        }

        /**
         * Test if the specified file path is available from local storage.
         *
         * @param filePath Path of file to test.
         * @return True if path represents a file inside local storage, otherwise false.
         * @throws IOException In an exception occurs when resolving files.
         */
        fun isFileAvailable(filePath: String): Boolean {
            val pathFragments = VirtualFileSystem.convertFilePath(filePath)
            val resolveResult = vfs!!.resolvePath(pathFragments)
            return resolveResult.existsInStorage()
        }

        /**
         * Test if the specified file path is a nested file system.
         *
         *
         * If true a file system can be resolved from the file path which files can be
         * resolved from more efficiently than from higher up file systems.
         *
         *
         * Support for this feature is not yet implemented. Please resolve everything
         * from the root.
         *
         * @param filePath Path of file to test.
         * @return True if file is a nested file system, otherwise false.
         * @throws IOException In an exception occurs when resolving files.
         */
        fun isNestedFileSystem(filePath: String): Boolean {
            val pathFragments = VirtualFileSystem.convertFilePath(filePath)
            return try {
                val resolveResult = vfs!!.resolvePath(pathFragments)
                resolveResult.isTVFS
            } catch (e: FileNotFoundException) {
                false
            }
        }

        /**
         * Fully read the file at the specified file path into memory.
         *
         * @param filePath File path of file to read.
         * @return Buffer containing file data.
         * @throws IOException If an error occurs when reading the file.
         */
        fun readFileData(filePath: String): ByteBuffer {
            val pathFragments = VirtualFileSystem.convertFilePath(filePath)
            val resolveResult = vfs.resolvePath(pathFragments)
            if (!resolveResult.isFile) {
                throw FileNotFoundException("the specified file path does not resolve to a file")
            } else if (!resolveResult.existsInStorage()) {
                throw FileNotFoundException("the specified file is not in local storage")
            }
            val fileBuffer = resolveResult.readFile()
            fileBuffer.flip()
            return fileBuffer
        }
    }

    /** Warcraft III build information.  */
    val buildInfo: Info
    /**
     * Returns the active record index of the build information. This is the index
     * of the record that is mounted.
     *
     * @return Active record index of build information.
     */
    /** Detected active build information record.  */
    val activeRecordIndex: Int

    /** Warcraft III build configuration.  */
    private val buildConfiguration: ConfigurationFile

    /** Warcraft III CASC data folder path.  */
    private val dataPath: Path

    /** Warcraft III local storage.  */
    private val localStorage: Storage

    /** TVFS file system to resolve file paths.  */
    private val vfs: VirtualFileSystem

    override fun close() {
        localStorage.close()
    }

    /**
     * Returns the active branch name which is currently mounted.
     *
     *
     * This might reflect the locale that has been cached to local storage.
     *
     * @return Branch name.
     * @throws IOException If no branch information is available.
     */
    val branch: String
        get() {
            // resolve branch
            val branchFieldIndex = buildInfo.getFieldIndex("Branch")
            if (branchFieldIndex == -1) {
                throw MalformedCASCStructureException("build info contains no branch field")
            }
            return buildInfo.getField(activeRecordIndex, branchFieldIndex)
        }

    /**
     * Get the root file system of Warcraft III. From this all locally stored data
     * files can be accessed.
     *
     * @return Root file system containing all files.
     */
    val rootFileSystem: FileSystem
        get() = FileSystem()

    companion object {
        /** Name of the CASC data folder used by Warcraft III.  */
        private const val WC3_DATA_FOLDER_NAME = "Data"
    }

    /**
     * Construct an interface to the CASC local storage used by Warcraft III. Can be
     * used to read data files from the local storage.
     *
     *
     * The active build record is used for local storage details.
     *
     *
     * Install folder is the Warcraft III installation folder where the
     * `.build.info` file is located. For example
     * `C:\Program Files (x86)\Warcraft III`.
     *
     *
     * Memory mapped IO can be used instead of conventional channel based IO. This
     * should improve IO performance considerably by avoiding excessive memory copy
     * operations and system calls. However it may place considerable strain on the
     * Java VM application virtual memory address space. As such memory mapping
     * should only be used with large address aware VMs.
     *
     * @param installFolder    Warcraft III installation folder.
     * @param useMemoryMapping If memory mapped IO should be used to read file data.
     * @throws IOException If an exception occurs while mounting.
     */
    init {
        val infoFilePath = installFolder.resolve(Info.BUILD_INFO_FILE_NAME)
        buildInfo = Info(ByteBuffer.wrap(Files.readAllBytes(infoFilePath)))
        val recordCount = buildInfo.recordCount
        if (recordCount < 1) {
            throw MalformedCASCStructureException("build info contains no records")
        }

        // resolve the active record
        val activeFiledIndex = buildInfo.getFieldIndex("Active")
        if (activeFiledIndex == -1) {
            throw MalformedCASCStructureException("build info contains no active field")
        }
        var recordIndex = 0
        while (recordIndex < recordCount) {
            if (buildInfo.getField(recordIndex, activeFiledIndex).toInt() == 1) {
                break
            }
            recordIndex += 1
        }
        if (recordIndex == recordCount) {
            throw MalformedCASCStructureException("build info contains no active record")
        }
        activeRecordIndex = recordIndex

        // resolve build configuration file
        val buildKeyFieldIndex = buildInfo.getFieldIndex("Build Key")
        if (buildKeyFieldIndex == -1) {
            throw MalformedCASCStructureException("build info contains no build key field")
        }
        val buildKey = buildInfo.getField(activeRecordIndex, buildKeyFieldIndex)

        // resolve data folder
        dataPath = installFolder.resolve(WC3_DATA_FOLDER_NAME)
        if (!Files.isDirectory(dataPath)) {
            throw MalformedCASCStructureException("data folder is missing")
        }

        // resolve build configuration file
        buildConfiguration = ConfigurationFile.lookupConfigurationFile(dataPath, buildKey)

        // mounting local storage
        localStorage = Storage(dataPath, false, useMemoryMapping)

        vfs = try {
            VirtualFileSystem(localStorage, buildConfiguration.configuration)
        } catch (t: Throwable) {
            localStorage.close()
            throw t
        }
    }
}
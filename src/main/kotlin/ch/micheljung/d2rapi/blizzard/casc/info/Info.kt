package ch.micheljung.d2rapi.blizzard.casc.info

import ch.micheljung.d2rapi.blizzard.casc.nio.MalformedCASCStructureException
import ch.micheljung.d2rapi.nio.ByteBufferInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Top level CASC information file containing configuration information and
 * entry point references.
 */
class Info(encodedLines: ByteBuffer) {
    private val fieldDescriptors = mutableListOf<FieldDescriptor>()
    private val records = mutableListOf<ArrayList<String>>()

    /**
     * Retrieves a specific field of a record.
     *
     * @param recordIndex Record index to lookup.
     * @param fieldIndex  Field index to retrieve of record.
     * @return Field value.
     * @throws IndexOutOfBoundsException When recordIndex or fieldIndex are out of
     * bounds.
     */
    fun getField(recordIndex: Int, fieldIndex: Int): String {
        return records[recordIndex][fieldIndex]
    }

    /**
     * Retrieves a specific field of a record.
     *
     * @param recordIndex Record index to lookup.
     * @param fieldName   Field name to retrieve of record.
     * @return Field value, or null if field does not exist.
     * @throws IndexOutOfBoundsException When recordIndex is out of bounds.
     */
    fun getField(recordIndex: Int, fieldName: String): String? {
        // resolve field
        val fieldIndex = getFieldIndex(fieldName)
        return if (fieldIndex == -1) {
            // field does not exist
            null
        } else getField(recordIndex, fieldIndex)
    }

    /** The number of fields that make up each record.  */
    val fieldCount: Int
        get() = fieldDescriptors.size

    /**
     * Retrieve the field descriptor of a field index.
     *
     * @param fieldIndex Field index to retrieve descriptor from.
     * @return Field descriptor for field index.
     */
    fun getFieldDescriptor(fieldIndex: Int): FieldDescriptor {
        return fieldDescriptors[fieldIndex]
    }

    /**
     * Lookup the index of a named field. Returns the field index for the field name
     * if found, otherwise returns -1.
     *
     * @param name Name of the field to find.
     * @return Field index of field.
     */
    fun getFieldIndex(name: String): Int {
        var i = 0
        while (i < fieldDescriptors.size) {
            if (fieldDescriptors[i].name == name) {
                return i
            }
            i += 1
        }
        return -1
    }

    val recordCount: Int
        get() = records.size

    companion object {
        /** Name of the CASC build info file located in the install root (parent of the data folder).  */
        const val BUILD_INFO_FILE_NAME = ".build.info"

        /** Character encoding used by info files.  */
        val FILE_ENCODING = StandardCharsets.UTF_8

        /** Field separator used by CASC info files.  */
        private const val FIELD_SEPARATOR_REGEX = "|"

        /** Helper method to separate a single line of info file into separate field strings.  */
        private fun separateFields(encodedLine: String): Array<String> {
            return encodedLine.split(FIELD_SEPARATOR_REGEX).toTypedArray()
        }
    }

    init {
        try {
            ByteBufferInputStream(encodedLines).use { fileStream ->
                Scanner(fileStream, FILE_ENCODING).use { lineScanner ->
                    val encodedFieldDescriptors = separateFields(lineScanner.nextLine())
                    for (encodedFieldDescriptor in encodedFieldDescriptors) {
                        fieldDescriptors.add(FieldDescriptor(encodedFieldDescriptor))
                    }
                    while (lineScanner.hasNextLine()) {
                        records.add(ArrayList(listOf(*separateFields(lineScanner.nextLine()))))
                    }
                }
            }
        } catch (e: NoSuchElementException) {
            throw MalformedCASCStructureException("missing headers")
        }
    }
}
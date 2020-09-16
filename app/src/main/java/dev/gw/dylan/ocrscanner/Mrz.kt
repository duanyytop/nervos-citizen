package dev.gw.dylan.ocrscanner

/**
 * https://github.com/digital-voting-pass/polling-station-app/blob/master/app/src/main/java/com/digitalvotingpass/ocrscanner/Mrz.java
 */

import dev.gw.dylan.passport.DocumentData

class Mrz(private var text: String) {
    /**
     * Does some basic cleaning on the MRZ string of this object
     * Done before checksum verification so may throw errors, these are ignored
     */
    private fun cleanMRZString() {
        try {
            val spl = text.replace(" ", "").replace("\n\n", "\n").split("\n".toRegex())
                .toTypedArray() // Delete any space characters and replace double newline with a single newline
            text = """
                ${spl[0]}
                ${spl[1]}
                """.trimIndent() // Extract only first 2 lines, sometimes random errorous data is detected beyond.
        } catch (ignored: Exception) {
        }
    }

    /**
     * Returns relevant data from the MRZ in a DocumentData object.
     * @return DocumentData object
     */
    val prettyData: DocumentData
        get() {
            val data = DocumentData()
            if (text.startsWith("P")) {
                data.documentNumber = text.split("\n".toRegex()).toTypedArray()[1].substring(
                    PASSPORT_DOCNO_INDICES[0],
                    PASSPORT_DOCNO_INDICES[1]
                )
                data.dateOfBirth = text.split("\n".toRegex()).toTypedArray()[1].substring(
                    PASSPORT_DOB_INDICES[0],
                    PASSPORT_DOB_INDICES[1]
                )
                data.expiryDate = text.split("\n".toRegex()).toTypedArray()[1].substring(
                    PASSPORT_EXP_INDICES[0],
                    PASSPORT_EXP_INDICES[1]
                )
            } else if (text.startsWith("I")) {
                data.documentNumber = text.split("\n".toRegex()).toTypedArray()[0]
                    .substring(ID_DOCNO_INDICES[0], ID_DOCNO_INDICES[1])
                data.dateOfBirth = text.split("\n".toRegex()).toTypedArray()[1]
                    .substring(ID_DOB_INDICES[0], ID_DOB_INDICES[1])
                data.expiryDate = text.split("\n".toRegex()).toTypedArray()[1]
                    .substring(ID_EXP_INDICES[0], ID_EXP_INDICES[1])
            }
            return data
        }

    /**
     * Checks if this MRZ data is valid
     * @return boolean whether the given input is a correct MRZ.
     */
    fun valid(): Boolean {
        try {
            if (text.startsWith("P")) {
                return checkPassportMRZ(text)
            } else if (text.startsWith("I")) {
                return checkIDMRZ(text)
            }
        } catch (ignored: Exception) {
            // Probably an outOfBounds indicating the format was incorrect
        }
        return false
    }

    private fun checkIDMRZ(mrz: String): Boolean {
        val firstCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[0], arrayOf(
                ID_DOCNO_INDICES
            ), 14
        ) //Checks document number
        val secondCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                ID_DOB_INDICES
            ), 6
        ) //Checks DoB
        val thirdCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                ID_EXP_INDICES
            ), 14
        ) //Checks expiration date
        val fourthCheck = checkSum(
            mrz.replace("\n", ""),
            arrayOf(intArrayOf(5, 30), intArrayOf(30, 37), intArrayOf(38, 45), intArrayOf(49, 59)),
            59
        ) //Checks upper line from 6th digit + middle line
        return firstCheck && secondCheck && thirdCheck && fourthCheck
    }

    private fun checkPassportMRZ(mrz: String): Boolean {
        val firstCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                PASSPORT_DOCNO_INDICES
            ), 9
        ) // Checks document number
        val secondCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                PASSPORT_DOB_INDICES
            ), 19
        )
        val thirdCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                PASSPORT_EXP_INDICES
            ), 27
        )
        val fourthCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1], arrayOf(
                PASSPORT_PERSONAL_NUMBER_INDICES
            ), 42
        )
        val fifthCheck = checkSum(
            mrz.split("\n".toRegex()).toTypedArray()[1],
            arrayOf(intArrayOf(0, 10), intArrayOf(13, 20), intArrayOf(21, 43)),
            43
        )
        return firstCheck && secondCheck && thirdCheck && fourthCheck && fifthCheck
    }

    companion object {
        private val PASSPORT_DOCNO_INDICES = intArrayOf(0, 9)
        private val PASSPORT_DOB_INDICES = intArrayOf(13, 19)
        private val PASSPORT_EXP_INDICES = intArrayOf(21, 27)
        private val ID_DOCNO_INDICES = intArrayOf(5, 14)
        private val ID_DOB_INDICES = intArrayOf(0, 6)
        private val ID_EXP_INDICES = intArrayOf(8, 14)
        private val PASSPORT_PERSONAL_NUMBER_INDICES = intArrayOf(28, 42)

        /**
         * Performs checksum calculation of the given string's chars from start til end.
         * Uses value at index `checkIndex` in `string` as check value.
         * @param string String to be checked
         * @param ranges indices of substrings to be checked
         * @param checkIndex index of char to check against
         * @return boolean whether check was successful
         */
        private fun checkSum(string: String, ranges: Array<IntArray>, checkIndex: Int): Boolean {
            val code = intArrayOf(7, 3, 1)
            val checkValue = Character.getNumericValue(string[checkIndex])
            var count = 0
            var checkSum = 0f
            for (range in ranges) {
                val line = string.substring(range[0], range[1]).toCharArray()
                for (c in line) {
                    val num: Int = if (c.toString().matches(Regex("[A-Z]"))) {
                        c.toInt() - 55
                    } else if (c.toString().matches(Regex("\\d"))) {
                        Character.getNumericValue(c)
                    } else if (c.toString().matches(Regex("<"))) {
                        0
                    } else {
                        return false //illegal character
                    }
                    checkSum += num * code[count % 3].toFloat()
                    count++
                }
            }
            val rem = checkSum.toInt() % 10
            return rem == checkValue
        }
    }

    init {
        cleanMRZString()
    }
}
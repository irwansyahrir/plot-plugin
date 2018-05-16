/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import au.com.bytecode.opencsv.CSVReader
import hudson.FilePath
import org.apache.commons.io.IOUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.logging.Logger

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
class KtCSVReaderTest : KtSeriesTestCase() {
    companion object {
        private val LOGGER = Logger.getLogger(KtCSVReaderTest::class.simpleName)

        private val FILES = arrayOf("test.csv")

        private val LINES = intArrayOf(2)

        private val COLUMNS = intArrayOf(8)
    }

    fun testCSVReader() {
        // first create a FilePath to load the test Properties file.
        val csvFolder = File("target/test-classes/")
        val csvFilePath = FilePath(csvFolder)

        LOGGER.info("workspace File path: " + csvFolder.absolutePath)
        LOGGER.info("workspace Dir path: " + csvFilePath.name)

        var csvreader: CSVReader? = null
        var `in`: InputStream? = null
        var inputReader: InputStreamReader? = null

        val seriesFiles: Array<FilePath>?
        try {
            seriesFiles = csvFilePath.list(FILES[0])

            if (seriesFiles != null && seriesFiles.size < 1) {
                LOGGER.info("No plot data file found: " + csvFilePath.name + " " + FILES[0])
                assertFalse(true)
            }

            LOGGER.info("Loading plot series data from: " + FILES[0])

            `in` = seriesFiles!![0].read()

            inputReader = InputStreamReader(`in`!!)
            csvreader = CSVReader(inputReader)

            // save the header line to use it for the plot labels.
            var nextLine: Array<String>
            // read each line of the CSV file and add to rawPlotData
            var lineNum = 0
            nextLine = csvreader.readNext()
            while (nextLine != null) {
                // for some reason csv reader returns an empty line sometimes.
                if (nextLine.size == 1 && nextLine[0].length == 0) {
                    break
                }

                if (COLUMNS[0] != nextLine.size) {
                    val msg = StringBuilder()
                    msg.append("column count is not equal ").append(nextLine.size)
                    msg.append(" expected ").append(COLUMNS[0]).append(" at line ")
                    msg.append(lineNum).append(" line: ").append("'")
                    for (s in nextLine) {
                        msg.append("\"").append(s).append("\":").append(s.length).append(",")
                    }
                    msg.append("' length ").append(nextLine.size)
                    assertTrue(msg.toString(), COLUMNS[0] == nextLine.size)
                }
                ++lineNum
                nextLine = csvreader.readNext()
            }
            assertTrue("Line count is not equal " + lineNum + " expected " + LINES[0], LINES[0] == lineNum)
        } catch (e: IOException) {
            assertFalse("Exception $e", true)
        } catch (e: InterruptedException) {
            assertFalse("Exception $e", true)
        } finally {
            try {
                if (csvreader != null) {
                    csvreader.close()
                }
            } catch (e: IOException) {
                assertFalse("Exception $e", true)
            }

            IOUtils.closeQuietly(inputReader)
            IOUtils.closeQuietly(`in`)
        }
    }
}

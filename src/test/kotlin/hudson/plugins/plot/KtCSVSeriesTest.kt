/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

import au.com.bytecode.opencsv.CSVReader
import hudson.FilePath
import junit.framework.TestCase
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.logging.Logger
import org.apache.commons.io.IOUtils

/**
 * Test a CSV series.
 *
 * @author Allen Reese
 */
class KtCSVSeriesTest : KtSeriesTestCase() {

    fun testCSVSeriesWithNullExclusionValuesSetsDisplayTableFlag() {
        val series = CSVSeries(FILES[0], null, null, null, true)
        TestCase.assertTrue(series.displayTableFlag)
    }

    fun testCSVSeriesWithNoExclusions() {
        val csvFolders = File("target/test-classes/")
        val csvFilePath = FilePath(csvFolders)

        LOGGER.info("workspace File path: " + csvFolders.absolutePath)
        LOGGER.info("workspace Dir path: " + csvFilePath.name)

        // Check the number of columns
        var columns = -1
        try {
            columns = getNumColumns(csvFilePath, FILES[0])
        } catch (e: IOException) {
            TestCase.assertFalse(true)
        } catch (e: InterruptedException) {
            TestCase.assertFalse(true)
        }


        val series = CSVSeries(FILES[0], "http://localhost:8080/%name%/%index%/", "OFF", "", false)

        LOGGER.info("Created series " + series.toString())
        testSeriesProperties(series, FILES[0], "", "csv")

        val points = series.loadSeries(csvFilePath, 0, System.out)
        LOGGER.info("Got " + points!!.size + " plot points")
        testPlotPoints(points, columns)

        for (i in points.indices) {
            val point = points[i]
            TestCase.assertEquals("http://localhost:8080/" + point.label + "/" + i + "/", point.url)
        }
    }

    fun testCSVSeriesWithTrailingSemicolonDoesntCreateExtraneousPoint() {
        val csvFolders = File("target/test-classes/")
        val csvFilePath = FilePath(csvFolders)
        val file = "test_trailing_semicolon.csv"

        LOGGER.info("workspace File path: " + csvFolders.absolutePath)
        LOGGER.info("workspace Dir path: " + csvFilePath.name)

        val series = CSVSeries(file,
                "http://localhost:8080/%name%/%index%/", "OFF", "", false)

        LOGGER.info("Created series " + series.toString())
        testSeriesProperties(series, file, "", "csv")

        val points = series.loadSeries(csvFilePath, 0, System.out)

        LOGGER.info("Got " + points!!.size + " plot points")
        testPlotPoints(points, 8)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun getNumColumns(workspaceRootDir: FilePath, file: String): Int {
        var csvreader: CSVReader? = null
        var `in`: InputStream? = null
        var inputReader: InputStreamReader? = null

        val seriesFiles: Array<FilePath>?
        try {
            seriesFiles = workspaceRootDir.list(file)

            if (seriesFiles != null && seriesFiles.size < 1) {
                LOGGER.info("No plot data file found: " + workspaceRootDir.name + " " + file)
                return -1
            }

            LOGGER.info("Loading plot series data from: $file")

            `in` = seriesFiles!![0].read()

            inputReader = InputStreamReader(`in`!!)
            csvreader = CSVReader(inputReader)

            // save the header line to use it for the plot labels.
            val headerLine = csvreader.readNext()

            LOGGER.info("Got " + headerLine.size + " columns")
            return headerLine.size
        } finally {
            try {
                if (csvreader != null) {
                    csvreader.close()
                }
            } catch (e: IOException) {
                TestCase.assertFalse("Exception $e", true)
            }

            IOUtils.closeQuietly(inputReader)
            IOUtils.closeQuietly(`in`)
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(KtCSVSeriesTest::class.simpleName)

        private val FILES = arrayOf("test.csv")
    }
}

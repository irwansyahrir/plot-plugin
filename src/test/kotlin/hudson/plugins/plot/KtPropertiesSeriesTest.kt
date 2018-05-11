/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import hudson.FilePath
import org.assertj.core.api.Assertions.assertThat
import java.io.File

/**
 * Test a Properties file series.
 *
 * @author Allen Reese
 */
class KtPropertiesSeriesTest : KtSeriesTestCase() {

    fun testPropertiesSeries() {
        val propertiesFileDir = File("target/test-classes/")
        val propertiesFilePath = FilePath(propertiesFileDir)

        println("workspace path path: " + propertiesFileDir.absolutePath)

        val propSeries = PropertiesSeries(FILES[0], LABELS[0])

        testSeriesProperties(propSeries, FILES[0], LABELS[0], "properties")

        val points = propSeries.loadSeries(propertiesFilePath, 0, System.err)

        testPlotPoints(points, 1)
    }

    fun testNonExistentPropertiesFile() {
        val propertiesFileDir = File("target/test-classes/")
        val propertiesFilePath = FilePath(propertiesFileDir)

        val propSeries = PropertiesSeries("whatever", "label")
        var points = propSeries.loadSeries(propertiesFilePath, 0, System.err)
        assertThat(points).isEmpty()
    }

    companion object {
        private val FILES = arrayOf("test.properties")
        private val LABELS = arrayOf("testLabel")
    }
}

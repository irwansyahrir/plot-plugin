/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import hudson.FilePath
import java.io.File

/**
 * Test a Properties file series.
 *
 * @author Allen Reese
 */
class KtPropertiesSeriesTest : KtSeriesTestCase() {

    fun testPropertiesSeries() {
        // first create a FilePath to load the test Properties file.
        val workspaceDirFile = File("target/test-classes/")
        val workspaceRootDir = FilePath(workspaceDirFile)

        println("workspace path path: " + workspaceDirFile.absolutePath)

        // Create a new properties series.
        val propSeries = PropertiesSeries(FILES[0], LABELS[0])

        // test the basic subclass properties.
        testSeries(propSeries, FILES[0], LABELS[0], "properties")

        // load the series.
        val points = propSeries.loadSeries(workspaceRootDir, 0, System.err)
        testPlotPoints(points, 1)
    }

    companion object {
        private val FILES = arrayOf("test.properties")
        private val LABELS = arrayOf("testLabel")
    }
}

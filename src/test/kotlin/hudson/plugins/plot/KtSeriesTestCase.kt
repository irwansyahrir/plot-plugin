package hudson.plugins.plot

import org.assertj.core.api.Assertions.assertThat
import org.jvnet.hudson.test.HudsonTestCase


/**
 * Stub to hold common series test functionality.
 *
 * @author Allen Reese
 */
open class KtSeriesTestCase : HudsonTestCase() {
    fun testDummy() {
        // Allow us to subclass, and not have the tests puke.
    }

    fun testSeriesProperties(series: Series, fileName: String, label: String, fileType: String) {
        assertThat(series).isNotNull

        assertThat(series.file).`as`("Check file name").isEqualTo(fileName)
        assertThat(series.label).`as`("Check label").isEqualTo(label)
        assertThat(series.fileType).`as`("Check file type").isEqualTo(fileType)
    }

    fun testPlotPoints(points: List<PlotPoint>?, expectedNumber: Int) {
        assertThat(expectedNumber).isGreaterThan(-1)

        assertThat(points).`as`("Check if loadSeries() returns any PlotPoints").isNotNull

        if (points!!.size != expectedNumber) {
            val debug = StringBuilder()
            for ((i, p) in points.withIndex()) {
                debug.append("[").append(i).append("]").append(p).append("\n")
            }

            assertEquals("loadSeries loaded wrong number of points: expected "
                    + expectedNumber + ", got " + points.size + "\n" + debug, expectedNumber, points.size)
        }

        for (i in points.indices) {
            assertThat(points[i]).`as`("Point at index $i").isNotNull
            assertThat(points[i].yvalue).`as`("Y Value at index $i").isNotNull()
            assertThat(points[i].url).`as`("Url at index $i").isNotNull()
            assertThat(points[i].label).`as`("Label at index $i").isNotNull()

            // make sure the yvalue's can be parsed
            try {
                java.lang.Double.parseDouble(points[i].yvalue)
            } catch (nfe: NumberFormatException) {
                assertTrue("loadSeries returned invalid yvalue "
                        + points[i].yvalue + " at index " + i
                        + " Exception " + nfe.toString(), false)
            }
        }
    }
}

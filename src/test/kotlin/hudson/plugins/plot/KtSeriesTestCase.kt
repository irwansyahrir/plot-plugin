package hudson.plugins.plot

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

    fun testSeries(series: Series, file: String, label: String, type: String) {
        // verify the properties was created correctly
        assertNotNull(series)

        assertEquals("File name is not configured correctly", file, series.file)
        assertEquals("Label is not configured correctly", label, series.label)
        assertEquals("Type is not configured correctly", type, series.fileType)
    }

    fun testPlotPoints(points: List<PlotPoint>?, expected: Int) {
        assertTrue("Must have more than 0 columns", expected > -1)

        assertNotNull("loadSeries failed to return any points", points)
        if (points!!.size != expected) {
            val debug = StringBuilder()
            var i = 0
            for (p in points!!) {
                debug.append("[").append(i++).append("]").append(p).append("\n")
            }

            assertEquals("loadSeries loaded wrong number of points: expected "
                    + expected + ", got " + points.size + "\n" + debug, expected, points.size)
        }

        // validate each point.
        for (i in points.indices) {
            assertNotNull("loadSeries returned null point at index $i", points?.get(i))
            assertNotNull("loadSeries returned null yvalue at index $i",
                    points?.get(i)?.yvalue)
            assertNotNull("loadSeries returned null url at index $i", points?.get(i)?.url)
            assertNotNull("loadSeries returned null label at index $i", points?.get(i)?.label)

            // make sure the yvalue's can be parsed
            try {
                java.lang.Double.parseDouble(points?.get(i)?.yvalue)
            } catch (nfe: NumberFormatException) {
                assertTrue("loadSeries returned invalid yvalue "
                        + points?.get(i)!!.yvalue + " at index " + i
                        + " Exception " + nfe.toString(), false)
            }
        }
    }
}

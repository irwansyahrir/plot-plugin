package hudson.plugins.plot

import junit.framework.TestCase
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
        TestCase.assertNotNull(series)

        TestCase.assertEquals("File name is not configured correctly", file, series.file)
        TestCase.assertEquals("Label is not configured correctly", label, series.label)
        TestCase.assertEquals("Type is not configured correctly", type, series.fileType)
    }

    fun testPlotPoints(points: List<PlotPoint>, expected: Int) {
        TestCase.assertTrue("Must have more than 0 columns", expected > -1)

        TestCase.assertNotNull("loadSeries failed to return any points", points)
        if (points.size != expected) {
            val debug = StringBuilder()
            var i = 0
            for (p in points) {
                debug.append("[").append(i++).append("]").append(p).append("\n")
            }

            TestCase.assertEquals("loadSeries loaded wrong number of points: expected "
                    + expected + ", got " + points.size + "\n" + debug, expected, points.size)
        }

        // validate each point.
        for (i in points.indices) {
            TestCase.assertNotNull("loadSeries returned null point at index $i", points[i])
            TestCase.assertNotNull("loadSeries returned null yvalue at index $i",
                    points[i].yvalue)
            TestCase.assertNotNull("loadSeries returned null url at index $i", points[i].url)
            TestCase.assertNotNull("loadSeries returned null label at index $i", points[i].label)

            // make sure the yvalue's can be parsed
            try {
                java.lang.Double.parseDouble(points[i].yvalue)
            } catch (nfe: NumberFormatException) {
                TestCase.assertTrue("loadSeries returned invalid yvalue "
                        + points[i].yvalue + " at index " + i
                        + " Exception " + nfe.toString(), false)
            }

        }
    }
}

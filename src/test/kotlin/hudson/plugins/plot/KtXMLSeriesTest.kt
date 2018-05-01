package hudson.plugins.plot

import hudson.FilePath
import java.io.File
import java.util.HashMap
import org.junit.After
import org.junit.Before
import org.junit.Ignore

/**
 * Test an XML series.
 *
 * @author Brian Roe
 */

const val TEST_XML_FILE = "test.xml"
const val TEST2_XML_FILE = "test2.xml"
const val TEST3_XML_FILE = "test3.xml"

class KtXMLSeriesTest : KtSeriesTestCase() {
    private var workspaceDirFile: File? = null
    private var workspaceRootDir: FilePath? = null

    @Before
    public override fun setUp() {
        // first create a FilePath to load the test Properties file.
        workspaceDirFile = File("target/test-classes/")
        workspaceRootDir = FilePath(workspaceDirFile!!)
    }

    @After
    public override fun tearDown() {
        workspaceRootDir = null
        workspaceDirFile = null
    }

    fun testXMLSeries_WhenNodesSharingAParentHaveOneStringAndOneNumericContent_ThenCoalesceNodesToPointLabelledWithStringContent() {
        // Create a new XML series.
        val xpath = "//UIAction/name|//UIAction/numCalls"
        val series = XMLSeries(TEST2_XML_FILE, xpath, "NODESET", null)

        // test the basic subclass properties.
        testSeries(series, TEST2_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(4, points!!.size)
        val map = HashMap<String, Double>()
        for (point in points) {
            map[point.label] = java.lang.Double.parseDouble(point.yvalue)
        }

        assertEquals(7, map["AxTermDataService.updateItem"]!!.toInt())
        assertEquals(2, map["AxTermDataService.createEntity"]!!.toInt())
        testPlotPoints(points, 4)
    }

    fun testXMLSeries_WhenNodesHaveNoContent_ThenCoalesceForAttributes() {
        // Create a new XML series.
        val xpath = "//testcase[@name='testOne'] | //testcase[@name='testTwo'] | //testcase[@name='testThree']"

        val series = XMLSeries(TEST_XML_FILE, xpath, "NODESET", null)

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(points!!.size, 3)
        assertEquals(points[0].label, "testOne")
        assertEquals(points[1].label, "testTwo")
        assertEquals(points[2].label, "testThree")
        testPlotPoints(points, 3)
    }

    fun testXMLSeriesNodeset() {
        // Create a new XML series.
        val xpath = "//testcase"

        val series = XMLSeries(TEST_XML_FILE, xpath, "NODESET", null)

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(4, points!!.size)
        assertEquals(points[0].label, "testOne")
        assertEquals(points[1].label, "testTwo")
        assertEquals(points[2].label, "testThree")
        assertEquals(points[3].label, "testFour")
        assertEquals(points[3].yvalue, "1234.56")
        testPlotPoints(points, 4)
    }

    fun testXMLSeries_WhenAllNodesAreNumeric_ThenPointsAreLabelledWithNodeName() {
        // Create a new XML series.
        val xpath = "/results/testcase/*"

        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null)

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(2, points!!.size)
        assertEquals(points[0].label, "one")
        assertEquals(points[0].yvalue, "0.521")
        testPlotPoints(points, 2)
    }

    fun testXMLSeriesEmptyNodeset() {
        // Create a new XML series.
        val xpath = "/there/is/no/such/element"

        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null)

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(0, points!!.size)
        testPlotPoints(points, 0)
    }

    fun testXMLSeriesNode() {
        // Create a new XML series.
        val xpath = "//testcase[@name='testThree']"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NODE", null)

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        assertEquals(points!!.size, 1)
        assertEquals(java.lang.Double.parseDouble(points[0].yvalue), 27.0)
        testPlotPoints(points, 1)
    }

    fun testXMLSeriesString() {
        // Create a new XML series.
        val xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "STRING", null)

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        testPlotPoints(points, 1)
    }

    fun testXMLSeriesNumber() {
        // Create a new XML series.
        var xpath = "concat(//testcase[@name='testOne']/@name, '=', //testcase[@name='testOne']/@time)"
        xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NUMBER",
                "splunge")

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        testPlotPoints(points, 1)
    }

    fun testXMLSeriesUrl() {
        // Create a new XML series.
        val xpath = "/results/testcase/*"

        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET",
                "http://localhost/%build%/%name%/%index%")

        // test the basic subclass properties.
        testSeries(series, TEST3_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 42, System.out)
        assertNotNull(points)
        testPlotPoints(points, 2)
        assertEquals("http://localhost/42/one/0", points!![0].url)
        assertEquals("http://localhost/42/two/0", points[1].url)
    }

    @Ignore
    fun testXMLSeriesBoolean() {
        // Create a new XML series.
        val xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "BOOLEAN", null)

        // test the basic subclass properties.
        testSeries(series, TEST_XML_FILE, "", "xml")

        // load the series.
        val points = series.loadSeries(workspaceRootDir!!, 0, System.out)
        assertNotNull(points)
        testPlotPoints(points, 1)
    }
}

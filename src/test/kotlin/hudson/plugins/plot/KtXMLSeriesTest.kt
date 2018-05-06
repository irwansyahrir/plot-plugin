package hudson.plugins.plot

import hudson.FilePath
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import java.io.File
import java.util.*

/**
 * Test an XML series.
 *
 * @author Brian Roe
 */

const val TEST_XML_FILE = "test.xml"
const val TEST2_XML_FILE = "test2.xml"
const val TEST3_XML_FILE = "test3.xml"

class KtXMLSeriesTest : KtSeriesTestCase() {
    private var xmlFileDir: File? = null
    private var xmlFilePath: FilePath? = null

    @Before
    public override fun setUp() {
        // first create a FilePath to load the test Properties file.
        xmlFileDir = File("target/test-classes/")
        xmlFilePath = FilePath(xmlFileDir!!)
    }

    @After
    public override fun tearDown() {
        xmlFilePath = null
        xmlFileDir = null
    }

    fun testXMLSeries_WhenNodesSharingAParentHaveOneStringAndOneNumericContent_ThenCoalesceNodesToPointLabelledWithStringContent() {

        val xpath = "//UIAction/name|//UIAction/numCalls"
        val series = XMLSeries(TEST2_XML_FILE, xpath, "NODESET", null)

        testSeriesProperties(series, TEST2_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(4)

        val labelValueMap = HashMap<String, Double>()
        for (point in points) {
            labelValueMap[point.label] = java.lang.Double.parseDouble(point.yvalue)
        }

        assertThat(labelValueMap["AxTermDataService.updateItem"]).isEqualTo(7.0)
        assertThat(labelValueMap["AxTermDataService.createEntity"]).isEqualTo(2.0)

        testPlotPoints(points, 4)
    }

    fun testXMLSeries_NODE_TO_STRING_WhenNodesHaveNoContent_ThenCoalesceForAttributes() {
        val xpath = "//testcase[@name='testOne'] | //testcase[@name='testTwo'] | //testcase[@name='testThree']"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NODESET", null)

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(3)
        assertThat(points[0].label).isEqualTo("testOne")
        assertThat(points[1].label).isEqualTo("testTwo")
        assertThat(points[2].label).isEqualTo("testThree")

        testPlotPoints(points, 3)
    }

    fun testXMLSeriesNodeset() {
        val xpath = "//testcase"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NODESET", null)

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(4)
        assertThat(points[0].label).isEqualTo("testOne")
        assertThat(points[1].label).isEqualTo("testTwo")
        assertThat(points[2].label).isEqualTo("testThree")
        assertThat(points[3].label).isEqualTo("testFour")
        assertThat(points[3].yvalue).isEqualTo("1234.56")

        testPlotPoints(points, 4)
    }

    fun testXMLSeries_WhenAllNodesAreNumeric_ThenPointsAreLabelledWithNodeName() {
        val xpath = "/results/testcase/*"
        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null)

        testSeriesProperties(series, TEST3_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(2)
        assertThat(points[0].label).isEqualTo("one")
        assertThat(points[0].yvalue).isEqualTo("0.521")

        testPlotPoints(points, 2)
    }

    fun testXMLSeriesEmptyNodeset() {
        val xpath = "/there/is/no/such/element"
        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET", null)

        testSeriesProperties(series, TEST3_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(0)

        testPlotPoints(points, 0)
    }

    fun testXMLSeriesNode_ADD_NODE_TO_LIST() { //USE THIS
        val xpath = "//testcase[@name='testThree']"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NODE", null)

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull
        assertThat(points!!.size).isEqualTo(1)
        assertThat(java.lang.Double.parseDouble(points[0].yvalue)).isEqualTo(27.0)

        testPlotPoints(points, 1)
    }

    fun testXMLSeriesString() {
        val xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "STRING", null)

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull

        testPlotPoints(points, 1)
    }

    fun testXMLSeriesNumber() {
        val xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "NUMBER","splunge")

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull

        testPlotPoints(points, 1)
    }

    fun testXMLSeriesUrl() {
        val xpath = "/results/testcase/*"
        val url = "http://localhost/%build%/%name%/%index%"
        val series = XMLSeries(TEST3_XML_FILE, xpath, "NODESET", url)

        testSeriesProperties(series, TEST3_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 42, System.out)

        assertThat(points).isNotNull

        testPlotPoints(points, 2)

        assertThat(points!![0].url).isEqualTo("http://localhost/42/one/0")
        assertThat(points[1].url).isEqualTo("http://localhost/42/two/0")
    }

    fun testXMLSeriesBoolean() {
        val xpath = "//testcase[@name='testOne']/@time"
        val series = XMLSeries(TEST_XML_FILE, xpath, "BOOLEAN", null)

        testSeriesProperties(series, TEST_XML_FILE, "", "xml")

        val points = series.loadSeries(xmlFilePath!!, 0, System.out)

        assertThat(points).isNotNull

        testPlotPoints(points, 1)
    }
}

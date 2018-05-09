/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.model.Descriptor
import net.sf.json.JSONObject
import org.apache.commons.io.IOUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.StaplerRequest
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.InputStream
import java.io.PrintStream
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import javax.xml.namespace.QName
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpressionException
import javax.xml.xpath.XPathFactory

/**
 * Represents a plot data series configuration from an XML file.
 *
 * @author Allen Reese
 */
class XMLSeries : Series {
    companion object {
        @Transient
        private val LOGGER = Logger.getLogger(XMLSeries::class.simpleName)
        // Debugging hack, so I don't have to change FINE/INFO...
        @Transient
        private val DEFAULT_LOG_LEVEL = Level.INFO

        @Transient
        private val Q_NAME_MAP: Map<String, QName>

        init {
            Q_NAME_MAP = hashMapOf(
                    "BOOLEAN" to XPathConstants.BOOLEAN,
                    "NODE" to XPathConstants.NODE,
                    "NODESET" to XPathConstants.NODESET,
                    "NUMBER" to XPathConstants.NUMBER,
                    "STRING" to XPathConstants.STRING
            )
        }
    }

    /**
     * XPath to select for values
     */
    private val xpathString: String

    /**
     * Url to use as a base for mapping points.
     */
    private val baseUrl: String?

    /**
     * String of the qname type to use
     */
    private val nodeTypeString: String

    /**
     * Actual nodeType
     */
    @Transient
    private var nodeType: QName? = null

    @DataBoundConstructor
    constructor(file: String, xpath: String, nodeType: String, url: String?) : super(file, "", "xml") {
        this.xpathString = xpath
        this.nodeTypeString = nodeType
        this.nodeType = Q_NAME_MAP[nodeType]
        this.baseUrl = url
    }

    /**
     * @param buildNumber the build number
     * @return a List of PlotPoints where the label is the element name and the
     * value is the node content.
     */
    private fun mapNodeNameAsLabelTextContentAsValueStrategy(nodeList: NodeList,
                                                             buildNumber: Int): List<PlotPoint> {
        val series = mutableListOf<PlotPoint>()
        (0 until nodeList.length).forEach { i ->
            series.add(getNode(nodeList.item(i), buildNumber))
        }
        return series
    }

    /**
     * This is a fallback strategy for nodesets that include non numeric content
     * enabling users to create lists by selecting them such that names and
     * values share a common parent. If a node has attributes and is empty that
     * node will be re-enqueued as a parent to its attributes.
     *
     * @param buildNumber the build number
     * @return a list of PlotPoints where the label is the last non numeric
     * text content and the value is the last numeric text content for
     * each set of nodes under a given parent.
     */
    private fun coalesceTextnodesAsLabels(nodeList: NodeList, buildNumber: Int): List<PlotPoint> {

        val parentNodeMap = mutableMapOf<Node, MutableList<Node>>()

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            parentNodeMap.putIfAbsent(node.parentNode, mutableListOf())
            parentNodeMap[node.parentNode]?.add(node)
        }

        val returnValue = mutableListOf<PlotPoint>()
        val parents = ArrayDeque(parentNodeMap.keys)
        while (!parents.isEmpty()) {
            val parent = parents.poll()
            var value = 0.0
            var label = ""

            val children = parentNodeMap[parent]
            for (child in children!!) {
                when {
                    child.textContent.isNullOrEmpty() || child.textContent.trim().isEmpty() -> {
                        val childAttrs = child.attributes
                        parentNodeMap[child] = (0 until childAttrs.length).mapTo(ArrayList()) { childAttrs.item(it) }
                        parents.add(child)
                    }

                    Scanner(child.textContent.trim()).hasNextDouble() -> value = Scanner(child.textContent.trim()).nextDouble()

                    else -> label = child.textContent.trim()
                }
            }

            if (label.isNotEmpty() && value != 0.0) {
                returnValue.add(getSeriesValue(label, value.toString(), buildNumber)!!)
            }
        }
        return returnValue
    }

    /**
     * Load the series from a properties file.
     */
    override fun loadSeries(workspaceRootDir: FilePath,
                            buildNumber: Int,
                            logger: PrintStream): List<PlotPoint> {

        try {
            val seriesFiles: Array<FilePath> = retrieveSeriesFiles(workspaceRootDir)
            if (seriesFiles.isEmpty()){
                return emptyList()
            }

            var inputSource: InputSource? = getInputSource(seriesFiles) ?: return emptyList()

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "NodeType $nodeTypeString : $nodeType")
            }

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Loaded XML Plot file: " + super.file)
            }

            val xpath = XPathFactory.newInstance().newXPath()
            val xmlObject = xpath.evaluate(this.xpathString, inputSource, nodeType)

            /*
             * If we have a nodeset, we need multiples, otherwise we just need
             * one value, and can do a toString() to set it.
             */
            val returnedSeries = mutableListOf<PlotPoint>()
            if (nodeType == XPathConstants.NODESET) {
                return getSeriesForNodeset(xmlObject, buildNumber)
            } else if (nodeType == XPathConstants.NODE) {
                returnedSeries.add(getNode(xmlObject as Node, buildNumber))
            } else {
                if (xmlObject is NodeList) {
                    addNodeListToSeries(returnedSeries, xmlObject, buildNumber)
                } else {
                    returnedSeries.add(getSeriesValue(label, xmlObject, buildNumber)!!)
                }
            }
            return returnedSeries
        } catch (e: XPathExpressionException) {
            LOGGER.log(Level.SEVERE, "XPathExpressionException for XPath '$xpathString'", e)
        }

        return emptyList()
    }

    private fun addNodeListToSeries(series: MutableList<PlotPoint>, nodeList: NodeList, buildNumber: Int) {
        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
            LOGGER.log(DEFAULT_LOG_LEVEL, "Number of nodes: " + nodeList.length)
        }

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node?.localName != null && node.textContent != null) {
                series.add(getSeriesValue(label, nodeList, buildNumber)!!)
            }
        }
    }

    private fun getSeriesForNodeset(xmlObject: Any?, buildNumber: Int): List<PlotPoint> {
        val nodeList = xmlObject as NodeList

        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
            LOGGER.log(DEFAULT_LOG_LEVEL, "Number of nodes: " + nodeList.length)
        }

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (!Scanner(node.textContent.trim()).hasNextDouble()) {
                return coalesceTextnodesAsLabels(nodeList, buildNumber)
            }
        }
        return mapNodeNameAsLabelTextContentAsValueStrategy(nodeList, buildNumber)
    }

    private fun getInputSource(seriesFiles: Array<FilePath>): InputSource? {
        var inputStream : InputStream? = null
        try {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Loading plot series data from: " + super.file)
            }

            inputStream = seriesFiles[0].read()
            // load existing plot file
            return InputSource(inputStream)
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Exception reading plot series data from " + seriesFiles[0], e)
            IOUtils.closeQuietly(inputStream)
            return null
        }

        //TODO: check if IOUtils.closeQuietly(inputStream) should be called somewhere else
    }

    private fun retrieveSeriesFiles(workspaceRootDir: FilePath): Array<FilePath> {
        val seriesFiles: Array<FilePath>
        try {
            seriesFiles = workspaceRootDir.list(super.file)
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Exception trying to retrieve series files", e)
            return emptyArray()
        }

        if (seriesFiles.isEmpty()) {
            LOGGER.info("No plot data file found: " + super.file)
            return emptyArray()
        }

        return seriesFiles
    }

    private fun getNode(node: Node, buildNumber: Int) : PlotPoint {
        val label = node.attributes?.getNamedItem("name")?.textContent?.trim() ?: node.localName.trim()

        return getSeriesValue(label, node, buildNumber)!!

    }

    /**
     * Convert a given object into a String.
     *
     * @param nodeObject Xpath Object
     * @return String representation of the node
     */
    private fun nodeToString(nodeObject: Any): String {
        if (nodeType === XPathConstants.BOOLEAN) {
            return if (nodeObject as Boolean) "1" else "0"
        }

        if (nodeType === XPathConstants.NUMBER) {
            return (nodeObject as Double).toString().trim()
        }

        if (nodeType === XPathConstants.NODE || nodeType === XPathConstants.NODESET) {
            return when (nodeObject) {
                is String -> parseAsDouble(nodeObject.trim())
                else -> {
                    val node = nodeObject as Node
                    val namedItem = node.attributes?.getNamedItem("time")
                    parseAsDouble(namedItem?.textContent?.trim() ?: node.textContent.trim())
                }
            }
        }

        if (nodeType === XPathConstants.STRING) {
            return parseAsDouble((nodeObject as String).trim())
        }

        return ""
    }

    private fun parseAsDouble(nodeString: String): String {
        return when {
            Scanner(nodeString).hasNextDouble() -> Scanner(nodeString).nextDouble().toString()
            else -> ""
        }
    }

    /**
     * Add a given value to the plotPoints of results. This encapsulates some
     * otherwise duplicate logic due to nodeset/!nodeset
     */
    private fun getSeriesValue(label: String?, nodeValue: Any, buildNumber: Int): PlotPoint? {

        val value = nodeToString(nodeValue)

        if (value.isNotEmpty()) {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Adding node: $label value: $value")
            }
            val pointUrl = getUrl(baseUrl, label, 0, buildNumber)
            return PlotPoint(value, pointUrl, label)
        } else {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Unable to add node: $label value: $nodeValue")
            }
            return null
        }
    }

    override fun getDescriptor(): Descriptor<Series> {
        return DescriptorImpl()
    }

    @Extension
    class DescriptorImpl : Descriptor<Series>() {
        override fun getDisplayName(): String {
            return Messages.Plot_XmlSeries()
        }

        @Throws(Descriptor.FormException::class)
        override fun newInstance(req: StaplerRequest, formData: JSONObject): Series? {
            return SeriesFactory.createSeries(formData, req)
        }
    }
}

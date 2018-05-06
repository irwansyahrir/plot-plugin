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
import org.apache.commons.lang.ArrayUtils
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

        //Fill out the qName map for easy reference.
        init {
            val tempMap = HashMap<String, QName>()
            tempMap["BOOLEAN"] = XPathConstants.BOOLEAN
            tempMap["NODE"] = XPathConstants.NODE
            tempMap["NODESET"] = XPathConstants.NODESET
            tempMap["NUMBER"] = XPathConstants.NUMBER
            tempMap["STRING"] = XPathConstants.STRING
            Q_NAME_MAP = Collections.unmodifiableMap(tempMap)
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

    private fun readResolve(): Any {
        // Set nodeType when deserialized
        nodeType = Q_NAME_MAP[nodeTypeString]
        return this
    }

    fun getNodeType(): String {
        return nodeTypeString
    }

    /**
     * @param buildNumber the build number
     * @return a List of PlotPoints where the label is the element name and the
     * value is the node content.
     */
    private fun mapNodeNameAsLabelTextContentAsValueStrategy(nodeList: NodeList,
                                                             buildNumber: Int): List<PlotPoint> {
        val retval = mutableListOf<PlotPoint>()
        (0 until nodeList.length).forEach { i ->
            this.addNodeToList(retval, nodeList.item(i), buildNumber)
        }
        return retval
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

        val retval = mutableListOf<PlotPoint>()
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
                addValueToList(retval, label, value.toString(), buildNumber)
            }
        }
        return retval
    }

    /**
     * Load the series from a properties file.
     */
    override fun loadSeries(workspaceRootDir: FilePath,
                            buildNumber: Int,
                            logger: PrintStream): List<PlotPoint> {

        var inputStream: InputStream? = null
        val inputSource: InputSource

        try {
            val returnedPlotPoints = ArrayList<PlotPoint>()
            val seriesFiles: Array<FilePath>

            try {
                seriesFiles = workspaceRootDir.list(super.file)
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Exception trying to retrieve series files", e)
                return emptyList()
            }

            if (ArrayUtils.isEmpty(seriesFiles)) {
                LOGGER.info("No plot data file found: " + super.file)
                return emptyList()
            }

            try {
                if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                    LOGGER.log(DEFAULT_LOG_LEVEL, "Loading plot series data from: " + super.file)
                }

                inputStream = seriesFiles[0].read()
                // load existing plot file
                inputSource = InputSource(seriesFiles[0].read())
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE,"Exception reading plot series data from " + seriesFiles[0], e)
                return emptyList()
            }

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
            if (nodeType == XPathConstants.NODESET) {
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
            } else if (nodeType == XPathConstants.NODE) {
                addNodeToList(returnedPlotPoints, xmlObject as Node, buildNumber)
            } else {
                // otherwise we have a single type and can do a toString on it.
                if (xmlObject is NodeList) {

                    if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                        LOGGER.log(DEFAULT_LOG_LEVEL, "Number of nodes: " + xmlObject.length)
                    }

                    for (i in 0 until xmlObject.length) {
                        val n = xmlObject.item(i)

                        if (n != null && n.localName != null && n.textContent != null) {
                            addValueToList(returnedPlotPoints, label, xmlObject, buildNumber)
                        }
                    }
                } else {
                    addValueToList(returnedPlotPoints, label, xmlObject, buildNumber)
                }
            }
            return returnedPlotPoints
        } catch (e: XPathExpressionException) {
            LOGGER.log(Level.SEVERE, "XPathExpressionException for XPath '$xpathString'", e)
        } finally {
            IOUtils.closeQuietly(inputStream)
        }

        return emptyList()
    }

    private fun addNodeToList(returnList: MutableList<PlotPoint>, node: Node, buildNumber: Int) {
        val label = node.attributes?.getNamedItem("name")?.textContent?.trim() ?: node.localName.trim()

        addValueToList(returnList, label, node, buildNumber)
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
    private fun addValueToList(plotPoints: MutableList<PlotPoint>, label: String?,
                               nodeValue: Any, buildNumber: Int) {

        val value = nodeToString(nodeValue)

        if (value.isNotEmpty()) {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Adding node: $label value: $value")
            }
            val pointUrl = getUrl(baseUrl, label, 0, buildNumber)
            plotPoints.add(PlotPoint(value, pointUrl, label))
        } else {
            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Unable to add node: $label value: $nodeValue")
            }
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

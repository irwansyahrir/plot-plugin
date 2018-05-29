/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

import au.com.bytecode.opencsv.CSVReader
import hudson.Extension
import hudson.FilePath
import hudson.model.Descriptor
import hudson.plugins.plot.InclusionFlag.*
import mu.KotlinLogging
import net.sf.json.JSONObject
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.ArrayUtils
import org.apache.commons.lang.ObjectUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.StaplerRequest
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern

/**
 * Represents a plot data series configuration from an CSV file.
 *
 * @author Allen Reese
 */

enum class InclusionFlag {
    OFF, INCLUDE_BY_STRING, EXCLUDE_BY_STRING, INCLUDE_BY_COLUMN, EXCLUDE_BY_COLUMN
}

private val logger = KotlinLogging.logger{}
private val PATTERN_COMMA = Pattern.compile(",")

class CSVSeries : Series {

    /**
     * Set for excluding values by column name
     */
    private var strExclusionSet: MutableSet<String>? = null

    /**
     * Set for excluding values by column #
     */
    private var colExclusionSet: MutableSet<Int>? = null

    /**
     * Flag controlling how values are excluded.
     */
    private var inclusionFlag: InclusionFlag?

    /**
     * Comma separated list of columns to exclude.
     */
    var exclusionValues: String? = null
        private set

    /**
     * Url to use as a base for mapping points.
     */
    val url: String?
    val displayTableFlag: Boolean


    @DataBoundConstructor constructor(file: String, url: String?, inclusionFlag: String?, exclusionValues: String?,
                                      displayTableFlag: Boolean) : super(file, "", "csv") {
        this.url = url
        this.displayTableFlag = displayTableFlag
        this.inclusionFlag = OFF
        if (exclusionValues == null) {
            this.inclusionFlag = OFF
        } else {
            this.inclusionFlag = valueOf(inclusionFlag!!)
            this.exclusionValues = exclusionValues
            loadExclusionSet()
        }
    }

    fun getInclusionFlag(): String {
        return ObjectUtils.toString(inclusionFlag)
    }

    /**
     * Load the series from a properties file.
     */
    override fun loadSeries(workspaceRootDir: FilePath,
                            buildNumber: Int, printStream: PrintStream): List<PlotPoint> {
        var reader: CSVReader? = null
        var inputStream: InputStream? = null
        var inputReader: InputStreamReader? = null

        try {
            val seriesFiles: Array<FilePath>
            try {
                seriesFiles = workspaceRootDir.list(file)
            } catch (e: Exception) {
                logger.error {"$e when trying to retrieve series files"}
                return emptyList()
            }

            if (ArrayUtils.isEmpty(seriesFiles)) {
                logger.info {"No plot data file found: ${workspaceRootDir.name} $file"}
                return emptyList()
            }

            try {
                if (logger.isTraceEnabled) {
                    logger.trace { "Loading plot series data from: $file" }
                }

                inputStream = seriesFiles[0].read()
            } catch (e: Exception) {
                logger.error { "Exception reading plot series data from ${seriesFiles[0]}" }
                return emptyList()
            }

            if (logger.isTraceEnabled) {
                logger.trace { "Loaded CSV Plot file: $file" }
            }

            // load existing plot file
            inputReader = InputStreamReader(inputStream!!, Charset.defaultCharset().name())
            reader = CSVReader(inputReader)

            // save the header line to use it for the plot labels.
            val headerLine = reader.readNext()

            return getSeriesFromCSVLines(reader, headerLine, buildNumber)

        } catch (ioe: IOException) {
            logger.error { "$ioe when loading series" }
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    logger.error { "$e failed to close series reader" }
                }

            }
            IOUtils.closeQuietly(inputReader)
            IOUtils.closeQuietly(inputStream)
        }

        return emptyList()
    }

    private fun getSeriesFromCSVLines(reader: CSVReader, headerLine: Array<String>, buildNumber: Int): ArrayList<PlotPoint> {

        val series = ArrayList<PlotPoint>()

        var lineNum = 0
        var nextLine: Array<out String?> = reader.readNext()
        while (nextLine.size > 1) {
            // skip empty lines
            if (nextLine.size == 1 && nextLine[0]!!.isEmpty()) {
                continue
            }

            for (index in nextLine.indices) {
                var label: String? = null

                if (index > nextLine.size) {
                    continue
                }

                val yValue = nextLine[index]!!

                // empty value, caused by e.g. trailing comma in CSV
                if (yValue.trim().isEmpty()) {
                    continue
                }

                if (index < headerLine.size) {
                    label = headerLine[index]
                }

                if (label == null || label.isEmpty()) {
                    // if there isn't a label, use the index as the label
                    label = "" + index
                }

                // LOGGER.finest("Loaded point: " + point);

                addPoint(label, index, yValue, buildNumber, lineNum, series)
            }
            lineNum++
            nextLine = reader.readNext()
        }

        return series
    }

    private fun addPoint(label: String, index: Int, yValue: String, buildNumber: Int, lineNum: Int, series: ArrayList<PlotPoint>) {
        if (!excludePoint(label, index)) {
            val pointUrl = getUrl(url, label, index, buildNumber)
            val point = PlotPoint(yValue, pointUrl, label)
            if (logger.isTraceEnabled) {
                logger.trace { "CSV Point: [$index:$lineNum]$point" }
            }
            series.add(point)
        } else {
            if (logger.isTraceEnabled) {
                logger.trace { "excluded CSV Column: $index : $label" }
            }
        }
    }

    /**
     * This function checks the exclusion/inclusion filters from the properties
     * file and returns true if a point should be excluded.
     *
     * @return true if the point should be excluded based on label or column
     */
    private fun excludePoint(label: String, index: Int): Boolean {
        if (inclusionFlag == null || inclusionFlag == OFF) {
            return false
        }

        val isPointExcluded: Boolean = when (inclusionFlag) {
            INCLUDE_BY_STRING ->
                // if the set contains it, don't exclude it.
                !strExclusionSet!!.contains(label)
            EXCLUDE_BY_STRING ->
                // if the set doesn't contain it, exclude it.
                strExclusionSet!!.contains(label)
            INCLUDE_BY_COLUMN ->
                // if the set contains it, don't exclude it.
                !colExclusionSet!!.contains(Integer.valueOf(index))
            EXCLUDE_BY_COLUMN ->
                // if the set doesn't contain it, don't exclude it.
                colExclusionSet!!.contains(Integer.valueOf(index))
            else -> false
        }

        if (logger.isTraceEnabled) {
            logger.trace { "${if (isPointExcluded) "excluded" else "included"} CSV Column: $index : $label" }
        }

        return isPointExcluded
    }

    /**
     * This function loads the set of columns that should be included or
     * excluded.
     */
    private fun loadExclusionSet() {
        if (inclusionFlag == OFF) {
            return
        }

        if (exclusionValues == null) {
            inclusionFlag = OFF
            return
        }

        when (inclusionFlag) {
            INCLUDE_BY_STRING, EXCLUDE_BY_STRING -> strExclusionSet = mutableSetOf()
            INCLUDE_BY_COLUMN, EXCLUDE_BY_COLUMN -> colExclusionSet = mutableSetOf()
            else -> logger.error { "Failed to initialize columns exclusions set." }
        }

        for (str in PATTERN_COMMA.split(exclusionValues)) {
            if (str == null || str.length <= 0) {
                continue
            }

            when (inclusionFlag) {
                INCLUDE_BY_STRING, EXCLUDE_BY_STRING -> {
                    if (logger.isTraceEnabled) {
                        logger.trace { "${inclusionFlag.toString()} CSV Column: $str" }
                    }
                    strExclusionSet!!.add(str)
                }
                INCLUDE_BY_COLUMN, EXCLUDE_BY_COLUMN -> try {
                    if (logger.isTraceEnabled) {
                        logger.trace { "${inclusionFlag.toString()} CSV Column: $str" }
                    }
                    colExclusionSet!!.add(Integer.valueOf(str))
                } catch (nfe: NumberFormatException) {
                    logger.error { "$nfe when converting to interger" }
                }

                else -> logger.error { "Failed to identify columns exclusions." }
            }
        }
    }

    override fun getDescriptor(): Descriptor<Series> {
        return DescriptorImpl()
    }

    @Extension
    class DescriptorImpl : Descriptor<Series>() {
        override fun getDisplayName(): String {
            return Messages.Plot_CsvSeries()
        }

        @Throws(Descriptor.FormException::class)
        override fun newInstance(req: StaplerRequest, formData: JSONObject): Series? {
            return SeriesFactory.createSeries(formData, req)
        }
    }
}

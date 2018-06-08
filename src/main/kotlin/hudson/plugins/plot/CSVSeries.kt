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
    OFF, INCLUDE_LABEL, EXCLUDE_LABEL, INCLUDE_COLUMN, EXCLUDE_COLUMN
}

private val logger = KotlinLogging.logger{}
private val PATTERN_COMMA = Pattern.compile(",")

class CSVSeries : Series {

    /**
     * Set for excluding values by column name
     */
    private var excludedLabels: MutableSet<String>? = null

    /**
     * Set for excluding values by column #
     */
    private var excludedColumns: MutableSet<Int>? = null

    /**
     * Flag controlling how values are excluded.
     */
    private var inclusionFlag: InclusionFlag?

    /**
     * Comma separated list of columns to exclude.
     */
    var labelsToExclude: String? = null
        private set

    /**
     * Url to use as a base for mapping points.
     */
    val url: String?
    val displayTableFlag: Boolean


    @DataBoundConstructor constructor(
            file: String,
            url: String?,
            inclusionFlag: String?,
            columnsToExclude: String?,
            displayTableFlag: Boolean) : super(file, "", "csv") {

        this.url = url
        this.displayTableFlag = displayTableFlag
        this.inclusionFlag = OFF
        if (columnsToExclude == null) {
            this.inclusionFlag = OFF
        } else {
            this.inclusionFlag = valueOf(inclusionFlag!!)
            this.labelsToExclude = columnsToExclude
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
                            buildNumber: Int,
                            printStream: PrintStream): List<PlotPoint> {

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
            if (skipEmptyLines(nextLine)) {
                continue
            }

            for (index in nextLine.indices) {
                if (index > nextLine.size) {
                    continue
                }

                val yValue = nextLine[index]!!
                if (yValue.trim().isEmpty()) {
                    continue
                }

                val label: String = createLabel(index, headerLine)

                addPoint(label, index, yValue, buildNumber, lineNum, series)
            }
            lineNum++
            nextLine = reader.readNext()
        }

        return series
    }

    private fun skipEmptyLines(nextLine: Array<out String?>) = nextLine.size == 1 && nextLine[0]!!.isEmpty()

    private fun createLabel(index: Int, headerLine: Array<String>): String {
        if (index < headerLine.size) {
            if (headerLine[index].isEmpty()) {
                return "" + index
            }
        }

        return ""
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
            INCLUDE_LABEL -> !excludedLabels!!.contains(label)
            EXCLUDE_LABEL -> excludedLabels!!.contains(label)
            INCLUDE_COLUMN -> !excludedColumns!!.contains(Integer.valueOf(index))
            EXCLUDE_COLUMN -> excludedColumns!!.contains(Integer.valueOf(index))
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

        if (labelsToExclude == null) {
            inclusionFlag = OFF
            return
        }

        when (inclusionFlag) {
            INCLUDE_LABEL, EXCLUDE_LABEL -> excludedLabels = mutableSetOf()
            INCLUDE_COLUMN, EXCLUDE_COLUMN -> excludedColumns = mutableSetOf()
            else -> logger.error { "Failed to initialize columns exclusions set." }
        }

        for (labelToExclude in PATTERN_COMMA.split(labelsToExclude)) {
            if (labelToExclude == null || labelToExclude.length <= 0) {
                continue
            }

            when (inclusionFlag) {
                INCLUDE_LABEL, EXCLUDE_LABEL -> addExcludedLabel(labelToExclude)

                INCLUDE_COLUMN, EXCLUDE_COLUMN -> addExcludedColumn(labelToExclude)

                else -> logger.error { "Failed to identify columns exclusions." }
            }
        }
    }

    private fun addExcludedColumn(column: String?) {
        try {
            if (logger.isTraceEnabled) {
                logger.trace { "${inclusionFlag.toString()} CSV Column: $column" }
            }
            excludedColumns!!.add(Integer.valueOf(column))
        } catch (nfe: NumberFormatException) {
            logger.error { "$nfe when converting to integer" }
        }
    }

    private fun addExcludedLabel(label: String) {
        if (logger.isTraceEnabled) {
            logger.trace { "${inclusionFlag.toString()} CSV Column: $label" }
        }
        excludedLabels!!.add(label)
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

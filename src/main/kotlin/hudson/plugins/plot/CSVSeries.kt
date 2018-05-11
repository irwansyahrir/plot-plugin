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
import java.util.logging.Level
import java.util.logging.Logger
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
        this.inclusionFlag = InclusionFlag.OFF
        if (exclusionValues == null) {
            this.inclusionFlag = InclusionFlag.OFF
        } else {
            this.inclusionFlag = InclusionFlag.valueOf(inclusionFlag!!)
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
        var `in`: InputStream? = null
        var inputReader: InputStreamReader? = null

        try {
            val ret = ArrayList<PlotPoint>()

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

                `in` = seriesFiles[0].read()
            } catch (e: Exception) {
                logger.error { "Exception reading plot series data from ${seriesFiles[0]}" }
                return emptyList()
            }

            if (logger.isTraceEnabled) {
                logger.trace { "Loaded CSV Plot file: $file" }
            }

            // load existing plot file
            inputReader = InputStreamReader(`in`!!, Charset.defaultCharset().name())
            reader = CSVReader(inputReader)

            // save the header line to use it for the plot labels.
            val headerLine = reader.readNext()

            // read each line of the CSV file and add to rawPlotData
            var lineNum = 0
            var nextLine : Array<out String?> = reader.readNext()
            while (nextLine.size > 1) {
                // skip empty lines
                if (nextLine.size == 1 && nextLine[0]!!.length == 0) {
                    continue
                }

                for (index in nextLine.indices) {
                    val yvalue: String
                    var label: String? = null

                    if (index > nextLine.size) {
                        continue
                    }

                    yvalue = nextLine[index]!!

                    // empty value, caused by e.g. trailing comma in CSV
                    if (yvalue.trim { it <= ' ' }.length == 0) {
                        continue
                    }

                    if (index < headerLine.size) {
                        label = headerLine[index]
                    }

                    if (label == null || label.length <= 0) {
                        // if there isn't a label, use the index as the label
                        label = "" + index
                    }

                    // LOGGER.finest("Loaded point: " + point);

                    // create a new point with the yvalue from the csv file and
                    // url from the URL_index in the properties file.
                    if (!excludePoint(label, index)) {
                        val point = PlotPoint(yvalue, getUrl(url,
                                label, index, buildNumber), label)
                        if (logger.isTraceEnabled) {
                            logger.trace { "CSV Point: [$index:$lineNum]$point" }
                        }
                        ret.add(point)
                    } else {
                        if (logger.isTraceEnabled) {
                            logger.trace { "excluded CSV Column: $index : $label" }
                        }
                    }
                }
                lineNum++
                nextLine = reader.readNext()
            }

            return ret
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
            IOUtils.closeQuietly(`in`)
        }

        return emptyList()
    }

    /**
     * This function checks the exclusion/inclusion filters from the properties
     * file and returns true if a point should be excluded.
     *
     * @return true if the point should be excluded based on label or column
     */
    private fun excludePoint(label: String, index: Int): Boolean {
        if (inclusionFlag == null || inclusionFlag == InclusionFlag.OFF) {
            return false
        }

        val retVal: Boolean
        when (inclusionFlag) {
            InclusionFlag.INCLUDE_BY_STRING ->
                // if the set contains it, don't exclude it.
                retVal = !strExclusionSet!!.contains(label)
            InclusionFlag.EXCLUDE_BY_STRING ->
                // if the set doesn't contain it, exclude it.
                retVal = strExclusionSet!!.contains(label)
            InclusionFlag.INCLUDE_BY_COLUMN ->
                // if the set contains it, don't exclude it.
                retVal = !colExclusionSet!!.contains(Integer.valueOf(index))
            InclusionFlag.EXCLUDE_BY_COLUMN ->
                // if the set doesn't contain it, don't exclude it.
                retVal = colExclusionSet!!.contains(Integer.valueOf(index))
            else -> retVal = false
        }

        if (logger.isTraceEnabled) {
            logger.trace { "${if (retVal) "excluded" else "included"} CSV Column: $index : $label" }
        }

        return retVal
    }

    /**
     * This function loads the set of columns that should be included or
     * excluded.
     */
    private fun loadExclusionSet() {
        if (inclusionFlag == InclusionFlag.OFF) {
            return
        }

        if (exclusionValues == null) {
            inclusionFlag = InclusionFlag.OFF
            return
        }

        when (inclusionFlag) {
            InclusionFlag.INCLUDE_BY_STRING, InclusionFlag.EXCLUDE_BY_STRING -> strExclusionSet = mutableSetOf()
            InclusionFlag.INCLUDE_BY_COLUMN, InclusionFlag.EXCLUDE_BY_COLUMN -> colExclusionSet = mutableSetOf()
            else -> logger.error { "Failed to initialize columns exclusions set." }
        }

        for (str in PATTERN_COMMA.split(exclusionValues)) {
            if (str == null || str.length <= 0) {
                continue
            }

            when (inclusionFlag) {
                InclusionFlag.INCLUDE_BY_STRING, InclusionFlag.EXCLUDE_BY_STRING -> {
                    if (logger.isTraceEnabled) {
                        logger.trace { "${inclusionFlag.toString()} CSV Column: $str" }
                    }
                    strExclusionSet!!.add(str)
                }
                InclusionFlag.INCLUDE_BY_COLUMN, InclusionFlag.EXCLUDE_BY_COLUMN -> try {
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

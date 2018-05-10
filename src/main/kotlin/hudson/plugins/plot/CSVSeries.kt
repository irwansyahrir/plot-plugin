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
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.HashSet
import java.util.logging.Level
import java.util.logging.Logger
import java.util.regex.Pattern
import net.sf.json.JSONObject
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.ArrayUtils
import org.apache.commons.lang.ObjectUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.StaplerRequest

/**
 * Represents a plot data series configuration from an CSV file.
 *
 * @author Allen Reese
 */

enum class InclusionFlag {
    OFF, INCLUDE_BY_STRING, EXCLUDE_BY_STRING, INCLUDE_BY_COLUMN, EXCLUDE_BY_COLUMN
}

class CSVSeries : Series {

    companion object {
        @Transient
        private val LOGGER = Logger.getLogger(CSVSeries::class.java.name)

        // Debugging hack, so I don't have to change FINE/INFO...
        @Transient
        private val DEFAULT_LOG_LEVEL = Level.FINEST

        @Transient
        private val PATTERN_COMMA = Pattern.compile(",")
    }


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


    @DataBoundConstructor constructor(file: String, url: String?, inclusionFlag: String?, exclusionValues: String?, displayTableFlag: Boolean) : super(file, "", "csv") {
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
                            buildNumber: Int, logger: PrintStream): List<PlotPoint> {
        var reader: CSVReader? = null
        var `in`: InputStream? = null
        var inputReader: InputStreamReader? = null

        try {
            val ret = ArrayList<PlotPoint>()

            val seriesFiles: Array<FilePath>
            try {
                seriesFiles = workspaceRootDir.list(file)
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Exception trying to retrieve series files", e)
                return emptyList()
            }

            if (ArrayUtils.isEmpty(seriesFiles)) {
                LOGGER.info("No plot data file found: " + workspaceRootDir.name
                        + " " + file)
                return emptyList()
            }

            try {
                if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                    LOGGER.log(DEFAULT_LOG_LEVEL, "Loading plot series data from: $file")
                }

                `in` = seriesFiles[0].read()
            } catch (e: Exception) {
                LOGGER.log(Level.SEVERE, "Exception reading plot series data from " + seriesFiles[0], e)
                return emptyList()
            }

            if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                LOGGER.log(DEFAULT_LOG_LEVEL, "Loaded CSV Plot file: $file")
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
                        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                            LOGGER.log(DEFAULT_LOG_LEVEL, "CSV Point: [" + index
                                    + ":" + lineNum + "]" + point)
                        }
                        ret.add(point)
                    } else {
                        if (LOGGER.isLoggable(DEFAULT_LOG_LEVEL)) {
                            LOGGER.log(DEFAULT_LOG_LEVEL, "excluded CSV Column: "
                                    + index + " : " + label)
                        }
                    }
                }
                lineNum++
                nextLine = reader.readNext()
            }

            return ret
        } catch (ioe: IOException) {
            LOGGER.log(Level.SEVERE, "Exception loading series", ioe)
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    LOGGER.log(Level.SEVERE, "Failed to close series reader", e)
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

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest((if (retVal) "excluded" else "included")
                    + " CSV Column: " + index + " : " + label)
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
            else -> LOGGER.log(Level.SEVERE, "Failed to initialize columns exclusions set.")
        }

        for (str in PATTERN_COMMA.split(exclusionValues)) {
            if (str == null || str.length <= 0) {
                continue
            }

            when (inclusionFlag) {
                InclusionFlag.INCLUDE_BY_STRING, InclusionFlag.EXCLUDE_BY_STRING -> {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(inclusionFlag.toString() + " CSV Column: " + str)
                    }
                    strExclusionSet!!.add(str)
                }
                InclusionFlag.INCLUDE_BY_COLUMN, InclusionFlag.EXCLUDE_BY_COLUMN -> try {
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.finest(inclusionFlag.toString() + " CSV Column: " + str)
                    }
                    colExclusionSet!!.add(Integer.valueOf(str))
                } catch (nfe: NumberFormatException) {
                    LOGGER.log(Level.SEVERE, "Exception converting to integer", nfe)
                }

                else -> LOGGER.log(Level.SEVERE, "Failed to identify columns exclusions.")
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

package hudson.plugins.plot

import au.com.bytecode.opencsv.CSVReader
import hudson.model.AbstractProject
import hudson.model.Job
import org.apache.commons.collections.CollectionUtils
import org.apache.commons.lang.StringUtils
import org.kohsuke.stapler.StaplerRequest
import org.kohsuke.stapler.StaplerResponse
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PlotReport(// called from PlotReport/index.jelly
        val job: Job<*, *>,
        /**
         * The group these plots belong to.
         */
        // called from PlotReport/index.jelly
        val group: String,
        /**
         * The sorted list of plots that belong to the same group.
         */
        // called from PlotReport/index.jelly
        val plots: List<Plot>) {

    val project: AbstractProject<*, *>?
        @Deprecated("")
        get() = job as? AbstractProject<*, *>

    constructor(project: AbstractProject<*, *>, group: String,
                plots: List<Plot>) : this(project as Job<*, *>, group, plots) {
    }

    init {
        Collections.sort(plots)
    }

    // called from PlotReport/index.jelly
    fun doGetPlot(req: StaplerRequest, rsp: StaplerResponse) {
        val i = req.getParameter("index")
        val plot = getPlot(i)
        try {
            plot!!.plotGraph(req, rsp)
        } catch (ioe: IOException) {
            LOGGER.log(Level.SEVERE, "Exception plotting graph", ioe)
        }

    }

    // called from PlotReport/index.jelly
    fun doGetPlotMap(req: StaplerRequest, rsp: StaplerResponse) {
        val i = req.getParameter("index")
        val plot = getPlot(i)
        try {
            plot!!.plotGraphMap(req, rsp)
        } catch (ioe: IOException) {
            LOGGER.log(Level.SEVERE, "Exception plotting graph", ioe)
        }

    }

    // called from PlotReport/index.jelly
    fun getDisplayTableFlag(i: Int): Boolean {
        val plot = getPlot(i)

        if (CollectionUtils.isNotEmpty(plot.getSeries())) {
            val series = plot.getSeries()[0]
            return series is CSVSeries && series.displayTableFlag
        }
        return false
    }

    // called from PlotReport/index.jelly
    fun getTable(i: Int): List<List<String>> {
        val tableData = ArrayList<List<String>>()

        val plot = getPlot(i)

        // load existing csv file
        val plotFile = File(job.getRootDir(), plot.getCsvFileName())
        if (!plotFile.exists()) {
            return tableData
        }
        var reader: CSVReader? = null
        try {
            reader = CSVReader(InputStreamReader(FileInputStream(plotFile),
                    Charset.defaultCharset().name()))
            // throw away 2 header lines
            reader.readNext()
            reader.readNext()
            // array containing header titles
            val header = ArrayList<String>()
            header.add(Messages.Plot_Build() + " #")
            tableData.add(header)
            var nextLine: Array<out String?> = reader.readNext()
            while (nextLine != null) {
                val buildNumber = nextLine[2]
                if (!plot.reportBuild(Integer.parseInt(buildNumber))) {
                    continue
                }
                val seriesLabel = nextLine[1]
                // index of the column where the value should be located
                var index = header.lastIndexOf(seriesLabel)
                if (index <= 0) {
                    // add header label
                    index = header.size
                    header.add(seriesLabel!!)
                }
                var tableRow: MutableList<String>? = null
                for (j in 1 until tableData.size) {
                    val r = tableData[j]
                    if (StringUtils.equals(r[0], buildNumber)) {
                        // found table row corresponding to the build number
                        tableRow = r
                        break
                    }
                }
                // table row corresponding to the build number not found
                if (tableRow == null) {
                    // create table row with build number at first column
                    tableRow = ArrayList()
                    tableRow.add(buildNumber!!)
                    tableData.add(tableRow)
                }
                // set value at index column
                val value = nextLine[0]
                if (index < tableRow.size) {
                    tableRow[index] = value!!
                } else {
                    for (j in tableRow.size until index) {
                        tableRow.add(StringUtils.EMPTY)
                    }
                    tableRow.add(value!!)
                }

                nextLine = reader.readNext()
            }
            val lastColumn = tableData[0].size
            for (tableRow in tableData) {
                for (j in tableRow.size until lastColumn) {
                    tableRow.add(StringUtils.EMPTY)
                }
            }
        } catch (ioe: IOException) {
            LOGGER.log(Level.SEVERE, "Exception reading csv file", ioe)
        } finally {
            if (reader != null) {
                try {
                    reader.close()
                } catch (e: IOException) {
                    LOGGER.log(Level.INFO, "Failed to close CSV reader", e)
                }

            }
        }
        return tableData
    }

    private fun getPlot(i: Int): Plot {
        val p = plots[i]
        p.job = job
        return p
    }

    private fun getPlot(i: String): Plot? {
        try {
            return getPlot(Integer.parseInt(i))
        } catch (ignore: NumberFormatException) {
            LOGGER.log(Level.SEVERE, "Exception converting to integer", ignore)
            return null
        }

    }

    companion object {
        private val LOGGER = Logger.getLogger(PlotReport::class.java.name)
    }
}
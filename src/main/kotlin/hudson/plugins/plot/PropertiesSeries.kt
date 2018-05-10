/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.model.Descriptor
import java.io.InputStream
import java.io.PrintStream
import java.util.ArrayList
import java.util.Properties
import java.util.logging.Level
import java.util.logging.Logger
import net.sf.json.JSONObject
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.ArrayUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.StaplerRequest

/**
 * @author Allen Reese
 */
class PropertiesSeries : Series {

    @DataBoundConstructor constructor(file: String, label: String?) : super(file, label, "properties")

    /**
     * Load the series from a properties file.
     */
    override fun loadSeries(workspaceRootDir: FilePath, buildNumber: Int,
                            logger: PrintStream): List<PlotPoint> {
        var `in`: InputStream? = null
        val seriesFiles: Array<FilePath>

        try {
            seriesFiles = workspaceRootDir.list(file)
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE,
                    "Exception trying to retrieve series files", e)
            return emptyList()
        }

        if (ArrayUtils.isEmpty(seriesFiles)) {
            logger.println("No plot data file found: $file")
            return emptyList()
        }

        try {
            `in` = seriesFiles[0].read()
            logger.println("Saving plot series data from: " + seriesFiles[0])
            val properties = Properties()
            properties.load(`in`)
            val yvalue = properties.getProperty("YVALUE")
            val url = properties.getProperty("URL", "")
            if (yvalue == null || url == null) {
                logger.println("Not creating point with null values: y="
                        + yvalue + " label=" + label + " url=" + url)
                return emptyList()
            }
            val series = ArrayList<PlotPoint>()
            series.add(PlotPoint(yvalue, url, label))
            return series
        } catch (e: Exception) {
            LOGGER.log(Level.SEVERE, "Exception reading plot series data from " + seriesFiles[0], e)
            return emptyList()
        } finally {
            IOUtils.closeQuietly(`in`)
        }
    }

    override fun getDescriptor(): Descriptor<Series> {
        return PropertiesSeries.DescriptorImpl()
    }

    @Extension
    class DescriptorImpl : Descriptor<Series>() {
        override fun getDisplayName(): String {
            return Messages.Plot_PropertiesSeries()
        }

        @Throws(Descriptor.FormException::class)
        override fun newInstance(req: StaplerRequest, formData: JSONObject): Series? {
            return SeriesFactory.createSeries(formData, req)
        }
    }

    companion object {
        @Transient
        private val LOGGER = Logger.getLogger(PropertiesSeries::class.java.name)
    }
}

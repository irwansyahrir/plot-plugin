/*
 * Copyright (c) 2008-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */

package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.model.Descriptor
import mu.KotlinLogging
import net.sf.json.JSONObject
import org.apache.commons.io.IOUtils
import org.kohsuke.stapler.DataBoundConstructor
import org.kohsuke.stapler.StaplerRequest
import java.io.PrintStream
import java.util.*

/**
 * @author Allen Reese
 */

private val logger = KotlinLogging.logger{}

class PropertiesSeries : Series {

    @DataBoundConstructor
    constructor(file: String, label: String?) : super(file, label, "properties")

    /**
     * Load the series from a properties file.
     */
    override fun loadSeries(filePath: FilePath, buildNumber: Int, printStream: PrintStream): List<PlotPoint> {

        val seriesFiles: Array<FilePath>
        try {
            seriesFiles = filePath.list(file)
        } catch (e: Exception) {
            logger.error(e) { "$e when trying to retrieve series files" }
            return emptyList()
        }

        if (seriesFiles.isEmpty()) {
            printStream.println("No plot data file found: $file")
            return emptyList()
        }

        return try {
            val properties = getProperties(seriesFiles, printStream)
            val yValue = properties.getProperty("YVALUE")
            val url = properties.getProperty("URL", "")
            when {
                yValue == null || url == null -> {
                    printStream.println("Not creating point with null values: y=$yValue label=$label url=$url")
                    emptyList()
                }
                else -> listOf(PlotPoint(yValue, url, label))
            }
        } catch (e: Exception) {
            logger.error(e) { "$e when reading plot series data from ${seriesFiles[0]}" }
            emptyList()
        }
    }

    private fun getProperties(seriesFiles: Array<FilePath>, logger: PrintStream): Properties {
        logger.println("Saving plot series data from: " + seriesFiles[0])
        val inputStream = seriesFiles[0].read()
        val properties = Properties()
        properties.load(inputStream)

        IOUtils.closeQuietly(inputStream)

        return properties
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
        override fun newInstance(req: StaplerRequest?, formData: JSONObject): Series? {
            return SeriesFactory.createSeries(formData, req)
        }
    }
}

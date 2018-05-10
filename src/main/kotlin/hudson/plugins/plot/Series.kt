/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.model.AbstractDescribableImpl
import hudson.model.Descriptor
import java.io.PrintStream
import java.util.regex.Pattern
import net.sf.json.JSONObject
import org.kohsuke.stapler.StaplerRequest

/**
 * Represents a plot data series configuration.
 *
 * @author Nigel Daley
 * @author Allen Reese
 */
abstract class Series : AbstractDescribableImpl<Series> {

    protected constructor(file: String, label: String?, fileType: String) : super() {
        this.file = file
        this.label = label ?: Messages.Plot_Missing()
        this.fileType = fileType
    }

    companion object {
        @Transient
        private val NAME_PATTERN = Pattern.compile("%name%")
        @Transient
        private val INDEX_PATTERN = Pattern.compile("%index%")
        private val BUILD_NUMBER_PATTERN = Pattern.compile("%build%")
    }

    /**
     * Relative path to the data series property file. Mandatory.
     */
    var file: String
        protected set

    /**
     * Data series legend label. Optional.
     */
    var label: String = ""
        protected set

    /**
     * Data series type. Mandatory. This can be csv, xml, or properties file.
     * This should be an enum, but I am not sure how to support that with
     * stapler at the moment
     */
    var fileType: String?
        protected set

    /**
     * Retrieves the plot data for one series after a build from the workspace.
     *
     * @param workspaceRootDir the root directory of the workspace
     * @param buildNumber      the build Number
     * @param logger           the logger to use
     * @return a PlotPoint array of points to plot
     */
    abstract fun loadSeries(workspaceRootDir: FilePath,
                            buildNumber: Int, logger: PrintStream): List<PlotPoint>

    // Convert data from before version 1.3
    private fun readResolve(): Any {
        return when (fileType) {
            null -> PropertiesSeries(file, label)
            else -> this
        }
    }

    /**
     * Return the url that should be used for this point.
     *
     * @param label       Name of the column
     * @param index       Index of the column
     * @param buildNumber The build number
     * @return url for the label.
     */
    protected fun getUrl(baseUrl: String?, label: String?, index: Int, buildNumber: Int): String? {
        var resultUrl: String? = baseUrl
        if (resultUrl != null) {
            resultUrl = replaceName(resultUrl, label)
            resultUrl = replaceIndex(resultUrl, index)
            resultUrl = replaceBuildNumber(resultUrl, buildNumber)
        }

        return resultUrl
    }

    private fun replaceBuildNumber(url: String?, buildNumber: Int): String? {
        val buildNumberMatcher = BUILD_NUMBER_PATTERN.matcher(url)
        if (buildNumberMatcher.find()) {
            return buildNumberMatcher.replaceAll(buildNumber.toString())
        }
        return url
    }

    private fun replaceIndex(url: String?, index: Int): String? {
        val indexMatcher = INDEX_PATTERN.matcher(url)
        if (indexMatcher.find()) {
            return indexMatcher.replaceAll(index.toString())
        }
        return url
    }

    private fun replaceName(url: String?, label: String?): String? {
        val nameMatcher = NAME_PATTERN.matcher(url)
        if (nameMatcher.find()) {
            return nameMatcher.replaceAll(label ?: "")
        }
        return url
    }

    override fun getDescriptor(): Descriptor<Series> {
        return DescriptorImpl()
    }

    @Extension
    class DescriptorImpl : Descriptor<Series>() {
        override fun getDisplayName(): String {
            return Messages.Plot_Series()
        }

        @Throws(Descriptor.FormException::class)
        override fun newInstance(req: StaplerRequest, formData: JSONObject): Series? {
            return SeriesFactory.createSeries(formData, req)
        }
    }
}

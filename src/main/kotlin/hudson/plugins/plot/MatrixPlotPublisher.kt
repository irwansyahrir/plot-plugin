package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.Launcher
import hudson.matrix.MatrixConfiguration
import hudson.matrix.MatrixProject
import hudson.matrix.MatrixRun
import hudson.model.AbstractBuild
import hudson.model.AbstractProject
import hudson.model.Action
import hudson.model.BuildListener
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.Publisher
import hudson.util.FormValidation
import net.sf.json.JSONObject
import org.apache.commons.lang.ObjectUtils
import org.kohsuke.stapler.AncestorInPath
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest
import java.io.IOException
import java.util.*

/**
 * @author lucinka
 */
class MatrixPlotPublisher : AbstractPlotPublisher() {

    companion object {
        @Extension
        val DESCRIPTOR = DescriptorImpl()
    }

    @Transient
    private var plotsOfConfigurations: MutableMap<MatrixConfiguration, MutableList<Plot>> = HashMap()

    @Transient
    private var groupMap: MutableMap<String, MutableList<Plot>> = HashMap()

    /**
     * Configured plots.
     */
    /**
     * Reset Configuration and set plots settings for matrixConfiguration
     *
     * @param plots the new list of plots
     */
    var plots: List<Plot> = ArrayList()
        set(plots) {
            field = plots
            groupMap = HashMap()
            plotsOfConfigurations = HashMap()
        }

    fun urlGroupToOriginalGroup(urlGroup: String?, config: MatrixConfiguration): String {
        if (urlGroup == null || "nogroup" == urlGroup) {
            return "Plots"
        }
        if (groupMap.containsKey(urlGroup)) {
            val plotList = mutableListOf<Plot>()
            for (plot in groupMap[urlGroup]!!) {
                if (ObjectUtils.equals(plot.project, config)) {
                    plotList.add(plot)
                }
            }
            if (plotList.size > 0) {
                return plotList[0].group
            }
        }
        return ""
    }

    /**
     * Returns all group names as the original user specified strings.
     */
    fun getOriginalGroups(configuration: MatrixConfiguration): List<String> {
        val originalGroups = mutableListOf<String>()
        for (urlGroup in groupMap.keys) {
            originalGroups.add(urlGroupToOriginalGroup(urlGroup, configuration))
        }
        originalGroups.sort()
        return originalGroups
    }

    /**
     * Adds the new plot to the plot data structures managed by this object.
     *
     * @param plot the new plot
     */
    fun addPlot(plot: Plot) {
        val urlGroup = convertToUrlEncodedGroup(plot.getGroup())

        when {
            groupMap.containsKey(urlGroup) -> {
                groupMap[urlGroup]!!.add(plot)
            }
            else -> groupMap[urlGroup] = mutableListOf(plot)
        }

        val config = plot.project as MatrixConfiguration
        when {
            plotsOfConfigurations[config] == null -> {
                plotsOfConfigurations[config] = mutableListOf(plot)
            }
            else -> plotsOfConfigurations[config]?.add(plot)
        }
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    fun getPlots(config: MatrixConfiguration): List<Plot> {
        return plotsOfConfigurations[config] ?: mutableListOf()
    }

    /**
     * Returns the list of plots with the given group name. The given group must
     * be the URL friendly form of the group name.
     */
    fun getPlots(urlGroup: String, config: MatrixConfiguration): List<Plot> {
        val groupPlots = ArrayList<Plot>()

        groupMap[urlGroup]?.forEach { plot ->
            if (ObjectUtils.equals(plot.project, config)) {
                groupPlots.add(plot)
            }
        }

        return groupPlots
    }

    /**
     * Called by Jenkins.
     */
    override fun getProjectAction(project: AbstractProject<*, *>?): Action? {
        return when (project) {
            is MatrixConfiguration -> MatrixPlotAction(project, this)
            else -> null
        }
    }

    override fun prebuild(build: AbstractBuild<*, *>?, listener: BuildListener): Boolean {
        if (!plotsOfConfigurations.containsKey(build!!.getProject() as MatrixConfiguration)) {
            for (p in this.plots) {
                val plot = Plot(p.title, p.yaxis, p.group, p.numBuilds,
                        p.csvFileName, p.style, p.useDescr, p.keepRecords,
                        p.getExclZero(), p.isLogarithmic, p.yaxisMinimum, p.yaxisMaximum)
                plot.series = p.series
                plot.project = build.getProject() as MatrixConfiguration
                addPlot(plot)
            }
        }
        return true
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun perform(build: AbstractBuild<*, *>, launcher: Launcher,
                         listener: BuildListener): Boolean {
        if (build !is MatrixRun) {
            return true
        }
        listener.logger.println("Recording plot data")

        // add the build to each plot
        for (plot in plotsOfConfigurations[build.project]!!) {
            plot.addBuild(build, listener.logger)
        }
        // misconfigured plots will not fail a build so always return true
        return true
    }

    /**
     * Setup the groupMap upon deserialization.
     */
    private fun readResolve(): Any {
        plots = this.plots
        return this
    }

    /**
     * Called by Jenkins.
     */
    override fun getDescriptor(): BuildStepDescriptor<Publisher> {
        return DESCRIPTOR
    }

    class DescriptorImpl : BuildStepDescriptor<Publisher>(MatrixPlotPublisher::class.java) {

        override fun getDisplayName(): String {
            return Messages.Plot_Publisher_DisplayName()
        }

        override fun isApplicable(jobType: Class<out AbstractProject<*, *>>): Boolean {
            return MatrixProject::class.java.isAssignableFrom(jobType)
        }

        /**
         * Called when the user saves the project configuration.
         */
        @Throws(hudson.model.Descriptor.FormException::class)
        override fun newInstance(request: StaplerRequest, formData: JSONObject): Publisher {
            val plots = mutableListOf<Plot>()
            SeriesFactory.getArray(formData.get("plots")).forEach { data ->
                plots.add(bindPlot(data as JSONObject, request))
            }

            val publisher = MatrixPlotPublisher()
            publisher.plots = plots
            return publisher
        }

        private fun bindPlot(data: JSONObject, req: StaplerRequest): Plot {
            val plot = req.bindJSON(Plot::class.java, data)
            plot.series = SeriesFactory.createSeriesList(data.get("series"), req)
            return plot
        }

        /**
         * Checks if the series file is valid.
         */
        @Throws(IOException::class)
        fun doCheckSeriesFile(@AncestorInPath project: AbstractProject<*, *>,
                              @QueryParameter value: String): FormValidation {
            return FilePath.validateFileMask(project.getSomeWorkspace(), value)
        }
    }
}

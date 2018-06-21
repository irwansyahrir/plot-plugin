/*
 * Copyright (c) 2007-2009 Yahoo! Inc.  All rights reserved.
 * The copyrights to the contents of this file are licensed under the MIT License
 * (http://www.opensource.org/licenses/mit-license.php)
 */
package hudson.plugins.plot

import hudson.Launcher
import hudson.model.AbstractBuild
import hudson.model.AbstractProject
import hudson.model.Action
import hudson.model.BuildListener
import hudson.model.Run
import hudson.model.TaskListener
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.BuildStepMonitor
import hudson.tasks.Publisher
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import org.apache.commons.collections.CollectionUtils

/**
 * Records the plot data for builds.
 *
 * @author Nigel Daley
 */
class PlotPublisher : AbstractPlotPublisher() {

    /**
     * Array of Plot objects that represent the job's configured plots; must be non-null
     */
    private var plots: MutableList<Plot> = ArrayList()
    /**
     * Maps plot groups to plot objects; group strings are in a URL friendly format;
     * map must be non-null
     */
    @Transient
    private var groupMap: MutableMap<String, MutableList<Plot>> = HashMap()

    /**
     * Returns all group names as the original user specified strings.
     */
    val originalGroups: List<String>
        get() {
            val originalGroups = ArrayList<String>()
            for (urlGroup in groupMap.keys) {
                originalGroups.add(urlGroupToOriginalGroup(urlGroup))
            }
            Collections.sort(originalGroups)
            return originalGroups
        }

    /**
     * Setup the groupMap upon deserialization.
     */
    private fun readResolve(): Any {
        setPlots(plots)
        return this
    }

    /**
     * Converts a URL friendly plot group name to the original group name.
     * If the given urlGroup doesn't already exist then the empty string will be returned.
     */
    fun urlGroupToOriginalGroup(urlGroup: String?): String {
        if (urlGroup == null || "nogroup" == urlGroup) {
            return "Plots"
        }
        if (groupMap.containsKey(urlGroup)) {
            val plotList = groupMap[urlGroup]
            if (CollectionUtils.isNotEmpty(plotList)) {
                return plotList!!.get(0).group
            }
        }
        return ""
    }

    /**
     * Replaces the plots managed by this object with the given list.
     *
     * @param plots the new list of plots
     */
    fun setPlots(plots: List<Plot>) {
        this.plots = ArrayList()
        groupMap = HashMap()
        for (plot in plots) {
            addPlot(plot)
        }
    }

    /**
     * Adds the new plot to the plot data structures managed by this object.
     *
     * @param plot the new plot
     */
    fun addPlot(plot: Plot) {
        // update the plot list
        plots.add(plot)
        // update the group-to-plot map
        val urlGroup = convertToUrlEncodedGroup(plot.getGroup())
        if (groupMap.containsKey(urlGroup)) {
            val list = groupMap[urlGroup]
            list!!.add(plot)
        } else {
            val list = ArrayList<Plot>()
            list.add(plot)
            groupMap[urlGroup] = list
        }
    }

    /**
     * Called by Jenkins.
     */
    override fun getProjectAction(project: AbstractProject<*, *>?): Action {
        return PlotAction(project, this)
    }

    /**
     * Returns the entire list of plots managed by this object.
     */
    fun getPlots(): List<Plot> {
        return plots
    }

    /**
     * Returns the list of plots with the given group name. The given group must
     * be the URL friendly form of the group name.
     */
    fun getPlots(urlGroup: String): List<Plot> {
        val p = groupMap[urlGroup]
        return p ?: ArrayList()
    }

    /**
     * Called by Jenkins when a build is finishing.
     */
    @Throws(IOException::class, InterruptedException::class)
    override fun perform(build: AbstractBuild<*, *>, launcher: Launcher,
                         listener: BuildListener): Boolean {
        recordPlotData(build, listener)
        // misconfigured plots will not fail a build so always return true
        return true
    }

    private fun recordPlotData(build: Run<*, *>, listener: TaskListener) {
        listener.logger.println("Recording plot data")
        // add the build to each plot
        for (plot in getPlots()) {
            plot.addBuild(build as AbstractBuild<*, *>, listener.logger)
        }
    }

    override fun getRequiredMonitorService(): BuildStepMonitor {
        return BuildStepMonitor.BUILD
    }

    /**
     * Called by Jenkins.
     */
    override fun getDescriptor(): BuildStepDescriptor<Publisher> {
        return DESCRIPTOR
    }

    companion object {

        val DESCRIPTOR = PlotDescriptor()
    }
}

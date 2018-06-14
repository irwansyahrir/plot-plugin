package hudson.plugins.plot

import hudson.matrix.MatrixConfiguration
import hudson.model.AbstractProject
import hudson.model.Action
import java.io.IOException
import org.apache.commons.collections.CollectionUtils
import org.kohsuke.stapler.StaplerProxy

/**
 * @author lucinka
 */
class MatrixPlotAction(private val project: MatrixConfiguration, private val publisher: MatrixPlotPublisher) : Action, StaplerProxy {

    val originalGroups: List<String>
        get() = publisher.getOriginalGroups(project)

    fun getProject(): AbstractProject<*, *> {
        return project
    }

    @Throws(IOException::class)
    fun hasPlots(): Boolean {
        return CollectionUtils.isNotEmpty(publisher.getPlots(project))
    }

    fun getUrlGroup(originalGroup: String): String {
        return publisher.originalGroupToUrlEncodedGroup(originalGroup)
    }

    @Throws(IOException::class)
    fun getDynamic(group: String): PlotReport {
        return PlotReport(project,
                publisher.urlGroupToOriginalGroup(getUrlGroup(group), project),
                publisher.getPlots(getUrlGroup(group), project))
    }

    /**
     * If there's only one plot category, simply display that category of
     * reports on this view.
     */
    override fun getTarget(): Any {
        val groups = originalGroups
        return if (groups != null && groups.size == 1) {
            PlotReport(project, groups[0],
                    publisher.getPlots(getUrlGroup(groups[0]), project))
        } else {
            this
        }
    }

    override fun getDisplayName(): String {
        return Messages.Plot_Action_DisplayName()
    }

    override fun getIconFileName(): String {
        return "graph.gif"
    }

    override fun getUrlName(): String {
        return Messages.Plot_UrlName()
    }
}

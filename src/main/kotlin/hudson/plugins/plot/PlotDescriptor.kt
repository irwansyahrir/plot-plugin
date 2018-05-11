package hudson.plugins.plot

import hudson.Extension
import hudson.FilePath
import hudson.matrix.MatrixProject
import hudson.model.AbstractProject
import hudson.tasks.BuildStepDescriptor
import hudson.tasks.Publisher
import hudson.util.FormValidation
import net.sf.json.JSONObject
import org.kohsuke.stapler.AncestorInPath
import org.kohsuke.stapler.QueryParameter
import org.kohsuke.stapler.StaplerRequest
import java.io.IOException

/**
 * The Descriptor for the plot configuration Extension
 *
 * @author Nigel Daley
 * @author Thomas Fox
 */
@Extension
class PlotDescriptor : BuildStepDescriptor<Publisher>(PlotPublisher::class.java) {

    override fun getDisplayName(): String {
        return Messages.Plot_Publisher_DisplayName()
    }

    override fun isApplicable(jobType: Class<out AbstractProject<*, *>>): Boolean {
        return AbstractProject::class.java.isAssignableFrom(jobType)
                && !MatrixProject::class.java.isAssignableFrom(jobType)
    }

    /**
     * Called when the user saves the project configuration.
     */
    @Throws(hudson.model.Descriptor.FormException::class)
    override fun newInstance(req: StaplerRequest, formData: JSONObject): Publisher {
        val publisher = PlotPublisher()
        for (data in SeriesFactory.getArray(formData.get("plots"))) {
            publisher.addPlot(bindPlot(data as JSONObject, req))
        }
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
    fun doCheckSeriesFile(@AncestorInPath project: AbstractProject<*, *>?,
            @QueryParameter value: String): FormValidation {

        // we don't have a workspace while in Pipeline editor
        return when {
            project?.getRootDir() == null -> FormValidation.ok()
            else -> FilePath.validateFileMask(project.getSomeWorkspace(), value)
        }
    }
}

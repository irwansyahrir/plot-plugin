package hudson.plugins.plot

import hudson.Util
import hudson.tasks.BuildStepMonitor
import hudson.tasks.Recorder
import org.apache.commons.lang.StringUtils.isEmpty

/**
 * @author lucinka
 */
open class AbstractPlotPublisher : Recorder() {

    /**
     * Converts the original plot group name to a URL friendly group name.
     */
    fun convertToUrlEncodedGroup(originalGroup: String): String {
        return Util.rawEncode(originalGroupToUrlGroup(originalGroup))
    }

    private fun originalGroupToUrlGroup(originalGroup: String): String {
        return when {
            isEmpty(originalGroup) -> "nogroup"
            else -> originalGroup.replace('/', ' ')
        }
    }

    override fun getRequiredMonitorService(): BuildStepMonitor {
        return BuildStepMonitor.BUILD
    }
}

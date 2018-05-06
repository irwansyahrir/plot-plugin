/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.plot

import hudson.Launcher
import hudson.matrix.AxisList
import hudson.matrix.MatrixConfiguration
import hudson.matrix.MatrixProject
import hudson.matrix.TextAxis
import hudson.model.AbstractBuild
import hudson.model.AbstractProject
import hudson.model.BuildListener
import hudson.model.FreeStyleProject
import hudson.tasks.Builder
import hudson.tasks.LogRotator
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.JenkinsRule
import java.io.IOException
import java.util.*

class KtPlotTest {

    @Rule
    var j = JenkinsRule()

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForOldBuilds() {
        val p = jobArchivingBuilds(1)

        plotBuilds(p, "2", false)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1) // Truncated to 1

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1) // Still 1
    }

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForDeletedBuilds() {
        val p = jobArchivingBuilds(10)

        plotBuilds(p, "", false)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 2)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 3)

        p.lastBuild!!.delete()
        assertSampleCount(p, 2) // Data should be removed with the build
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForOldBuilds() {
        val p = jobArchivingBuilds(1)

        plotBuilds(p, "2", true)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 2)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 2) // Plot 2 builds

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 2) // Still 2
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForDeletedBuilds() {
        val p = jobArchivingBuilds(10)

        plotBuilds(p, "", true)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 2)

        j.buildAndAssertSuccess(p)
        assertSampleCount(p, 3)

        p.lastBuild!!.delete()
        assertSampleCount(p, 3) // Data should be kept
    }

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForDeletedMatrixBuilds() {
        val p = matrixJobArchivingBuilds(10)
        p.axes = AxisList(TextAxis("a", "a"))

        val c = p.getItem("a=a")

        plotMatrixBuilds(p, "10", false)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 2)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 3)

        c!!.lastBuild!!.delete()
        assertSampleCount(c, 2) // Data should be removed
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForDeletedMatrixBuilds() {
        val p = matrixJobArchivingBuilds(10)
        p.axes = AxisList(TextAxis("a", "a"))

        val c = p.getItem("a=a")

        plotMatrixBuilds(p, "10", true)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 1)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 2)

        j.buildAndAssertSuccess(p)
        assertSampleCount(c, 3)

        c!!.lastBuild!!.delete()
        assertSampleCount(c, 3) // Data should be kept
        p.lastBuild!!.delete()
        assertSampleCount(c, 3) // Data should be kept
    }

    @Throws(Exception::class)
    private fun jobArchivingBuilds(count: Int): FreeStyleProject {
        val p = j.createFreeStyleProject()
        p.buildersList.add(PlotBuildNumber())
        p.buildDiscarder = LogRotator(-1, count, -1, -1)

        return p
    }

    @Throws(Exception::class)
    private fun matrixJobArchivingBuilds(count: Int): MatrixProject {
        val p = j.createProject(MatrixProject::class.java)

        p.buildersList.add(PlotBuildNumber())
        p.buildDiscarder = LogRotator(-1, count, -1, -1)

        return p
    }

    private fun plotBuilds(p: AbstractProject<*, *>, count: String, keepRecords: Boolean) {
        val publisher = PlotPublisher()
        val plot = Plot("Title", "Number", "default", count, null,
                "line", false, keepRecords, false, false, null, null)
        p.getPublishersList().add(publisher)
        publisher.addPlot(plot)
        plot.series = Arrays.asList<Series>(PropertiesSeries("src.properties", null))
    }

    private fun plotMatrixBuilds(p: AbstractProject<*, *>, count: String, keepRecords: Boolean) {
        val publisher = MatrixPlotPublisher()
        val plot = Plot("Title", "Number", "default", count, null,
                "line", false, keepRecords, false, false, null, null)
        p.getPublishersList().add(publisher)
        publisher.plots = Arrays.asList(plot)
        plot.series = Arrays.asList<Series>(PropertiesSeries("src.properties", null))
    }

    @Throws(Exception::class)
    private fun assertSampleCount(p: AbstractProject<*, *>?, count: Int) {
        val pr = if (p is MatrixConfiguration)
            p.getAction(MatrixPlotAction::class.java).getDynamic("default")
        else
            p!!.getAction(PlotAction::class.java).getDynamic("default", null, null)
        val table = pr.getTable(0)
        assertEquals("Plot sample count", count.toLong(), (table.size - 1).toLong())
    }

    private class PlotBuildNumber : Builder() {

        @Throws(InterruptedException::class, IOException::class)
        override fun perform(build: AbstractBuild<*, *>, launcher: Launcher,
                             listener: BuildListener): Boolean {
            build.getWorkspace()!!
                    .child("src.properties")
                    .write(String.format("YVALUE=%d", build.getNumber()), "UTF-8")
            return true
        }
    }
}

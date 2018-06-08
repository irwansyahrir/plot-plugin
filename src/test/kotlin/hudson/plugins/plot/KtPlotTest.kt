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

    @get:Rule
    var jenkinsRule = JenkinsRule()

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForOldBuilds() {
        val project = jobArchivingBuilds(1)

        plotBuilds(project, "2", false)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1) // Truncated to 1

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1) // Still 1
    }

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForDeletedBuilds() {
        val project = jobArchivingBuilds(10)

        plotBuilds(project, "", false)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 2)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 3)

        project.lastBuild!!.delete()
        assertSampleCount(project, 2) // Data should be removed with the build
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForOldBuilds() {
        val project = jobArchivingBuilds(1)

        plotBuilds(project, "2", true)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 2)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 2) // Plot 2 builds

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 2) // Still 2
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForDeletedBuilds() {
        val project = jobArchivingBuilds(10)

        plotBuilds(project, "", true)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 1)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 2)

        jenkinsRule.buildAndAssertSuccess(project)
        assertSampleCount(project, 3)

        project.lastBuild!!.delete()
        assertSampleCount(project, 3) // Data should be kept
    }

    @Test
    @Throws(Exception::class)
    fun discardPlotSamplesForDeletedMatrixBuilds() {
        val matrixProject = matrixJobArchivingBuilds(10)
        matrixProject.axes = AxisList(TextAxis("a", "a"))

        val c = matrixProject.getItem("a=a")

        plotMatrixBuilds(matrixProject, "10", false)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 1)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 2)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 3)

        c!!.lastBuild!!.delete()
        assertSampleCount(c, 2) // Data should be removed
    }

    @Test
    @Throws(Exception::class)
    fun keepPlotSamplesForDeletedMatrixBuilds() {
        val matrixProject = matrixJobArchivingBuilds(10)
        matrixProject.axes = AxisList(TextAxis("a", "a"))

        val c = matrixProject.getItem("a=a")

        plotMatrixBuilds(matrixProject, "10", true)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 1)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 2)

        jenkinsRule.buildAndAssertSuccess(matrixProject)
        assertSampleCount(c, 3)

        c!!.lastBuild!!.delete()
        assertSampleCount(c, 3) // Data should be kept
        matrixProject.lastBuild!!.delete()
        assertSampleCount(c, 3) // Data should be kept
    }

    @Throws(Exception::class)
    private fun jobArchivingBuilds(count: Int): FreeStyleProject {
        val project = jenkinsRule.createFreeStyleProject()
        project.buildersList.add(PlotBuildNumber())
        project.buildDiscarder = LogRotator(-1, count, -1, -1)

        return project
    }

    @Throws(Exception::class)
    private fun matrixJobArchivingBuilds(count: Int): MatrixProject {
        val matrixProject = jenkinsRule.createProject(MatrixProject::class.java)

        matrixProject.buildersList.add(PlotBuildNumber())
        matrixProject.buildDiscarder = LogRotator(-1, count, -1, -1)

        return matrixProject
    }

    private fun plotBuilds(project: AbstractProject<*, *>, count: String, keepRecords: Boolean) {
        val publisher = PlotPublisher()
        val plot = Plot("Title", "Number", "default", count, null,
                "line", false, keepRecords, false, false, null, null)
        project.getPublishersList().add(publisher)
        publisher.addPlot(plot)
        plot.series = Arrays.asList<Series>(PropertiesSeries("src.properties", null))
    }

    private fun plotMatrixBuilds(project: AbstractProject<*, *>, count: String, keepRecords: Boolean) {
        val publisher = MatrixPlotPublisher()
        val plot = Plot("Title", "Number", "default", count, null,
                "line", false, keepRecords, false, false, null, null)
        project.getPublishersList().add(publisher)
        publisher.plots = Arrays.asList(plot)
        plot.series = Arrays.asList<Series>(PropertiesSeries("src.properties", null))
    }

    @Throws(Exception::class)
    private fun assertSampleCount(project: AbstractProject<*, *>?, count: Int) {
        val plotReport = when (project) {
            is MatrixConfiguration -> project.getAction(MatrixPlotAction::class.java).getDynamic("default")
            else -> project!!.getAction(PlotAction::class.java).getDynamic("default", null, null)
        }
        val table = plotReport.getTable(0)
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

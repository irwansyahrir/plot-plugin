package hudson.plugins.plot

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.jvnet.hudson.test.Issue
import org.jvnet.hudson.test.JenkinsRule
import java.util.*
import java.util.concurrent.*

class KtPlotBuildActionTest {

    @get:Rule
    var r = JenkinsRule()
    private var plotBuildAction: PlotBuildAction? = null

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val run = r.buildAndAssertSuccess(r.createFreeStyleProject())
        val plots = ArrayList<Plot>()
        for (i in 0..29) {
            val p = Plot()
            p.title = (i).toString()
            plots.add(p)
        }
        plotBuildAction = PlotBuildAction(run, plots)
    }

    @Issue("JENKINS-48465")
    @Test
    @Throws(Exception::class)
    fun checksNoConcurrentModificationExceptionIsThrownForPlotsListAccess() {
        val tasksCount = 10
        val executorService = Executors.newFixedThreadPool(2)
        val tasks = ArrayList<FutureTask<Any>>()
        val latch = CountDownLatch(tasksCount)

        simulateConcurrentModificationException(executorService, tasksCount, tasks, latch)

        waitForAllThreadsToFinish(executorService, latch)
        assertNoConcurrentModificationExceptionThrown(tasks)
    }

    private fun simulateConcurrentModificationException(executorService: ExecutorService,
                                                        tasksCount: Int, tasks: MutableList<FutureTask<Any>>, latch: CountDownLatch) {
        for (i in 0 until tasksCount) {
            val task = FutureTask(object : Callable<Any> {
                @Throws(Exception::class)
                override fun call(): Any? {
                    try {
                        Thread.sleep(Random().nextInt(100).toLong())
                        // using PureJavaReflectionProvider just because it's used in Jenkins
                        // close to "real world"
                        val provider = PureJavaReflectionProvider()
                        provider.visitSerializableFields(plotBuildAction!!
                        ) { fieldName, fieldType, definedIn, value ->
                            if (value != null && value is List<*>) {
                                val plots = value as MutableList<*>?
                                // simulate ConcurrentModificationException
                                for (p in plots!!) {
                                    if (plots!!.size > 0) {
                                        plots!!.remove(p)  //MutableList can remote item
                                    }
                                }
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                    return null
                }
            })
            tasks.add(task)
            executorService.submit(task)
        }
    }

    private fun waitForAllThreadsToFinish(executorService: ExecutorService, latch: CountDownLatch) {
        try {
            latch.await()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        executorService.shutdown()
    }

    @Throws(InterruptedException::class)
    private fun assertNoConcurrentModificationExceptionThrown(tasks: List<FutureTask<Any>>) {
        try {
            // we expect here no ConcurrentModificationException
            // otherwise access to plots list is not synchronized
            for (task in tasks) {
                task.get()
            }
        } catch (e: ExecutionException) {
            fail("Access to PlotBuildAction#plots list is not synchronized")
        } catch (e: ConcurrentModificationException) {
            fail("Access to PlotBuildAction#plots list is not synchronized")
        }

    }
}

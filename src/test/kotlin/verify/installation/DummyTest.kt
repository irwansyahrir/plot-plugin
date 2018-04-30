package verify.installation

import org.junit.Assert.assertEquals
import org.junit.Test

class DummyTest {

    @Test
    fun testHelloWorld(){
        val dummy = Dummy()
        assertEquals(dummy.helloWorld(), "Hello, World!")
    }

}
package verify.installation

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test

class DummyTest {

    @Test
    fun testHelloWorld(){
        val dummy = Dummy()
        assertEquals(dummy.helloWorld(), "Hello, World!")
    }

    @Test
    fun name() {
        assertThat("  ".trim()).isEqualTo("")
        assertThat("".isNullOrEmpty()).isTrue()
    }
}
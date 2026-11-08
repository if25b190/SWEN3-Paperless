package at.fhtw.swen3.paperless

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

@SpringBootTest
class WorkerApplicationTests {
    @Autowired
    lateinit var context: ApplicationContext

    @Test
    fun context_loads_ok() {
        // The full application context (JPA, AMQP topology, listener
        // configuration) starts without a live broker when the test profile
        // disables listener auto-startup and dynamic topology.
        assertThat(context).isNotNull()
    }
}

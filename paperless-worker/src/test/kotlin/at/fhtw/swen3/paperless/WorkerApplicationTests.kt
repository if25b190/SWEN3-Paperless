package at.fhtw.swen3.paperless

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

@SpringBootTest
class WorkerApplicationTests {
    @Autowired
    lateinit var context: ApplicationContext

    @Autowired
    lateinit var listeners: RabbitListenerEndpointRegistry

    @Test
    fun disabled_listener_does_not_start_ok() {
        // given
        val listener = requireNotNull(listeners.getListenerContainer("ocrWorker"))

        // then
        assertThat(listener.isAutoStartup).isFalse()
        assertThat(listener.isRunning).isFalse()
    }

    @Test
    fun services_do_not_depend_on_other_services_ok() {
        // given
        val dependencies = serviceDependencies()

        // then
        assertThat(dependencies).isNotEmpty()
        assertThat(dependencies).allSatisfy { service, injectedServices ->
            assertThat(injectedServices).describedAs(service).isEmpty()
        }
    }

    private fun serviceDependencies(): Map<String, List<String>> {
        val services = context.getBeansWithAnnotation(Service::class.java).values.map(AopUtils::getTargetClass)

        return services.associate { service ->
            service.simpleName to service.declaredConstructors.flatMap { constructor ->
                constructor.parameterTypes.filter { dependency -> services.any(dependency::isAssignableFrom) }
                    .map { it.simpleName }
            }
        }
    }
}

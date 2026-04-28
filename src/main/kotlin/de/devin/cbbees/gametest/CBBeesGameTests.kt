package de.devin.cbbees.gametest

import de.devin.cbbees.CreateBuzzyBeez
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.gametest.framework.TestFunction
import net.neoforged.neoforge.event.RegisterGameTestsEvent
import java.lang.reflect.Method
import java.util.function.Consumer

/**
 * Registers all cbbees game tests with NeoForge's GameTest framework.
 *
 * Manually constructs [TestFunction] objects with the `cbbees:` namespace prefix
 * so they pass NeoForge's namespace filter.
 */
object CBBeesGameTests {

    private val testHolders = arrayOf(
        de.devin.cbbees.gametest.flight.FlightPlanTests::class.java,
        de.devin.cbbees.gametest.inventory.BeeInventoryTests::class.java,
        de.devin.cbbees.gametest.deconstruction.DeconstructionTests::class.java,
        de.devin.cbbees.gametest.logistics.LogisticsPortTests::class.java,
        de.devin.cbbees.gametest.construction.ConstructionTests::class.java,
        de.devin.cbbees.gametest.transport.TransportTests::class.java,
        de.devin.cbbees.gametest.portable.PortableBeehiveTests::class.java,
        de.devin.cbbees.gametest.network.NetworkLinkingTests::class.java,
    )

    fun onRegisterGameTests(event: RegisterGameTestsEvent) {
        event.register(CBBeesGameTests::class.java)
    }

    @net.minecraft.gametest.framework.GameTestGenerator
    @JvmStatic
    fun generateTests(): Collection<TestFunction> {
        return testHolders.flatMap { holder ->
            holder.declaredMethods
                .filter { it.isAnnotationPresent(GameTest::class.java) }
                .map { toTestFunction(it) }
        }
    }

    private fun toTestFunction(method: Method): TestFunction {
        val annotation = method.getAnnotation(GameTest::class.java)
        val className = method.declaringClass.simpleName
        val testName = "${CreateBuzzyBeez.ID}:$className.${method.name}"
        val template = annotation.template.let {
            if (it.contains(":")) it else "${CreateBuzzyBeez.ID}:$it"
        }

        return TestFunction(
            annotation.batch,           // batchName
            testName,                   // testName
            template,                   // structureName
            net.minecraft.world.level.block.Rotation.NONE, // rotation
            annotation.timeoutTicks,    // maxTicks
            annotation.setupTicks.toLong(), // setupTicks
            annotation.required,        // required
            false,                      // manualOnly
            annotation.attempts,        // maxAttempts
            annotation.requiredSuccesses, // requiredSuccesses
            false,                      // skyAccess
            Consumer { helper: GameTestHelper ->
                method.invoke(null, helper)
            },
        )
    }
}

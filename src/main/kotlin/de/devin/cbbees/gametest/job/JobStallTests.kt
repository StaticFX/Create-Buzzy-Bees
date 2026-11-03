package de.devin.cbbees.gametest.job

import de.devin.cbbees.content.domain.job.BeeJob
import de.devin.cbbees.content.domain.job.JobType
import de.devin.cbbees.content.domain.job.Stall
import de.devin.cbbees.content.domain.job.StallReason
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import java.util.UUID

object JobStallTests {

    private const val TEMPLATE = "cbbees:gametest/empty3x3"

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    @JvmStatic
    fun stallReason_showsOnlyOnceItLasts(helper: GameTestHelper) {
        val job = BeeJob(UUID.randomUUID(), BlockPos.ZERO, helper.level, JobType.Construction)
        job.stall = Stall(StallReason.OUT_OF_RANGE)
        helper.assertTrue(job.visibleStall() == null, "A fresh reason should not show yet")

        helper.runAfterDelay(Stall.SHOW_AFTER_TICKS) {
            job.stall = Stall(StallReason.OUT_OF_RANGE)
            helper.assertTrue(job.visibleStall()?.reason == StallReason.OUT_OF_RANGE, "A lasting reason should show")
            job.stall = Stall(StallReason.NO_BEES)
            helper.assertTrue(job.visibleStall() == null, "A new reason starts its own wait")
            helper.succeed()
        }
    }
}

package de.devin.cbbees.datagen

import de.devin.cbbees.CreateBuzzyBeez
import net.neoforged.neoforge.data.event.GatherDataEvent

object CCRDatagen {

    fun gatherData(event: GatherDataEvent) {
        if (event.modContainer.modId != CreateBuzzyBeez.ID)
            return

        val generator = event.generator
        val output = generator.packOutput
        val lookupProvider = event.lookupProvider

        CreateBuzzyBeez.LOGGER.info("Gathering data...")

        val generatedEntriesProvider = GeneratedEntriesProvider(output, lookupProvider)
        generator.addProvider(event.includeServer(), generatedEntriesProvider)

        generator.addProvider(event.includeServer(), CBBSequencedAssemblyGen(output, lookupProvider, CreateBuzzyBeez.ID))
        generator.addProvider(event.includeServer(), CBBGlueingRecipeGen(output, lookupProvider))
        generator.addProvider(event.includeServer(), CBBFillingRecipeGen(output, lookupProvider))
    }
}
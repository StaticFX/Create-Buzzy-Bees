package de.devin.cbbees.content.schematics.client

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn

@OnlyIn(Dist.CLIENT)
object SharedSchematicClientCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cbbees").then(
                Commands.literal("shared").then(
                    Commands.argument("id", StringArgumentType.word())
                        .then(Commands.literal("open").executes { run(it, SharedSchematicChat::open) })
                        .then(Commands.literal("save").executes { run(it, SharedSchematicChat::save) })
                )
            )
        )
    }

    private fun run(ctx: CommandContext<CommandSourceStack>, action: (String) -> Boolean): Int {
        if (action(StringArgumentType.getString(ctx, "id"))) return 1
        ctx.source.sendFailure(Component.translatable("message.cbbees.share.missing"))
        return 0
    }
}

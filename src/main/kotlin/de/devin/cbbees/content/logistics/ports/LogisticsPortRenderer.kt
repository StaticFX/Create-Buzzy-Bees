package de.devin.cbbees.content.logistics.ports

import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider

class LogisticsPortRenderer(context: BlockEntityRendererProvider.Context) :
    SmartBlockEntityRenderer<LogisticPortBlockEntity>(context)

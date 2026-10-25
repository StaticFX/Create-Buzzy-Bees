package de.devin.cbbees.content.logistics.waggle.client

import com.simibubi.create.content.trains.station.NoShadowFontWrapper
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import de.devin.cbbees.icons.AllCCRGuiTextures
import de.devin.cbbees.network.SetWaggleAddressPacket
import net.createmod.catnip.gui.AbstractSimiScreen
import net.minecraft.ChatFormatting
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.EditBox
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

class WagglePortScreen(private val pos: BlockPos, private val address: String) :
    AbstractSimiScreen(Component.translatable("block.cbbees.waggle_port")) {

    private lateinit var addressBox: EditBox

    override fun init() {
        setWindowSize(BACKGROUND.width, BACKGROUND.height)
        super.init()
        addressBox = EditBox(NoShadowFontWrapper(font), guiLeft + 34, guiTop + 29, 128, 10, Component.empty()).apply {
            isBordered = false
            setMaxLength(MAX_ADDRESS_LENGTH)
            value = address
            setTextColor(0x555555)
            setHint(Component.translatable("gui.cbbees.waggle_port.address").withStyle(ChatFormatting.GRAY))
            isFocused = true
        }
        addRenderableWidget(addressBox)
        setInitialFocus(addressBox)
        addRenderableWidget(IconButton(guiLeft + 174, guiTop + 24, AllIcons.I_CONFIRM).withCallback<IconButton> { onClose() })
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        BACKGROUND.render(graphics, guiLeft, guiTop)
        graphics.drawCenteredString(font, title, guiLeft + BACKGROUND.width / 2, guiTop + 5, 0xFFD4AA00.toInt())
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onClose()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun removed() {
        PacketDistributor.sendToServer(SetWaggleAddressPacket(pos, addressBox.value.trim()))
    }

    override fun isPauseScreen() = false

    private companion object {
        val BACKGROUND = AllCCRGuiTextures.WAGGLE_PORT
        const val MAX_ADDRESS_LENGTH = 64
    }
}

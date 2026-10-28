package de.devin.cbbees.content.zapper.client

import com.simibubi.create.AllDataComponents
import com.simibubi.create.content.equipment.zapper.ConfigureZapperPacket
import com.simibubi.create.content.equipment.zapper.ZapperScreen
import com.simibubi.create.content.equipment.zapper.terrainzapper.ConfigureWorldshaperPacket
import com.simibubi.create.content.equipment.zapper.terrainzapper.PlacementOptions
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainBrushes
import com.simibubi.create.content.equipment.zapper.terrainzapper.TerrainTools
import com.simibubi.create.foundation.gui.AllGuiTextures
import com.simibubi.create.foundation.gui.AllIcons
import com.simibubi.create.foundation.gui.widget.IconButton
import com.simibubi.create.foundation.gui.widget.Indicator
import com.simibubi.create.foundation.gui.widget.Label
import com.simibubi.create.foundation.gui.widget.ScrollInput
import com.simibubi.create.foundation.gui.widget.SelectionScrollInput
import com.simibubi.create.foundation.utility.CreateLang
import de.devin.cbbees.content.zapper.shape.ShapeSettings
import de.devin.cbbees.content.zapper.shape.ZapperShape
import de.devin.cbbees.content.zapper.shape.ZapperShapes
import de.devin.cbbees.icons.AllCCRGuiTextures
import de.devin.cbbees.network.ConfigureZapperShapePacket
import net.createmod.catnip.lang.FontHelper
import de.devin.cbbees.registry.AllDataComponents as CBBeesDataComponents
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.neoforged.neoforge.network.PacketDistributor

class ZapperShapeScreen(zapper: ItemStack, hand: InteractionHand) :
    ZapperScreen(AllGuiTextures.TERRAINZAPPER, zapper, hand) {

    private val toolSection = CreateLang.translateDirect("gui.terrainzapper.tool")
    private val placementSection = CreateLang.translateDirect("gui.terrainzapper.placement")

    private val saved = zapper.getOrDefault(CBBeesDataComponents.ZAPPER_SHAPE.get(), ShapeSettings.DEFAULT)
    private var shape: ZapperShape = ZapperShapes.byId(saved.shape)
    private val values = IntArray(MAX_PARAMS) { saved.params.getOrNull(it) ?: shape.params.getOrNull(it)?.default ?: 1 }
    private var hollow = saved.hollow
    private var diagonals = saved.diagonals
    private var fuzzy = saved.fuzzy
    private var tool = zapper.getOrDefault(AllDataComponents.SHAPER_TOOL, TerrainTools.Fill)
    private var placement = zapper.getOrDefault(AllDataComponents.SHAPER_PLACEMENT_OPTIONS, PlacementOptions.Merged)

    private val shapeWidgets = mutableListOf<AbstractWidget>()
    private val preview = ZapperShapePreview()

    init {
        fontColor = 0x000000
        title = zapper.hoverName
    }

    override fun setWindowOffset(xOffset: Int, yOffset: Int) =
        super.setWindowOffset(xOffset + (PREVIEW_WIDTH + PREVIEW_GAP) / 2, yOffset)

    override fun init() {
        super.init()
        val label = Label(guiLeft + 61, guiTop + 25, CommonComponents.EMPTY).withShadow()
        val input = SelectionScrollInput(guiLeft + 56, guiTop + 20, 77, 18)
            .forOptions(ZapperShapes.ALL.map(ZapperShape::name))
            .titled(Component.translatable("gui.cbbees.zapper.shape"))
            .writingTo(label)
            .calling { index ->
                shape = ZapperShapes.ALL[index]
                initShapeWidgets()
            }
        input.setState(ZapperShapes.ALL.indexOf(shape))
        addRenderableWidget(label)
        addRenderableWidget(input)
        initShapeWidgets()
    }

    private fun initShapeWidgets() {
        removeWidgets(shapeWidgets)
        shapeWidgets.clear()
        val x = guiLeft
        val y = guiTop

        shape.params.forEachIndexed { index, param ->
            val label = Label(x + 65 + 20 * index, y + 45, CommonComponents.EMPTY).withShadow()
            val input = ScrollInput(x + 56 + 20 * index, y + 40, 18, 18)
                .withRange(param.min, param.max() + 1)
                .writingTo(label)
                .titled(param.label.plainCopy())
                .calling { state ->
                    values[index] = state
                    label.x = x + 65 + 20 * index - font.width(label.text) / 2
                }
            input.setState(values[index])
            input.onChanged()
            shapeWidgets += label
            shapeWidgets += input
        }

        if (shape.canBeHollow) {
            toggle(x + 136, y + 40, AllIcons.I_FX_SURFACE_ON, Component.translatable("gui.cbbees.zapper.hollow"), hollow) { hollow = it }
        }

        if (shape.hasConnectivityOptions) {
            toggle(x + 79, y + 79, AllIcons.I_FOLLOW_DIAGONAL, CreateLang.translateDirect("gui.terrainzapper.searchDiagonal"), diagonals) { diagonals = it }
            toggle(x + 97, y + 79, AllIcons.I_FOLLOW_MATERIAL, CreateLang.translateDirect("gui.terrainzapper.searchFuzzy"), fuzzy) { fuzzy = it }
        }

        if (tool !in shape.tools) tool = shape.tools.first()
        val toolButtons = shape.tools.mapIndexed { id, option ->
            IconButton(x + 7 + id * 18, y + 79, option.icon).apply {
                describe(this, CreateLang.translateDirect("gui.terrainzapper.tool." + option.translationKey), toolDescription(option))
                green = option == tool
            }
        }
        toolButtons.forEachIndexed { id, button ->
            button.withCallback<IconButton> {
                toolButtons.forEach { it.green = false }
                button.green = true
                tool = shape.tools[id]
            }
        }
        shapeWidgets += toolButtons

        if (shape.hasPlacementOptions) {
            val placementButtons = PlacementOptions.entries.mapIndexed { id, option ->
                IconButton(x + 136 + id * 18, y + 79, option.icon).apply {
                    describe(this, CreateLang.translateDirect("gui.terrainzapper.placement." + option.translationKey), Component.translatable("gui.cbbees.zapper.placement.${option.translationKey}.desc"))
                    green = option == placement
                }
            }
            placementButtons.forEachIndexed { id, button ->
                button.withCallback<IconButton> {
                    placementButtons.forEach { it.green = false }
                    button.green = true
                    placement = PlacementOptions.entries[id]
                }
            }
            shapeWidgets += placementButtons
        }

        addRenderableWidgets(shapeWidgets)
    }

    private fun toolDescription(option: TerrainTools): Component =
        Component.translatable("gui.cbbees.zapper.tool.${option.translationKey}.desc")

    private fun describe(button: IconButton, name: Component, description: Component) {
        button.toolTip.clear()
        button.toolTip += name
        button.toolTip += FontHelper.cutTextComponent(description, FontHelper.Palette.ALL_GRAY)
    }

    private fun toggle(x: Int, y: Int, icon: AllIcons, tooltip: Component, on: Boolean, onChange: (Boolean) -> Unit) {
        val indicator = Indicator(x, y - 6, CommonComponents.EMPTY).apply { state = if (on) Indicator.State.ON else Indicator.State.OFF }
        val button = IconButton(x, y, icon).apply { setToolTip(tooltip) }
        button.withCallback<IconButton> {
            val enabled = indicator.state != Indicator.State.ON
            indicator.state = if (enabled) Indicator.State.ON else Indicator.State.OFF
            onChange(enabled)
        }
        shapeWidgets += indicator
        shapeWidgets += button
    }

    override fun renderWindow(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTicks: Float) {
        AllCCRGuiTextures.SCHEMATIC_ZAPPER.render(graphics, guiLeft, guiTop)
        drawOnBackground(graphics, guiLeft, guiTop)
        renderBlock(graphics, guiLeft, guiTop)
        renderZapper(graphics, guiLeft, guiTop)
        renderPreview(graphics)
    }

    private fun renderPreview(graphics: GuiGraphics) {
        val x = guiLeft - PREVIEW_GAP - PREVIEW_WIDTH
        val h = AllCCRGuiTextures.SCHEMATIC_ZAPPER.height
        AllCCRGuiTextures.PLANNER_PANEL.renderNineSlice(graphics, x, guiTop, PREVIEW_WIDTH, h, 2)
        AllCCRGuiTextures.PLANNER_VIEWPORT.renderTiled(graphics, x + 2, guiTop + 2, PREVIEW_WIDTH - 4, h - 4)
        val settings = ShapeSettings(shape.id, values.take(shape.params.size), hollow, diagonals, fuzzy)
        val block = zapper.getOrDefault(AllDataComponents.SHAPER_BLOCK_USED, Blocks.AIR.defaultBlockState())
            .takeUnless { it.isAir } ?: Blocks.STONE.defaultBlockState()
        preview.render(graphics, shape, settings, block, x + 2, guiTop + 2, PREVIEW_WIDTH - 4, h - 4)
        renderToolCaption(graphics, x + 2, guiTop + h - 2, PREVIEW_WIDTH - 4)
    }

    private fun renderToolCaption(graphics: GuiGraphics, x: Int, bottom: Int, w: Int) {
        val name = CreateLang.translateDirect("gui.terrainzapper.tool." + tool.translationKey)
        val lines = font.split(toolDescription(tool), w - 6)
        val top = bottom - 4 - (lines.size + 1) * CAPTION_LINE
        val pose = graphics.pose()
        pose.pushPose()
        pose.translate(0f, 0f, 400f)
        graphics.fill(x, top - 2, x + w, bottom, 0xA0101010.toInt())
        graphics.drawString(font, name, x + 3, top, 0xFFFFFF)
        lines.forEachIndexed { i, line -> graphics.drawString(font, line, x + 3, top + (i + 1) * CAPTION_LINE, 0xAAAAAA) }
        pose.popPose()
    }

    override fun drawOnBackground(graphics: GuiGraphics, x: Int, y: Int) {
        graphics.drawString(font, title, x + (AllCCRGuiTextures.SCHEMATIC_ZAPPER.width - font.width(title)) / 2, y + 4, fontColor, false)
        for (index in MAX_PARAMS - 1 downTo shape.params.size) {
            AllCCRGuiTextures.SCHEMATIC_ZAPPER_INACTIVE_PARAM.render(graphics, x + 56 + 20 * index, y + 40)
        }
        graphics.drawString(font, toolSection, x + 7, y + 69, fontColor, false)
        if (shape.hasPlacementOptions) graphics.drawString(font, placementSection, x + 136, y + 69, fontColor, false)
    }

    override fun getConfigurationPacket(): ConfigureZapperPacket =
        ConfigureWorldshaperPacket(hand, currentPattern, TerrainBrushes.Cuboid, 1, 1, 1, tool, placement)

    override fun removed() {
        super.removed()
        val settings = ShapeSettings(shape.id, values.take(shape.params.size), hollow, diagonals, fuzzy).clampedTo(shape)
        zapper.set(CBBeesDataComponents.ZAPPER_SHAPE.get(), settings)
        PacketDistributor.sendToServer(ConfigureZapperShapePacket(hand, settings))
    }

    private companion object {
        const val MAX_PARAMS = 3
        const val PREVIEW_WIDTH = 140
        const val PREVIEW_GAP = 4
        const val CAPTION_LINE = 10
    }
}

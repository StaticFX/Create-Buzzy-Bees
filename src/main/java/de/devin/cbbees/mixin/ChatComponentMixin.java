package de.devin.cbbees.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.devin.cbbees.content.schematics.client.SharedSchematicChat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {

    @Shadow
    private int getLineHeight() {
        throw new AssertionError();
    }

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I")
    )
    private int cbbees$collectSharedPreview(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y, int color, Operation<Integer> original) {
        SharedSchematicChat.INSTANCE.collect(text, y, getLineHeight());
        return original.call(graphics, font, text, x, y, color);
    }

    @Inject(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;getChatListener()Lnet/minecraft/client/multiplayer/chat/ChatListener;")
    )
    private void cbbees$drawSharedPreviews(GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        SharedSchematicChat.INSTANCE.flush(graphics);
    }
}

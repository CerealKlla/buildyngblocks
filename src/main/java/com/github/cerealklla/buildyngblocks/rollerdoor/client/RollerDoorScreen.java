package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Standard vanilla 3-row-chest texture reuse, per Settlemynts' GuardhouseFoodScreen precedent. */
public class RollerDoorScreen extends AbstractContainerScreen<RollerDoorMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");

    public RollerDoorScreen(RollerDoorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

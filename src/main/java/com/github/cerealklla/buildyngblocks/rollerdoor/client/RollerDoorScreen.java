package com.github.cerealklla.buildyngblocks.rollerdoor.client;

import com.github.cerealklla.buildyngblocks.rollerdoor.RollerDoorMenu;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Standard vanilla 3-row-chest texture reuse, per Settlemynts' GuardhouseFoodScreen precedent --
 * mirrored one-for-one against vanilla's own real {@code ContainerScreen} (the generic N-row chest
 * screen class, confirmed against its decompiled source for this exact MC version, not guessed).
 *
 * <p>Two real bugs fixed, found live via a screenshot: the original version used a flat {@code
 * imageHeight = 166} and a single straight blit of the texture's raw top-left corner. The real
 * {@code generic_54.png} isn't a simple stretchable panel -- vanilla's own formula for an N-row
 * generic container is {@code imageHeight = 114 + rows * 18} (168 for 3 rows, not 166), and the
 * background must be drawn as two separate crops: the top chrome+slot-grid region (source
 * {@code (0,0)}, height {@code rows*18+17}) and the bottom chrome+player-inventory region (source
 * {@code (0,126)}, fixed height 96), stacked directly under each other. A single flat blit instead
 * crops an arbitrary, wrongly-proportioned rectangle out of the source texture, so the texture's own
 * baked-in slot gridlines don't line up with the real (separately, correctly computed)
 * {@code RollerDoorMenu} slot positions at all -- exactly the overlap/misalignment visible in the
 * screenshot.
 */
public class RollerDoorScreen extends AbstractContainerScreen<RollerDoorMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 3;

    public RollerDoorScreen(RollerDoorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F,
                this.imageWidth, ROWS * 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos + ROWS * 18 + 17, 0.0F, 126.0F,
                this.imageWidth, 96, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

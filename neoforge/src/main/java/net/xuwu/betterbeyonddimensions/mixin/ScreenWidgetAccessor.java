package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Exposes Screen's widget lists without adding classes to a Minecraft package. */
@Mixin(Screen.class)
public interface ScreenWidgetAccessor
{
    @Accessor("children")
    List<GuiEventListener> bbd$getChildren();

    @Accessor("renderables")
    List<Renderable> bbd$getRenderables();

    @Accessor("narratables")
    List<NarratableEntry> bbd$getNarratables();
}

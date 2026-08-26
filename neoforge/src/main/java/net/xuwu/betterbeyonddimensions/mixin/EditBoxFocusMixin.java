package net.xuwu.betterbeyonddimensions.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.xuwu.betterbeyonddimensions.client.TextInputFocusTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Records focus changes for all vanilla EditBox-based text inputs. */
@Mixin(EditBox.class)
public abstract class EditBoxFocusMixin
{
    @Inject(method = "setFocused", at = @At("TAIL"))
    private void bbd$trackFocus(boolean focused, CallbackInfo callbackInfo)
    {
        TextInputFocusTracker.onFocusChanged((EditBox) (Object) this, focused);
    }
}

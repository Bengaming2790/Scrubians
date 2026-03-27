package ca.techgarage.scrubians.mixin;

import net.minecraft.entity.decoration.MannequinEntity;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(MannequinEntity.class)
public abstract class MannequinEntityMixin {

    @Shadow
    private static TrackedData<Optional<Text>> DESCRIPTION;

    @Inject(
            method = "<init>(Lnet/minecraft/entity/EntityType;Lnet/minecraft/world/World;)V",
            at = @At("TAIL")
    )
    private void scrubDefaultDescription(CallbackInfo ci) {
        ((net.minecraft.entity.Entity)(Object)this)
                .getDataTracker()
                .set(DESCRIPTION, Optional.empty());
    }
}

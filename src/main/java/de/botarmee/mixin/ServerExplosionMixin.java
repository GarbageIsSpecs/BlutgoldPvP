package de.botarmee.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;

import de.botarmee.ArenaProtection;

/** Explosionen (Creeper, TNT) machen in gebauten Arenen keine Löcher. */
@Mixin(ServerExplosion.class)
public abstract class ServerExplosionMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
	private void botarmee$protectArena(CallbackInfoReturnable<List<BlockPos>> cir) {
		cir.getReturnValue().removeIf(pos -> ArenaProtection.isProtected(this.level, pos));
	}
}

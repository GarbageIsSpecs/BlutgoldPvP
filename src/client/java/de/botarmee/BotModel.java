package de.botarmee;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.HumanoidArm;

/** Spieler-Modell (mit Jacke, Ärmeln, Hose) für den eigenen {@link BotRenderState}. */
public class BotModel extends HumanoidModel<BotRenderState> {
	private final boolean slim;

	public BotModel(ModelPart root, boolean slim) {
		super(root, RenderTypes::entityTranslucent);
		this.slim = slim;
	}

	@Override
	public void translateToHand(net.minecraft.client.renderer.entity.state.HumanoidRenderState state, HumanoidArm arm, PoseStack poseStack) {
		this.root().translateAndRotate(poseStack);
		ModelPart part = this.getArm(arm);
		if (slim) {
			float offset = 0.5F * (arm == HumanoidArm.RIGHT ? 1 : -1);
			part.x += offset;
			part.translateAndRotate(poseStack);
			part.x -= offset;
		} else {
			part.translateAndRotate(poseStack);
		}
	}
}

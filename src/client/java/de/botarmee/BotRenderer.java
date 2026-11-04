package de.botarmee;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;

/** Zeichnet Bots exakt wie echte Spieler: Spieler-Modell, Skin, Rüstung, Waffen, Pfeile im Körper. */
public class BotRenderer extends LivingEntityRenderer<BotEntity, AvatarRenderState, PlayerModel> {
	private final PlayerModel wideModel;
	private final PlayerModel slimModel;

	public BotRenderer(EntityRendererProvider.Context context) {
		super(context, new PlayerModel(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
		this.wideModel = this.model;
		this.slimModel = new PlayerModel(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
		this.addLayer(new HumanoidArmorLayer<>(this,
				ArmorModelSet.bake(ModelLayers.PLAYER_ARMOR, context.getModelSet(), part -> new PlayerModel(part, false)),
				context.getEquipmentRenderer()));
		this.addLayer(new PlayerItemInHandLayer<>(this));
		this.addLayer(new ArrowLayer<>(this, context));
		this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getPlayerSkinRenderCache()));
		this.addLayer(new WingsLayer<>(this, context.getModelSet(), context.getEquipmentRenderer()));
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new AvatarRenderState();
	}

	@Override
	public void extractRenderState(BotEntity entity, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		HumanoidMobRenderer.extractHumanoidRenderState(entity, state, partialTicks, this.itemModelResolver);
		state.rightArmPose = armPose(entity, HumanoidArm.RIGHT);
		state.leftArmPose = armPose(entity, HumanoidArm.LEFT);
		state.skin = Skins.get(entity.getSkinSeed());
		state.id = entity.getId();
		state.arrowCount = entity.getArrowCount();
		state.showCape = false;
	}

	private static HumanoidModel.ArmPose armPose(BotEntity bot, HumanoidArm arm) {
		InteractionHand hand = arm == bot.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		ItemStack stack = bot.getItemInHand(hand);
		if (stack.isEmpty()) return HumanoidModel.ArmPose.EMPTY;
		if (bot.isUsingItem() && bot.getUsedItemHand() == hand) {
			ItemUseAnimation anim = stack.getUseAnimation();
			if (anim == ItemUseAnimation.BOW) return HumanoidModel.ArmPose.BOW_AND_ARROW;
			if (anim == ItemUseAnimation.CROSSBOW) return HumanoidModel.ArmPose.CROSSBOW_CHARGE;
			if (anim == ItemUseAnimation.BLOCK) return HumanoidModel.ArmPose.BLOCK;
		}
		if (stack.is(Items.CROSSBOW) && hand == InteractionHand.MAIN_HAND && !bot.isUsingItem()) return HumanoidModel.ArmPose.CROSSBOW_HOLD;
		return HumanoidMobRenderer.usesSpearPose(stack, arm, bot) ? HumanoidModel.ArmPose.SPEAR : HumanoidModel.ArmPose.ITEM;
	}

	@Override
	public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		this.model = state.skin.model() == PlayerModelType.SLIM ? slimModel : wideModel;
		super.submit(state, poseStack, collector, camera);
	}

	@Override
	public Identifier getTextureLocation(AvatarRenderState state) {
		return state.skin.body().texturePath();
	}

	@Override
	protected void scale(AvatarRenderState state, PoseStack poseStack) {
		poseStack.scale(0.9375F, 0.9375F, 0.9375F);
	}
}

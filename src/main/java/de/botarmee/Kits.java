package de.botarmee;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/** Welche Gegenstände man im Armor-Stand-Editor auswählen kann und wie daraus ItemStacks werden. */
public final class Kits {
	private Kits() {}

	public static final EquipmentSlot[] SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
	};

	private static final String[] TIERS = {"leather", "chainmail", "copper", "iron", "golden", "diamond", "netherite"};

	public static final List<List<String>> OPTIONS = List.of(
			armor("helmet", "minecraft:turtle_helmet"),
			armor("chestplate", "minecraft:elytra"),
			armor("leggings"),
			armor("boots"),
			List.of("",
					"minecraft:wooden_sword", "minecraft:stone_sword", "minecraft:copper_sword", "minecraft:iron_sword",
					"minecraft:golden_sword", "minecraft:diamond_sword", "minecraft:netherite_sword",
					"minecraft:stone_axe", "minecraft:iron_axe", "minecraft:golden_axe", "minecraft:diamond_axe", "minecraft:netherite_axe",
					"minecraft:wooden_spear", "minecraft:stone_spear", "minecraft:copper_spear", "minecraft:iron_spear",
					"minecraft:golden_spear", "minecraft:diamond_spear", "minecraft:netherite_spear",
					"minecraft:trident", "minecraft:mace", "minecraft:bow", "minecraft:crossbow", "minecraft:stick", "minecraft:blaze_rod"),
			List.of("", "minecraft:shield", "minecraft:totem_of_undying", "minecraft:torch", "minecraft:white_banner",
					"minecraft:red_banner", "minecraft:blue_banner", "minecraft:arrow")
	);

	public static final String[] PATTERNS = {
			"sentry", "dune", "coast", "wild", "ward", "eye", "vex", "tide", "snout", "rib",
			"spire", "wayfinder", "shaper", "silence", "raiser", "host", "flow", "bolt"
	};
	public static final String[] MATERIALS = {
			"quartz", "iron", "netherite", "redstone", "copper", "gold", "emerald", "diamond", "lapis", "amethyst", "resin"
	};

	private static List<String> armor(String type, String... extra) {
		String[] out = new String[1 + TIERS.length + extra.length];
		out[0] = "";
		for (int i = 0; i < TIERS.length; i++) out[1 + i] = "minecraft:" + TIERS[i] + "_" + type;
		System.arraycopy(extra, 0, out, 1 + TIERS.length, extra.length);
		return List.of(out);
	}

	public static Item item(String id) {
		if (id == null || id.isEmpty()) return Items.AIR;
		Identifier key = Identifier.tryParse(id);
		if (key == null) return Items.AIR;
		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == null ? Items.AIR : item;
	}

	/** Symbol-Item für ein Trim-Muster (die Schmiedevorlage). */
	public static ItemStack patternIcon(String pattern) {
		Item it = item("minecraft:" + pattern + "_armor_trim_smithing_template");
		return new ItemStack(it == Items.AIR ? Items.PAPER : it);
	}

	/** Symbol-Item für ein Trim-Material. */
	public static ItemStack materialIcon(String material) {
		String id = switch (material) {
			case "quartz" -> "minecraft:quartz";
			case "iron" -> "minecraft:iron_ingot";
			case "netherite" -> "minecraft:netherite_ingot";
			case "redstone" -> "minecraft:redstone";
			case "copper" -> "minecraft:copper_ingot";
			case "gold" -> "minecraft:gold_ingot";
			case "emerald" -> "minecraft:emerald";
			case "diamond" -> "minecraft:diamond";
			case "lapis" -> "minecraft:lapis_lazuli";
			case "amethyst" -> "minecraft:amethyst_shard";
			case "resin" -> "minecraft:resin_brick";
			default -> "minecraft:barrier";
		};
		return new ItemStack(item(id));
	}

	public static boolean isRanged(Item item) {
		return item == Items.BOW || item == Items.CROSSBOW;
	}

	/**
	 * Baut den Gegenstand für einen Slot. {@code dyeRgb} färbt Leder (oder -1),
	 * {@code downgrade} = wie viele Stufen schlechter (für zufällige Variation).
	 */
	public static ItemStack build(HolderLookup.Provider registries, BattleConfig.Piece piece, int slot, int dyeRgb, int downgrade) {
		String id = piece.item;
		if (downgrade > 0 && slot < 4) id = downgradeId(id, downgrade);
		Item it = item(id);
		if (it == Items.AIR) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(it);
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		if (piece.glint) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		if (dyeRgb >= 0 && id.contains("leather_")) stack.set(DataComponents.DYED_COLOR, new DyedItemColor(dyeRgb));
		if (slot < 4 && !piece.pattern.isEmpty() && !piece.material.isEmpty() && registries != null && stack.is(ItemTags.TRIMMABLE_ARMOR)) {
			try {
				Holder<TrimPattern> pattern = registries.lookupOrThrow(Registries.TRIM_PATTERN)
						.get(ResourceKey.create(Registries.TRIM_PATTERN, Identifier.withDefaultNamespace(piece.pattern))).orElse(null);
				Holder<TrimMaterial> material = registries.lookupOrThrow(Registries.TRIM_MATERIAL)
						.get(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.withDefaultNamespace(piece.material))).orElse(null);
				if (pattern != null && material != null) stack.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
			} catch (RuntimeException ignored) {
				// Trim-Registry fehlt (z.B. ganz am Anfang) – dann eben ohne Trim
			}
		}
		return stack;
	}

	private static String downgradeId(String id, int steps) {
		for (int i = 0; i < TIERS.length; i++) {
			String prefix = "minecraft:" + TIERS[i] + "_";
			if (id.startsWith(prefix)) {
				int ni = i - steps;
				if (ni < 0) return "";
				return "minecraft:" + TIERS[ni] + "_" + id.substring(prefix.length());
			}
		}
		return id;
	}

	/** Zufällige Abweichung: 0 = gleich, 1 = eine Stufe schlechter, 9 = Teil fehlt. */
	public static int rollDowngrade(RandomSource random, int variationPercent) {
		if (random.nextInt(100) >= variationPercent) return 0;
		return random.nextInt(4) == 0 ? 9 : 1 + random.nextInt(2);
	}

	public static String shortName(String id) {
		if (id == null || id.isEmpty()) return "Nichts";
		int c = id.indexOf(':');
		return id.substring(c + 1).replace('_', ' ');
	}
}

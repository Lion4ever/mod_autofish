package net.unladenswallow.minecraft.autofish;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import jline.internal.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.world.storage.loot.LootEntry;
import net.minecraft.world.storage.loot.LootPool;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableList;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraft.world.storage.loot.conditions.RandomChanceWithLooting;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;

public class AutoFishEventHandler {

	private Minecraft minecraft;
	private EntityPlayer player;
	private long castScheduledAt = 0L;
	private long startedReelDelayAt = 0L;
	private long dropScheduledFor = 0L;
	private int nextDumpSlot = FIRST_DROP_SLOT;
	private static final int CAST_QUEUE_TICK_DELAY = 30;
	private static final int REEL_TICK_DELAY = 5;
	private static final int DROP_BASE_DELAY = 20;
	private static final int DROP_SEARCH_DELAY = 5;
	private static final int FIRST_DROP_SLOT = 10; //No hotbar :(
	private static final int AUTOFISH_BREAKPREVENT_THRESHOLD = 2;
	private static final double MOTION_Y_THRESHOLD = -0.02d;
	private static final double DROP_CHANCE_DECREMENT = 0.95;


	@SubscribeEvent
	public void onClientTickEvent(ClientTickEvent event) {
		this.minecraft = Minecraft.getMinecraft();
		if (ModAutoFish.config_autofish_enable && !this.minecraft.isGamePaused() && this.minecraft.player != null) {
			this.player = this.minecraft.player;

			if (playerHookInWater() && !isDuringReelDelay() && isFishBiting()) {
				startReelDelay();
				playerUseRod();
				if (ModAutoFish.config_autofish_drop_junk && isTimeToDumpJunk()) {
					minecraft.getConnection()
					.sendPacket(new CPacketClientStatus(CPacketClientStatus.State.OPEN_INVENTORY_ACHIEVEMENT));
					scheduleNextDrop(0);
				} else {
					scheduleNextCast();
				}
			} else if (isTimeToCast()) {
				if (needToSwitchRods()) {
					tryToSwitchRods();
				}
				if (playerCanCast()) {
					playerUseRod();
				}
				// Resetting these values is not strictly necessary, but will
				// improve the performance
				// of the check that potentially occurs every tick.
				resetReelDelay();
				resetCastSchedule();
			} else if (isTimeToDrop()) {
				dropJunkItem();
			}
		}
	}

	private boolean isJunk(Item item) {
		for (int i = 0; i < ModAutoFish.config_autofish_junk.length; i++) {
			if (item.getRegistryName().toString().equals(ModAutoFish.config_autofish_junk[i])) {
				return true;
			}
		}

		return false;
	}

	private void dropJunkItem() {
		NonNullList<ItemStack> mainInv = player.inventory.mainInventory;
		int i;
		for (i = nextDumpSlot; i < mainInv.size(); i++) {
			if (isJunk(mainInv.get(i).getItem())) {
				minecraft.playerController.windowClick(player.inventoryContainer.windowId, i, 1, ClickType.THROW,
						player);
				scheduleNextDrop(i - nextDumpSlot);
				nextDumpSlot = (i + 1) % mainInv.size();
				return;
			}
		}
		nextDumpSlot = FIRST_DROP_SLOT;
		dropScheduledFor = 0;
		scheduleNextCast();
	}

	private boolean isTimeToDrop() {
		return (this.dropScheduledFor != 0 && this.minecraft.world.getTotalWorldTime() > this.dropScheduledFor);
	}

	private void scheduleNextDrop(int numberOfSearchedSlots) {
		Random rng = player.getRNG();
		this.dropScheduledFor = minecraft.world.getTotalWorldTime() + rng.nextInt(DROP_BASE_DELAY)
				+ rng.nextInt(DROP_SEARCH_DELAY * numberOfSearchedSlots + 1)
				+ DROP_SEARCH_DELAY * numberOfSearchedSlots;
	}

	private int countEmptySlots() {
		int count = 0;
		for (int i = FIRST_DROP_SLOT; i < player.inventory.mainInventory.size(); ++i) {
			if (((ItemStack) player.inventory.mainInventory.get(i)).isEmpty()) {
				count++;
			}
		}
		return count;
	}

	private boolean isTimeToDumpJunk() {
		return player.getRNG().nextDouble() <= Math.pow(DROP_CHANCE_DECREMENT, countEmptySlots());
	}

	private void resetCastSchedule() {
		this.castScheduledAt = 0;
	}

	private boolean needToSwitchRods() {
		return ModAutoFish.config_autofish_multirod && !playerCanCast();
	}

	private void scheduleNextCast() {
		this.castScheduledAt = this.minecraft.world.getTotalWorldTime();
	}

	/*
	 * Trigger a delay so we don't use the rod multiple times for the same bite,
	 * which can persist for 2-3 ticks.
	 */
	private void startReelDelay() {
		this.startedReelDelayAt = this.minecraft.world.getTotalWorldTime();
	}

	private void resetReelDelay() {
		startedReelDelayAt = 0;
	}

	private boolean isDuringReelDelay() {
		return (this.startedReelDelayAt != 0
				&& this.minecraft.world.getTotalWorldTime() < this.startedReelDelayAt + REEL_TICK_DELAY);
	}

	private boolean playerHookInWater() {
		return this.player.fishEntity != null && this.player.fishEntity.isInWater();
	}

	private boolean playerIsHoldingRod() {
		ItemStack heldItem = this.player.getHeldItemMainhand();

		return (heldItem != null && heldItem.getItem() == Items.FISHING_ROD
				&& heldItem.getItemDamage() <= heldItem.getMaxDamage());
	}

	private boolean isFishBiting() {
		EntityPlayer serverPlayerEntity = getServerPlayerEntity();
		if (serverPlayerEntity != null) {
			/*
			 * If single player (integrated server), we can actually check to
			 * see if something is catchable, but it's fragile (other mods could
			 * break it) If anything goes wrong, fall back to the safer but less
			 * reliable method
			 */
			try {
				return isFishBiting_fromServerEntity(serverPlayerEntity);
			} catch (Exception e) {
				return isFishBiting_fromMovement();
			}
		} else {
			return isFishBiting_fromMovement();
		}
	}

	private boolean isFishBiting_fromMovement() {
		EntityFishHook fishEntity = this.player.fishEntity;
		if (fishEntity != null && fishEntity.motionX == 0 && fishEntity.motionZ == 0
				&& fishEntity.motionY < MOTION_Y_THRESHOLD) {
			return true;
		}
		return false;
	}

	private boolean isFishBiting_fromServerEntity(EntityPlayer serverPlayerEntity) throws NumberFormatException,
			NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		/*
		 * The fish hook entity on the server side knows whether a fish is
		 * catchable at any given time. However, that field is private and not
		 * exposed in any way. So we must use reflection to access that field.
		 */
		EntityFishHook serverFishEntity = serverPlayerEntity.fishEntity;
		int ticksCatchable = getPrivateIntFieldFromObject(serverFishEntity, "ticksCatchable", "field_146045_ax");
		if (ticksCatchable > 0) {
			return true;
		}
		return false;
	}

	/**
	 * Using Java reflection APIs, access a private member data of type int
	 * 
	 * @param object
	 *            The target object
	 * @param fieldName
	 *            The name of the private data field in object
	 * 
	 * @return The int value of the private member data from object with
	 *         fieldName
	 */
	private int getPrivateIntFieldFromObject(Object object, String forgeFieldName, String vanillaFieldName)
			throws NoSuchFieldException, SecurityException, NumberFormatException, IllegalArgumentException,
			IllegalAccessException {
		Field targetField = null;
		try {
			targetField = object.getClass().getDeclaredField(forgeFieldName);
		} catch (NoSuchFieldException e) {
			targetField = object.getClass().getDeclaredField(vanillaFieldName);
		}
		if (targetField != null) {
			targetField.setAccessible(true);
			return Integer.valueOf(targetField.get(object).toString()).intValue();
		} else {
			return 0;
		}

	}

	private EntityPlayer getServerPlayerEntity() {
		if (this.minecraft.getIntegratedServer() == null
				|| this.minecraft.getIntegratedServer().getEntityWorld() == null) {
			return null;
		} else {
			return this.minecraft.getIntegratedServer().getEntityWorld()
					.getPlayerEntityByName(this.minecraft.player.getName());
		}
	}

	private EnumActionResult playerUseRod() {
		return this.minecraft.playerController.processRightClick(this.player, this.minecraft.world, EnumHand.MAIN_HAND);
	}

	private boolean isTimeToCast() {
		return (this.castScheduledAt != 0
				&& this.minecraft.world.getTotalWorldTime() > this.castScheduledAt + CAST_QUEUE_TICK_DELAY);
	}

	private void tryToSwitchRods() {
		InventoryPlayer inventory = this.player.inventory;
		for (int i = 0; i < 9; i++) {
			ItemStack curItemStack = inventory.mainInventory.get(i);
			if (curItemStack != null && curItemStack.getItem() == Items.FISHING_ROD
					&& (!ModAutoFish.config_autofish_preventBreak || (curItemStack.getMaxDamage()
							- curItemStack.getItemDamage() > AUTOFISH_BREAKPREVENT_THRESHOLD))) {
				inventory.currentItem = i;
				break;
			}
		}
	}

	private boolean playerCanCast() {
		if (!playerIsHoldingRod()) {
			return false;
		} else {
			ItemStack heldItem = this.player.getHeldItemMainhand();

			return (!ModAutoFish.config_autofish_preventBreak
					|| (heldItem.getMaxDamage() - heldItem.getItemDamage() > AUTOFISH_BREAKPREVENT_THRESHOLD));
		}
	}

	@SubscribeEvent
	public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent eventArgs) {
		if (eventArgs.getModID().equals(ModAutoFish.MODID)) {
			ModAutoFish.syncConfig();
		}
	}
}

package me.fril.regeneration.common.capability;

import java.awt.Color;
import java.util.UUID;

import javax.annotation.Nonnull;

import me.fril.regeneration.RegenerationMod;
import me.fril.regeneration.client.RegenKeyBinds;
import me.fril.regeneration.common.types.IRegenType;
import me.fril.regeneration.common.types.RegenTypes;
import me.fril.regeneration.network.MessageUpdateRegen;
import me.fril.regeneration.network.NetworkHandler;
import me.fril.regeneration.util.ExplosionUtil;
import me.fril.regeneration.util.PlayerUtil;
import me.fril.regeneration.util.RegenConfig;
import me.fril.regeneration.util.RegenObjects;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.fml.common.Mod;

/**
 * Created by Sub
 * on 16/09/2018.
 */
@Mod.EventBusSubscriber(modid = RegenerationMod.MODID)
public class CapabilityRegeneration implements IRegeneration {
	
	public static final ResourceLocation CAP_REGEN_ID = new ResourceLocation(RegenerationMod.MODID, "regeneration");
	private static final UUID SLOWNESS_ID = UUID.fromString("f9aa2c36-f3f3-4d76-a148-86d6f2c87782");
	
	@CapabilityInject(IRegeneration.class)
	public static final Capability<IRegeneration> CAPABILITY = null;
	
	
	private final EntityPlayer player;
	private int timesRegenerated = 0, livesLeft = 0, regenTicks = 0, ticksInSolace = 0, ticksGlowing = 0;
	private boolean isRegenerating = false, isInGrace = false, isGraceGlowing = false;
	private String typeName = RegenTypes.FIERY.getName(), traitName = "none"; //NOTE unused
	
	private float primaryRed = 0.93f, primaryGreen = 0.61f, primaryBlue = 0.0f;
	private float secondaryRed = 1f, secondaryGreen = 0.5f, secondaryBlue = 0.18f;
	
	private AttributeModifier slownessModifier = new AttributeModifier(SLOWNESS_ID, "slow", -0.5D, 1);
	
	
	
	public CapabilityRegeneration() {
		this.player = null;
	}
	
	public CapabilityRegeneration(EntityPlayer player) {
		this.player = player;
	}
	
	@Nonnull
	public static IRegeneration getForPlayer(EntityPlayer player) {
		if (player.hasCapability(CAPABILITY, null)) {
			return player.getCapability(CAPABILITY, null);
		}
		throw new IllegalStateException("Missing Regeneration capability: " + player + ", please report this to the issue tracker");
	}
	
	@Override
	public void update() {
		if (isRegenerating()) {
			updateRegeneration();
			sync();
		} else {
			setSolaceTicks(0);
			setInGracePeriod(false);
			setTicksGlowing(0);
		}
	}
	
	@Override
	public boolean isRegenerating() {
		return isRegenerating;
	}
	
	@Override
	public void setRegenerating(boolean regenerating) {
		isRegenerating = regenerating;
	}
	
	@Override
	public boolean isGlowing() {
		return isGraceGlowing;
	}
	
	@Override
	public void setGlowing(boolean glowing) {
		isGraceGlowing = glowing;
	}
	
	@Override
	public int getTicksGlowing() {
		return ticksGlowing;
	}
	
	@Override
	public void setTicksGlowing(int ticks) {
		ticksGlowing = ticks;
	}
	
	@Override
	public boolean isInGracePeriod() {
		return isInGrace;
	}
	
	@Override
	public void setInGracePeriod(boolean gracePeriod) {
		isInGrace = gracePeriod;
	}
	
	@Override
	public int getSolaceTicks() {
		return ticksInSolace;
	}
	
	@Override
	public void setSolaceTicks(int ticks) {
		ticksInSolace = ticks;
	}
	
	@Override
	public int getTicksRegenerating() {
		return regenTicks;
	}
	
	@Override
	public void setTicksRegenerating(int ticks) {
		regenTicks = ticks;
	}
	
	@Override
	public EntityPlayer getPlayer() {
		return player;
	}
	
	@Override
	public int getLivesLeft() {
		return livesLeft;
	}
	
	@Override
	public void setLivesLeft(int left) {
		livesLeft = left;
	}
	
	@Override
	public int getTimesRegenerated() {
		return timesRegenerated;
	}
	
	@Override
	public void setTimesRegenerated(int times) {
		timesRegenerated = times;
	}
	
	@Override
	public NBTTagCompound getStyle() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setFloat("PrimaryRed", primaryRed);
		nbt.setFloat("PrimaryGreen", primaryGreen);
		nbt.setFloat("PrimaryBlue", primaryBlue);
		
		nbt.setFloat("SecondaryRed", secondaryRed);
		nbt.setFloat("SecondaryGreen", secondaryGreen);
		nbt.setFloat("SecondaryBlue", secondaryBlue);
		return nbt;
	}
	
	@Override
	public void setStyle(NBTTagCompound nbt) {
		primaryRed = nbt.getFloat("PrimaryRed");
		primaryGreen = nbt.getFloat("PrimaryGreen");
		primaryBlue = nbt.getFloat("PrimaryBlue");
		
		secondaryRed = nbt.getFloat("SecondaryRed");
		secondaryGreen = nbt.getFloat("SecondaryGreen");
		secondaryBlue = nbt.getFloat("SecondaryBlue");
	}
	
	@Override
	public void sync() {
		NetworkHandler.INSTANCE.sendToAll(new MessageUpdateRegen(player, serializeNBT()));
	}
	
	@Override
	public IRegenType getType() {
		return RegenTypes.getTypeByName(typeName);
	}
	
	@Override
	public void setType(String name) {
		typeName = name;
	}
	
	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagCompound nbt = getStyle();
		
		nbt.setBoolean("isRegenerating", isRegenerating);
		nbt.setInteger("timesRegenerated", timesRegenerated);
		nbt.setInteger("livesLeft", livesLeft);
		nbt.setInteger("regenTicks", regenTicks);
		nbt.setBoolean("gracePeriod", isInGrace);
		nbt.setInteger("solaceTicks", ticksInSolace);
		nbt.setBoolean("handGlowing", isGraceGlowing);
		nbt.setString("type", typeName);
		
		nbt.setFloat("PrimaryRed", primaryRed);
		nbt.setFloat("PrimaryGreen", primaryGreen);
		nbt.setFloat("PrimaryBlue", primaryBlue);
		
		nbt.setFloat("SecondaryRed", secondaryRed);
		nbt.setFloat("SecondaryGreen", secondaryGreen);
		nbt.setFloat("SecondaryBlue", secondaryBlue);
		
		return nbt;
	}
	
	@Override
	public void deserializeNBT(NBTTagCompound nbt) {
		setRegenerating(nbt.getBoolean("isRegenerating"));
		setTimesRegenerated(nbt.getInteger("timesRegenerated"));
		setLivesLeft(nbt.getInteger("livesLeft"));
		setTicksRegenerating(nbt.getInteger("regenTicks"));
		setInGracePeriod(nbt.getBoolean("gracePeriod"));
		setSolaceTicks(nbt.getInteger("solaceTicks"));
		setGlowing(nbt.getBoolean("handGlowing"));
		setType(nbt.getString("type"));
		
		primaryRed = nbt.getFloat("PrimaryRed");
		primaryGreen = nbt.getFloat("PrimaryGreen");
		primaryBlue = nbt.getFloat("PrimaryBlue");
		
		secondaryRed = nbt.getFloat("SecondaryRed");
		secondaryGreen = nbt.getFloat("SecondaryGreen");
		secondaryBlue = nbt.getFloat("SecondaryBlue");
	}
	
	@Override
	public Color getPrimaryColor() {
		return new Color(primaryRed, primaryGreen, primaryBlue);
	}
	
	@Override
	public Color getSecondaryColor() {
		return new Color(secondaryRed, secondaryGreen, secondaryBlue);
	}
	
	
	private void updateRegeneration() {
		setSolaceTicks(getSolaceTicks() + 1);
		
		// Start glowing and playing the hand sound
		if (getSolaceTicks() == 2) {
			setGlowing(true);
			if (player.world.isRemote) {
				PlayerUtil.playMovingSound(player, RegenObjects.Sounds.HAND_GLOW, SoundCategory.PLAYERS);
			}
		}
		
		// Indicate to the player what keybinds to use on the client
		if (player.world.isRemote && getSolaceTicks() < 200 && !isInGracePeriod()) {
			if (ticksInSolace % 25 == 0) {
                player.sendStatusMessage(new TextComponentTranslation("regeneration.messages.choice", RegenKeyBinds.ENTER_GRACE.getDisplayName(), RegenKeyBinds.REGEN_NOW.getDisplayName()), true);
			}
		}
		
		// The actual regeneration
		if (!isInGracePeriod() && getSolaceTicks() > 200) {
			setGlowing(false);
			player.dismountRidingEntity();
			player.removePassengers();
			setTicksRegenerating(getTicksRegenerating() + 1);
			
			player.setAbsorptionAmount(RegenConfig.absorbtionLevel * 2);
			
			if (getTicksRegenerating() == 3) {
				if (player.world.isRemote) {
					PlayerUtil.playMovingSound(player, getType().getSound(), SoundCategory.PLAYERS);
				}
				setLivesLeft(getLivesLeft() - 1);
				setTimesRegenerated(getTimesRegenerated() + 1);
				ExplosionUtil.regenerationExplosion(player);
			}
			
			if (getTicksRegenerating() > 0 && getTicksRegenerating() < 100) {
				getType().onStartRegeneration(player);
				
				if (getTicksRegenerating() <= 50) {
					
					String time = "" + (getTimesRegenerated());
					int lastDigit = getTimesRegenerated();
					if (lastDigit > 20)
						while (lastDigit > 10)
							lastDigit -= 10;
						
					if (lastDigit < 3) {
						time = time + I18n.translateToLocalFormatted("regeneration.messages.numsuffix." + lastDigit);
					} else {
						time = time + I18n.translateToLocalFormatted("regeneration.messages.numsuffix.ext");
					}
					
					PlayerUtil.sendMessage(player, new TextComponentTranslation("regeneration.messages.remaining_regens.notification", time, getLivesLeft()), true);
				}
			}
			
			if (getTicksRegenerating() >= 100 && getTicksRegenerating() < 200) {
				getType().onUpdateMidRegen(player);
			}
			
			if (player.getHealth() < player.getMaxHealth()) {
				player.setHealth(player.getHealth() + 1);
			}
			
			if (RegenConfig.resetHunger) {
				FoodStats foodStats = player.getFoodStats();
				foodStats.setFoodLevel(foodStats.getFoodLevel() + 1);
			}
			
			if (RegenConfig.resetOxygen) {
				player.setAir(player.getAir() + 1);
			}
			
			if (getTicksRegenerating() == 200) { // regeneration has finished, reset
				getType().onFinishRegeneration(player);
				setTicksRegenerating(0);
				setRegenerating(false);
				setSolaceTicks(0);
				setInGracePeriod(false);
				setGlowing(false);
				
				player.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, RegenConfig.postRegenerationDuration * 2, RegenConfig.postRegenerationLevel - 1, false, false));
				
				if (player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).hasModifier(slownessModifier)) {
					player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(SLOWNESS_ID);
				}
				
				if (player.world.isRemote) {
					Minecraft.getMinecraft().gameSettings.thirdPersonView = 0;
				} else {
					sync();
				}
			}
		}
		
		// Grace handling
		if (isInGracePeriod()) {
			if (player.ticksExisted % 200 == 0) {
				if (player.getHealth() < player.getMaxHealth()) {
					player.heal(2.0F);
				}
			}
			
			if (getSolaceTicks() == 2) {
				if (!player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).hasModifier(slownessModifier)) {
					player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(slownessModifier);
				}
			}
			
			if (isGlowing()) {
				setTicksGlowing(getTicksGlowing() + 1);
			}
			
			if (getTicksGlowing() >= 600) {
				setInGracePeriod(false);
			}
			
			if (getSolaceTicks() % 220 == 0) {
				//XXX there's nothing here
			}
			
			// Every Minute
			if (getSolaceTicks() % 1200 == 0) {
				setGlowing(true);
				if (player.world.isRemote) {
					PlayerUtil.playMovingSound(player, RegenObjects.Sounds.HAND_GLOW, SoundCategory.PLAYERS);
				}
			}
			
			// Five minutes
			if (getSolaceTicks() == 6000) {
				//XXX there's nothing here
			}
			
			// 14 Minutes - Critical stage start
			if (getSolaceTicks() == 17100) {
				if (player.world.isRemote) {
					PlayerUtil.playMovingSound(player, RegenObjects.Sounds.CRITICAL_STAGE, SoundCategory.PLAYERS);
					player.addPotionEffect(new PotionEffect(Potion.getPotionById(9), 800, 0, false, false)); // could be removed with milk, but I think that's not that bad
				}
			}
			
		}
		
		// CRITICAL STAGE
		if (getSolaceTicks() > 16800 && getSolaceTicks() < 18000) {
			// TODO random damage
			PlayerUtil.sendMessage(player, "regeneration.messages.regen_or_die", true);
		}
		
		// 15 minutes all gone, rip user
		if (getSolaceTicks() == 17999) {
			if (!player.world.isRemote)
				player.setHealth(-1);
			reset();
		}
	}
	
	@Override
	public void reset() {
		setInGracePeriod(false);
		setGlowing(false);
		setTicksGlowing(0);
		setTicksRegenerating(0);
		setRegenerating(false);
		if (RegenConfig.losePowerOnMidRegenDeath)
			setLivesLeft(0);
	}
	
}
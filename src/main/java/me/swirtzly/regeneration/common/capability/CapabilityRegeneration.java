package me.swirtzly.regeneration.common.capability;

import me.swirtzly.regeneration.RegenConfig;
import me.swirtzly.regeneration.RegenerationMod;
import me.swirtzly.regeneration.client.skinhandling.SkinChangingHandler;
import me.swirtzly.regeneration.client.skinhandling.SkinInfo;
import me.swirtzly.regeneration.common.advancements.RegenTriggers;
import me.swirtzly.regeneration.common.entity.EntityLindos;
import me.swirtzly.regeneration.common.traits.DnaHandler;
import me.swirtzly.regeneration.common.types.TypeHandler;
import me.swirtzly.regeneration.debugger.util.DebuggableScheduledAction;
import me.swirtzly.regeneration.handlers.ActingForwarder;
import me.swirtzly.regeneration.handlers.RegenObjects;
import me.swirtzly.regeneration.network.MessageSynchronisationRequest;
import me.swirtzly.regeneration.network.MessageSynchroniseRegeneration;
import me.swirtzly.regeneration.network.NetworkHandler;
import me.swirtzly.regeneration.util.PlayerUtil;
import me.swirtzly.regeneration.util.RegenState;
import me.swirtzly.regeneration.util.RegenUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Sub
 * on 16/09/2018.
 */
public class CapabilityRegeneration implements IRegeneration {
	
	@CapabilityInject(IRegeneration.class)
	public static final Capability<IRegeneration> CAPABILITY = null;
	public static final ResourceLocation CAP_REGEN_ID = new ResourceLocation(RegenerationMod.MODID, "regeneration");
	
	private final EntityPlayer player;
	private final RegenerationStateManager stateManager;
	public String deathSource = "";
	public int lcCoreReserve = 0;
	private boolean didSetup = false, traitActive = true;
	private int regenerationsLeft;
	private RegenState state = RegenState.ALIVE;
	private TypeHandler.RegenType regenType = TypeHandler.RegenType.FIERY;
	
	private String BASE64_SKIN = "NONE";
	
	private SkinInfo.SkinType skinType = SkinInfo.SkinType.ALEX;
	private SkinInfo.SkinType vanillaSkinType = SkinInfo.SkinType.ALEX;
	private SkinChangingHandler.EnumChoices preferredModel = SkinChangingHandler.EnumChoices.EITHER;
	private float primaryRed = 0.93f, primaryGreen = 0.61f, primaryBlue = 0.0f;
	private float secondaryRed = 1f, secondaryGreen = 0.5f, secondaryBlue = 0.18f;
	private ResourceLocation traitLocation = new ResourceLocation(RegenerationMod.MODID, "boring");
	private int ticksAnimating = 0; //Im so sorry
	private boolean syncingToJar = false;
	
	
	/**
	 * WHY THIS IS A SEPERATE FIELD: the hands are glowing if <code>stateManager.handGlowTimer.getTransition() == Transition.HAND_GLOW_TRIGGER</code>, however the state manager isn't available on the client.
	 * This property is synced over to the client to solve this
	 */
	private boolean handsAreGlowingClient;
	
	
	public CapabilityRegeneration() {
		this.player = null;
		this.stateManager = null;
	}
	
	public CapabilityRegeneration(EntityPlayer player) {
		this.player = player;
		if (!player.world.isRemote)
			this.stateManager = new RegenerationStateManager();
		else
			this.stateManager = null;
	}
	
	@Nonnull
	public static IRegeneration getForPlayer(EntityPlayer player) {
		if (player.hasCapability(CAPABILITY, null)) {
			return player.getCapability(CAPABILITY, null);
		}
		throw new IllegalStateException("Missing Regeneration capability: " + player + ", please report this to the issue tracker");
	}
	
	
	@Override
	public void tick() {
		if (!didSetup && player.world.isRemote) {
			NetworkHandler.INSTANCE.sendToServer(new MessageSynchronisationRequest(player));
			didSetup = true;
		}
		
		if (isSyncingToJar() && ticksAnimating >= 250) {
			setSyncingFromJar(false);
			ticksAnimating = 0;
			synchronise();
		} else {
			if (isSyncingToJar()) {
				if (!player.world.isRemote) {
					PlayerUtil.setPerspective((EntityPlayerMP) player, true, false);
				}
			}
		}
		
		
		if (getEncodedSkin().toLowerCase().equals("NONE")) {
			if (getSkinType() != getVanillaDefault()) {
				setSkinType(getVanillaDefault().name());
			}
		}
		
		if (state != RegenState.REGENERATING && !isSyncingToJar()) {
			ticksAnimating = 0;
		} else {
			ticksAnimating++;
		}
		
		if (getRegenerationsLeft() > RegenConfig.regenCapacity && !RegenConfig.infiniteRegeneration) {
			regenerationsLeft = RegenConfig.regenCapacity;
			RegenerationMod.LOG.info("Correcting the amount of Regenerations &s has", player.getName());
		}
		
		DnaHandler.getDnaEntry(getDnaType()).onUpdate(this);
		
		if (!player.world.isRemote && state != RegenState.ALIVE) // ticking only on the server for simplicity
			stateManager.tick();
		
		if (state == RegenState.REGENERATING) {
			TypeHandler.getTypeInstance(regenType).onUpdateMidRegen(player, this);
		}
	}
	
	@Override
	public void synchronise() {
		if (player.world.isRemote)
			throw new IllegalStateException("Don't sync client -> server");
		
		handsAreGlowingClient = state.isGraceful() && stateManager.handGlowTimer.getTransition() == RegenState.Transition.HAND_GLOW_TRIGGER;
		NBTTagCompound nbt = serializeNBT();
		nbt.removeTag("stateManager");
		NetworkHandler.INSTANCE.sendToAll(new MessageSynchroniseRegeneration(player, nbt));
	}
	
	
	@Override
	public NBTTagCompound serializeNBT() {
		NBTTagCompound nbt = new NBTTagCompound();
		nbt.setString("state", state.toString());
		nbt.setInteger("regenerationsLeft", regenerationsLeft);
		nbt.setTag("style", getStyle());
		if (regenType != null) {
			nbt.setString("type_id", regenType.name());
		} else {
			regenType = TypeHandler.RegenType.FIERY;
		}
		nbt.setString("base64_skin", BASE64_SKIN);
		nbt.setString("skinType", skinType.name());
		nbt.setString("preferredModel", preferredModel.name());
		nbt.setBoolean("handsAreGlowing", handsAreGlowingClient);
		if (traitLocation != null) {
			nbt.setString("regen_dna", traitLocation.toString());
		} else {
			nbt.setString("regen_dna", DnaHandler.DNA_BORING.getRegistryName().toString());
		}
		nbt.setBoolean("traitActive", traitActive);
		nbt.setInteger("lc_regen", lcCoreReserve);
		nbt.setString("v_type", vanillaSkinType.name());
		nbt.setInteger("ticks_animating", ticksAnimating);
		nbt.setBoolean("jar", syncingToJar);
		if (!player.world.isRemote)
			nbt.setTag("stateManager", stateManager.serializeNBT());
		return nbt;
	}
	
	@Override
	public void deserializeNBT(NBTTagCompound nbt) {
		regenerationsLeft = Math.min(RegenConfig.regenCapacity, nbt.getInteger(nbt.hasKey("livesLeft") ? "livesLeft" : "regenerationsLeft"));
		
		//TODO could probably use a utility method that checks is a key exists and returns a default value if it doesn't
		if (nbt.hasKey("skinType")) {
			setSkinType(nbt.getString("skinType"));
		} else {
			setSkinType(RegenUtil.isSlimSkin(player.getUniqueID()) ? "ALEX" : "STEVE");
		}
		
		if (nbt.hasKey("preferredModel")) {
			setPreferredModel(nbt.getString("preferredModel"));
		} else {
			setPreferredModel("ALEX");
		}
		
		
		if (nbt.hasKey("regenerationsLeft")) {
			regenerationsLeft = nbt.getInteger("regenerationsLeft");
		}
		
		if (nbt.hasKey("traitActive")) {
			setDnaActive(nbt.getBoolean("traitAlive"));
		} else {
			setDnaActive(true);
		}
		
		if (nbt.hasKey("regen_dna")) {
			setDnaType(new ResourceLocation(nbt.getString("regen_dna")));
		} else {
			setDnaType(DnaHandler.DNA_BORING.getRegistryName());
		}
		
		if (nbt.hasKey("handsAreGlowing")) {
			handsAreGlowingClient = nbt.getBoolean("handsAreGlowing");
		}
		
		if (nbt.hasKey("lc_regen")) {
			lcCoreReserve = nbt.getInteger("lc_regen");
		}
		
		if (nbt.hasKey("v_type")) {
			vanillaSkinType = SkinInfo.SkinType.valueOf(nbt.getString("v_type"));
		}
		
		if (nbt.hasKey("ticks_animating")) {
			ticksAnimating = nbt.getInteger("ticks_animating");
		}
		
		// v1.3+ has a sub-tag 'style' for styles. If it exists we pull the data from this tag, otherwise we pull it from the parent tag
		setStyle(nbt.hasKey("style") ? nbt.getCompoundTag("style") : nbt);
		
		if (nbt.hasKey("type_id")) // v1.3+ saves have a type tag
			regenType = TypeHandler.RegenType.valueOf(nbt.getString("type_id"));
		else // for previous save versions set to default 'fiery' type
			regenType = TypeHandler.RegenType.FIERY;
		
		state = nbt.hasKey("state") ? RegenState.valueOf(nbt.getString("state")) : RegenState.ALIVE; // I need to check for versions before the new state-ticking system
		setEncodedSkin(nbt.getString("base64_skin"));
		
		if (nbt.hasKey("stateManager"))
			if (stateManager != null) {
				stateManager.deserializeNBT(nbt.getCompoundTag("stateManager"));
			}
		
		if (nbt.hasKey("jar")) {
			syncingToJar = nbt.getBoolean("jar");
		}
	}
	
	
	@Override
	public int getRegenerationsLeft() {
		return regenerationsLeft;
	}
	
	@Deprecated
	@Override
	public void setRegenerationsLeft(int amount) {
		regenerationsLeft = amount;
	}
	
	
	@Override
	public EntityPlayer getPlayer() {
		return player;
	}
	
	@Override
	public TypeHandler.RegenType getType() {
		return regenType;
	}
	
	
	@Override
	public String getEncodedSkin() {
		return BASE64_SKIN;
	}
	
	@Override
	public void setEncodedSkin(String string) {
		BASE64_SKIN = string;
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
	public Vec3d getPrimaryColor() {
		return new Vec3d(primaryRed, primaryGreen, primaryBlue);
	}
	
	@Override
	public Vec3d getSecondaryColor() {
		return new Vec3d(secondaryRed, secondaryGreen, secondaryBlue);
	}
	
	
	@Override
	public void receiveRegenerations(int amount) {
		if (RegenConfig.infiniteRegeneration)
			regenerationsLeft = RegenConfig.regenCapacity;
		else
			regenerationsLeft += amount;
		synchronise();
	}
	
	@Override
	public void extractRegeneration(int amount) {
		if (RegenConfig.infiniteRegeneration)
			regenerationsLeft = RegenConfig.regenCapacity;
		else
			regenerationsLeft -= amount;
		synchronise();
	}
	
	
	@Override
	public SkinInfo.SkinType getSkinType() {
		return skinType;
	}
	
	@Override
	public void setSkinType(String skinType) {
		this.skinType = SkinInfo.SkinType.valueOf(skinType);
	}
	
	@Override
	public SkinChangingHandler.EnumChoices getPreferredModel() {
		return preferredModel;
	}
	
	@Override
	public void setPreferredModel(String skinType) {
		this.preferredModel = SkinChangingHandler.EnumChoices.valueOf(skinType);
	}
	
	
	@Override
	public boolean areHandsGlowing() {
		return handsAreGlowingClient;
	}
	
	
	@Override
	public String getDeathSource() {
		return deathSource;
	}
	
	@Override
	public void setDeathSource(String source) {
		deathSource = source;
	}
	
	@Override
	public ResourceLocation getDnaType() {
		return traitLocation;
	}
	
	@Override
	public void setDnaType(ResourceLocation resgitryName) {
		this.traitLocation = resgitryName;
	}
	
	@Override
	public boolean isDnaActive() {
		return traitActive;
	}
	
	@Override
	public void setDnaActive(boolean alive) {
		traitActive = alive;
	}
	
	@Override
	public SkinInfo.SkinType getVanillaDefault() {
		return vanillaSkinType;
	}
	
	@Override
	public void setVanillaSkinType(SkinInfo.SkinType type) {
		vanillaSkinType = type;
	}
	
	@Override
	public int getAnimationTicks() {
		return ticksAnimating;
	}
	
	@Override
	public void setAnimationTicks(int ticks) {
		ticksAnimating = ticks;
	}
	
	@Override
	public void setSyncingFromJar(boolean syncing) {
		syncingToJar = syncing;
	}
	
	@Override
	public boolean isSyncingToJar() {
		return syncingToJar;
	}
	
	@Override
	public IRegenerationStateManager getStateManager() {
		return stateManager;
	}
	
	@Override
	public RegenState getState() {
		return state;
	}
	
	
	@Override
	public void triggerRegeneration() {
		if (player.world.isRemote)
			throw new IllegalStateException("Triggering regeneration via capability instance on the client side");
		stateManager.triggerRegeneration();
	}
	
	
	/**
	 * ONLY EXISTS ON THE SERVER SIDE
	 */
	public class RegenerationStateManager implements IRegenerationStateManager {
		
		private final Map<RegenState.Transition, Runnable> transitionCallbacks;
		private DebuggableScheduledAction nextTransition, handGlowTimer;
		
		private RegenerationStateManager() {
			this.transitionCallbacks = new HashMap<>();
			transitionCallbacks.put(RegenState.Transition.ENTER_CRITICAL, this::enterCriticalPhase);
			transitionCallbacks.put(RegenState.Transition.CRITICAL_DEATH, this::midSequenceKill);
			transitionCallbacks.put(RegenState.Transition.FINISH_REGENERATION, this::finishRegeneration);
			transitionCallbacks.put(RegenState.Transition.END_POST, this::endPost);
			
			Runnable err = () -> {
				throw new IllegalStateException("Can't use HAND_GLOW_* transitions as state transitions");
			};
			transitionCallbacks.put(RegenState.Transition.HAND_GLOW_START, err);
			transitionCallbacks.put(RegenState.Transition.HAND_GLOW_TRIGGER, err);
		}
		
		@SuppressWarnings("deprecation")
		private void scheduleTransitionInTicks(RegenState.Transition transition, long inTicks) {
			if (nextTransition != null && nextTransition.getTicksLeft() > 0)
				throw new IllegalStateException("Overwriting non-completed/cancelled transition: " +
						"\n Attempted Transition: " + transition.name() +
						"\n Current: " + nextTransition.transition.name() +
						"\n Affected Player: " + player.getName());
			
			if (transition == RegenState.Transition.HAND_GLOW_START || transition == RegenState.Transition.HAND_GLOW_TRIGGER)
				throw new IllegalStateException("Can't use HAND_GLOW_* transitions as state transitions");
			
			nextTransition = new DebuggableScheduledAction(transition, player, transitionCallbacks.get(transition), inTicks);
		}
		
		private void scheduleTransitionInSeconds(RegenState.Transition transition, long inSeconds) {
			scheduleTransitionInTicks(transition, inSeconds * 20);
		}
		
		
		@SuppressWarnings("deprecation")
		private void scheduleNextHandGlow() {
			if (state.isGraceful() && handGlowTimer.getTicksLeft() > 0)
				throw new IllegalStateException("Overwriting running hand-glow timer with new next hand glow");
			handGlowTimer = new DebuggableScheduledAction(RegenState.Transition.HAND_GLOW_START, player, this::scheduleHandGlowTrigger, RegenConfig.grace.handGlowInterval * 20);
			synchronise();
		}
		
		@SuppressWarnings("deprecation")
		private void scheduleHandGlowTrigger() {
			if (state.isGraceful() && handGlowTimer.getTicksLeft() > 0)
				throw new IllegalStateException("Overwriting running hand-glow timer with trigger timer prematurely");
			handGlowTimer = new DebuggableScheduledAction(RegenState.Transition.HAND_GLOW_TRIGGER, player, this::triggerRegeneration, RegenConfig.grace.handGlowTriggerDelay * 20);
			ActingForwarder.onHandsStartGlowing(CapabilityRegeneration.this);
			synchronise();
		}
		
		
		@Override
		public boolean onKilled(DamageSource source) {
			
			if (source == DamageSource.IN_WALL || source == DamageSource.CRAMMING) {
				return false;
			}
			
			if (state == RegenState.ALIVE) {
				if (!canRegenerate()) // that's too bad :(
					return false;
				
				// We're entering grace period...
				scheduleTransitionInSeconds(RegenState.Transition.ENTER_CRITICAL, RegenConfig.grace.gracePhaseLength);
				scheduleHandGlowTrigger();
				
				state = RegenState.GRACE;
				synchronise();
				ActingForwarder.onEnterGrace(CapabilityRegeneration.this);
				return true;
				
			} else if (state.isGraceful()) {
				
				// We're being forced to regenerate...
				triggerRegeneration();
				return true;
				
			} else if (state == RegenState.REGENERATING) {
				
				// We've been killed mid regeneration!
				nextTransition.cancel(); // ... cancel the finishing of the regeneration
				midSequenceKill();
				return false;
				
			} else if (state == RegenState.POST) {
				state = RegenState.ALIVE;
				nextTransition.cancel();
				midSequenceKill();
				return false;
			} else
				throw new IllegalStateException("Unknown state: " + state);
		}
		
		@Override
		public void onPunchEntity(LivingHurtEvent event) {
			EntityLivingBase entity = event.getEntityLiving();
			// We're healing mobs...
			if (state.isGraceful() && entity.getHealth() < entity.getMaxHealth() && areHandsGlowing() && player.isSneaking()) { // ... check if we're in grace and if the mob needs health
				float healthNeeded = entity.getMaxHealth() - entity.getHealth();
				entity.heal(healthNeeded);
				PlayerUtil.sendMessage(player, new TextComponentTranslation("message.regeneration.healed", entity.getName()), true);
				event.setAmount(0.0F);
				player.attackEntityFrom(RegenObjects.REGEN_DMG_HEALING, healthNeeded);
			}
		}
		
		@Override
		public void onPunchBlock(PlayerInteractEvent.LeftClickBlock e) {
			if (getState().isGraceful() && areHandsGlowing()) {
				
				IBlockState block = e.getWorld().getBlockState(e.getPos());
				
				if (block.getBlock() == Blocks.SNOW || block.getBlock() == Blocks.SNOW_LAYER) {
					e.getWorld().playSound(null, e.getPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 1, 1);
				}
				
				handGlowTimer.cancel();
				scheduleNextHandGlow();
				if (!player.world.isRemote) {
					RegenTriggers.CHANGE_REFUSAL.trigger((EntityPlayerMP) player);
					PlayerUtil.sendMessage(player, new TextComponentTranslation("regeneration.messages.regen_delayed"), true);
				}
				e.setCanceled(true); //It got annoying in creative to break something
			}
		}
		
		private void tick() {
			if (player.world.isRemote)
				throw new IllegalStateException("Ticking state manager on the client"); // the state manager shouldn't even exist on the client
			if (state == RegenState.ALIVE)
				throw new IllegalStateException("Ticking dormant state manager (state == ALIVE)"); // would NPE when ticking the transition, but this is a more clear message
			
			if (state.isGraceful())
				handGlowTimer.tick();
			
			ActingForwarder.onRegenTick(CapabilityRegeneration.this);
			nextTransition.tick();
			
			if (state == RegenState.POST) {
				ActingForwarder.onPerformingPost(CapabilityRegeneration.this);
			}
		}
		
		private void triggerRegeneration() {
			// We're starting a regeneration!
			state = RegenState.REGENERATING;
			
			if (RegenConfig.sendRegenDeathMessages) {
				TextComponentTranslation text = new TextComponentTranslation("regeneration.messages.regen_chat_message", player.getName());
				text.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(getDeathSource())));
				PlayerUtil.sendMessageToAll(text);
			}
			
			nextTransition.cancel(); // ... cancel any state shift we had planned
			if (state.isGraceful())
				handGlowTimer.cancel();
			scheduleTransitionInTicks(RegenState.Transition.FINISH_REGENERATION, TypeHandler.getTypeInstance(regenType).getAnimationLength());
			
			ActingForwarder.onRegenTrigger(CapabilityRegeneration.this);
			TypeHandler.getTypeInstance(regenType).onStartRegeneration(player, CapabilityRegeneration.this);
			synchronise();
		}
		
		private void enterCriticalPhase() {
			// We're entering critical phase...
			state = RegenState.GRACE_CRIT;
			scheduleTransitionInSeconds(RegenState.Transition.CRITICAL_DEATH, RegenConfig.grace.criticalPhaseLength);
			ActingForwarder.onGoCritical(CapabilityRegeneration.this);
			synchronise();
		}
		
		private void midSequenceKill() {
			state = RegenState.ALIVE;
			nextTransition = null;
			handGlowTimer = null;
			TypeHandler.getTypeInstance(regenType).onFinishRegeneration(player, CapabilityRegeneration.this);
			player.setHealth(-1);
			setDnaType(DnaHandler.DNA_BORING.getRegistryName());
			if (RegenConfig.loseRegensOnDeath) {
				extractRegeneration(getRegenerationsLeft());
			}
			
			synchronise();
		}
		
		private void endPost() {
			state = RegenState.ALIVE;
			synchronise();
			nextTransition = null;
			
			PlayerUtil.sendMessage(player, new TextComponentTranslation("regeneration.messages.post_ended"), true);
			
			if (player.world.rand.nextBoolean()) {
				EntityLindos lindos = new EntityLindos(player.world);
				lindos.setLocationAndAngles(player.posX, player.posY + player.getEyeHeight(), player.posZ, 0, 0);
				player.world.spawnEntity(lindos);
				player.world.playSound(null, player.getPosition(), RegenObjects.Sounds.REGEN_BREATH, SoundCategory.PLAYERS, 1, 1);
			}
		}
		
		private void finishRegeneration() {
			state = RegenState.POST;
			scheduleTransitionInSeconds(RegenState.Transition.END_POST, player.world.rand.nextInt(300));
			handGlowTimer = null;
			TypeHandler.getTypeInstance(regenType).onFinishRegeneration(player, CapabilityRegeneration.this);
			ActingForwarder.onRegenFinish(CapabilityRegeneration.this);
			synchronise();
		}
		
		@Override
		@Deprecated
		/** @deprecated Debug purposes */
		public Pair<RegenState.Transition, Long> getScheduledEvent() {
			return nextTransition == null ? null : Pair.of(nextTransition.transition, nextTransition.getTicksLeft());
		}
		
		@Override
		@Deprecated
		/** @deprecated Debug purposes */
		public void fastForward() {
			while (!nextTransition.tick()) ;
		}
		
		@Override
		@Deprecated
		/** @deprecated Debug purposes */
		public void fastForwardHandGlow() {
			while (!handGlowTimer.tick()) ;
		}
		
		@Override
		public double getStateProgress() {
			return nextTransition.getProgress();
		}
		
		@SuppressWarnings("deprecation")
		@Override
		public NBTTagCompound serializeNBT() {
			NBTTagCompound nbt = new NBTTagCompound();
			if (nextTransition != null && nextTransition.getTicksLeft() >= 0) {
				nbt.setString("transitionId", nextTransition.transition.toString());
				nbt.setLong("transitionInTicks", nextTransition.getTicksLeft());
			}
			
			if (handGlowTimer != null && handGlowTimer.getTicksLeft() >= 0) {
				nbt.setString("handGlowState", handGlowTimer.transition.toString());
				nbt.setLong("handGlowScheduledTicks", handGlowTimer.getTicksLeft());
			}
			return nbt;
		}
		
		@Override
		public void deserializeNBT(NBTTagCompound nbt) {
			if (nbt.hasKey("transitionId"))
				scheduleTransitionInTicks(RegenState.Transition.valueOf(nbt.getString("transitionId")), nbt.getLong("transitionInTicks"));
			
			if (nbt.hasKey("handGlowState")) {
				RegenState.Transition transition = RegenState.Transition.valueOf(nbt.getString("handGlowState"));
				
				Runnable callback;
				if (transition == RegenState.Transition.HAND_GLOW_START)
					callback = this::scheduleHandGlowTrigger;
				else if (transition == RegenState.Transition.HAND_GLOW_TRIGGER)
					callback = this::triggerRegeneration;
				else
					throw new IllegalStateException("Illegal hand glow timer transition");
				
				handGlowTimer = new DebuggableScheduledAction(transition, player, callback, nbt.getLong("handGlowScheduledTicks"));
			}
		}
		
	}
	
}

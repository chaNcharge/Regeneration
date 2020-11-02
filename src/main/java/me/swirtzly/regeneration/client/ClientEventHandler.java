package me.swirtzly.regeneration.client;

import me.swirtzly.regeneration.RegenConfig;
import me.swirtzly.regeneration.RegenerationMod;
import me.swirtzly.regeneration.client.animation.AnimationContext;
import me.swirtzly.regeneration.client.animation.AnimationHandler;
import me.swirtzly.regeneration.client.animation.ModelRotationEvent;
import me.swirtzly.regeneration.client.animation.RenderCallbackEvent;
import me.swirtzly.regeneration.client.gui.GuiPreferences;
import me.swirtzly.regeneration.client.gui.parts.InventoryTabRegeneration;
import me.swirtzly.regeneration.client.skinhandling.SkinChangingHandler;
import me.swirtzly.regeneration.common.capability.CapabilityRegeneration;
import me.swirtzly.regeneration.common.capability.IRegeneration;
import me.swirtzly.regeneration.common.item.ItemArchInterface;
import me.swirtzly.regeneration.common.item.arch.capability.CapabilityArch;
import me.swirtzly.regeneration.common.item.arch.capability.IArch;
import me.swirtzly.regeneration.common.traits.DnaHandler;
import me.swirtzly.regeneration.common.types.TypeHandler;
import me.swirtzly.regeneration.handlers.RegenObjects;
import me.swirtzly.regeneration.util.ClientUtil;
import me.swirtzly.regeneration.util.EnumCompatModids;
import me.swirtzly.regeneration.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ChatType;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import static me.swirtzly.regeneration.asm.RegenClientHooks.handleShader;
import static me.swirtzly.regeneration.util.PlayerUtil.RegenState.*;

/**
 * Created by Sub on 16/09/2018.
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = RegenerationMod.MODID)
public class ClientEventHandler {

    public static final ResourceLocation[] SHADERS_TEXTURES = new ResourceLocation[]{new ResourceLocation("shaders/post/notch.json"), new ResourceLocation("shaders/post/fxaa.json"), new ResourceLocation("shaders/post/art.json"), new ResourceLocation("shaders/post/bumpy.json"), new ResourceLocation("shaders/post/blobs2.json"), new ResourceLocation("shaders/post/pencil.json"), new ResourceLocation("shaders/post/color_convolve.json"), new ResourceLocation("shaders/post/deconverge.json"), new ResourceLocation("shaders/post/flip.json"), new ResourceLocation("shaders/post/invert.json"), new ResourceLocation("shaders/post/ntsc.json"), new ResourceLocation("shaders/post/outline.json"), new ResourceLocation("shaders/post/phosphor.json"), new ResourceLocation("shaders/post/scan_pincushion.json"), new ResourceLocation("shaders/post/sobel.json"), new ResourceLocation("shaders/post/bits.json"), new ResourceLocation("shaders/post/desaturate.json"), new ResourceLocation("shaders/post/green.json"), new ResourceLocation("shaders/post/blur.json"), new ResourceLocation("shaders/post/wobble.json"), new ResourceLocation("shaders/post/blobs.json"), new ResourceLocation("shaders/post/antialias.json"), new ResourceLocation("shaders/post/creeper.json"), new ResourceLocation("shaders/post/spider.json")};
    public static final ResourceLocation TEX = new ResourceLocation(RegenerationMod.MODID, "textures/gui/widgets.png");
    public static EnumHandSide SIDE = null;

    @SubscribeEvent
    public static void onColorFog(EntityViewRenderEvent.RenderFogEvent.FogColors e) {
        if (Minecraft.getMinecraft().getRenderViewEntity() instanceof EntityPlayer) {
            IRegeneration data = CapabilityRegeneration.getForPlayer(Minecraft.getMinecraft().player);
            if (data.getType() == TypeHandler.RegenType.LAY_FADE && data.getState() == REGENERATING) {
                e.setRed((float) data.getPrimaryColor().x);
                e.setGreen((float) data.getPrimaryColor().y);
                e.setBlue((float) data.getPrimaryColor().z);
            }
        }
    }

    @SubscribeEvent
    public static void onAction(GuiScreenEvent.ActionPerformedEvent event) {
        if (event.getButton() instanceof InventoryTabRegeneration) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiPreferences());
        }
    }

    @SubscribeEvent
    public static void onClientUpdate(LivingEvent.LivingUpdateEvent e) {
        if (!(e.getEntity() instanceof EntityPlayer) || Minecraft.getMinecraft().player == null) return;

        EntityPlayer player = (EntityPlayer) e.getEntity();
        UUID clientUUID = Minecraft.getMinecraft().player.getUniqueID();
        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);

        // Horrible Sound repairs
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (player.ticksExisted == 50) {
                if (SIDE != null) {
                    SIDE = Minecraft.getMinecraft().gameSettings.mainHand;
                }

                if (cap.areHandsGlowing()) {
                    ClientUtil.playSound(cap.getPlayer(), RegenObjects.Sounds.HAND_GLOW.getRegistryName(), SoundCategory.PLAYERS, true, () -> !cap.areHandsGlowing(), 0.5F);
                }

                if (cap.getState() == REGENERATING) {
                    ClientUtil.playSound(cap.getPlayer(), RegenObjects.Sounds.REGENERATION_0.getRegistryName(), SoundCategory.PLAYERS, true, () -> !cap.getState().equals(REGENERATING), 1.0F);
                }

                if (cap.getState().isGraceful() && clientUUID == player.getUniqueID()) {
                    ClientUtil.playSound(cap.getPlayer(), RegenObjects.Sounds.CRITICAL_STAGE.getRegistryName(), SoundCategory.PLAYERS, true, () -> !cap.getState().equals(GRACE_CRIT), 1F);
                    ClientUtil.playSound(cap.getPlayer(), RegenObjects.Sounds.HEART_BEAT.getRegistryName(), SoundCategory.PLAYERS, true, () -> !cap.getState().isGraceful(), 0.2F);
                    ClientUtil.playSound(cap.getPlayer(), RegenObjects.Sounds.GRACE_HUM.getRegistryName(), SoundCategory.AMBIENT, true, () -> cap.getState() != GRACE, 1.5F);
                }
            }

        });

        if (cap.getAnimationTicks() == 100 && cap.getState() == REGENERATING) {
            if (Minecraft.getMinecraft().player.getUniqueID().equals(cap.getPlayer().getUniqueID())) {
                SkinChangingHandler.sendSkinUpdate(cap.getPlayer().world.rand, cap.getPlayer());
            }
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public static void onAnimate(ModelRotationEvent ev) {
        if (EnumCompatModids.LCCORE.isLoaded()) return;
        if (ev.getEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) ev.getEntity();
            IRegeneration data = CapabilityRegeneration.getForPlayer(player);
            AnimationContext context = new AnimationContext(ev.model, player, ev.limbSwing, ev.limbSwingAmount, ev.ageInTicks, ev.netHeadYaw, ev.headPitch);
            if (data.getState() == REGENERATING) {
                ev.setCanceled(TypeHandler.getTypeInstance(data.getType()).getRenderer().onAnimateRegen(context));
            } else {
                AnimationHandler.animate(context);
            }
        }
    }


    @SubscribeEvent
    public static void onRenderGui(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) return;

        EntityPlayerSP player = Minecraft.getMinecraft().player;
        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);

        switch (cap.getState()) {
            case REGENERATING:
                RenderUtil.renderVignette(cap.getSecondaryColor(), 0.5F, cap.getState());
                if (cap.getAnimationTicks() < 3) {
                    handleShader();
                }
                break;

            case POST:
                if (player.hurtTime > 0 || player.getActivePotionEffect(MobEffects.NAUSEA) != null) {
                    RenderUtil.renderVignette(cap.getSecondaryColor(), 0.5F, cap.getState());
                }

                if (player.hurtTime == 1 || player.ticksExisted % 600 == 0) {
                    handleShader();
                }
                break;
            case ALIVE:
                break;
            case GRACE_CRIT:
            case GRACE:
                RenderUtil.renderVignette(cap.getSecondaryColor(), 0.5F, cap.getState());

                break;
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        if (e.getName().equals("entity.generic.explode")) {
            ISound sound = PositionedSoundRecord.getRecord(SoundEvents.ENTITY_GENERIC_EXPLODE, 1F, 0.2F);
            mc.world.playerEntities.forEach(player -> {
                if (mc.player != player && mc.player.getDistance(player) < 40) {
                    if (CapabilityRegeneration.getForPlayer(player).getState().equals(REGENERATING)) {
                        e.setResultSound(sound);
                    }
                }
            });

            if (CapabilityRegeneration.getForPlayer(Minecraft.getMinecraft().player).getState() == REGENERATING) {
                e.setResultSound(sound);
            }
        }

    }

    @SubscribeEvent
    public static void onSetupFogDensity(EntityViewRenderEvent.RenderFogEvent.FogDensity event) {
        if (Minecraft.getMinecraft().getRenderViewEntity() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) Minecraft.getMinecraft().getRenderViewEntity();
            IRegeneration data = CapabilityRegeneration.getForPlayer(player);

            if (data.getState() == GRACE_CRIT) {
                GlStateManager.setFog(GlStateManager.FogMode.EXP);
                event.setCanceled(true);
                float amount = MathHelper.cos(data.getPlayer().ticksExisted * 0.06F) * -0.09F;
                event.setDensity(amount);
            }

            if (data.getType() == TypeHandler.RegenType.LAY_FADE && data.getAnimationTicks() > 0) {
                GlStateManager.setFog(GlStateManager.FogMode.EXP);
                event.setCanceled(true);
                float opacity = MathHelper.clamp(MathHelper.sin((player.ticksExisted + Minecraft.getMinecraft().getRenderPartialTicks()) / 10F) * 0.1F + 0.1F, 0.11F, 1F);
                event.setDensity(opacity);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onClientChatRecieved(ClientChatReceivedEvent e) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (e.getType() != ChatType.CHAT) return;
        if (CapabilityRegeneration.getForPlayer(player).getState() != POST) return;

        if (player.world.rand.nextBoolean()) {
            String message = e.getMessage().getUnformattedText();
            TextComponentString newMessage = new TextComponentString("");
            String[] words = message.split(" ");
            for (String word : words) {
                if (word.equals(words[0])) {
                    TextComponentString name = new TextComponentString(word + " ");
                    newMessage.appendSibling(name);
                    continue;
                }
                if (player.world.rand.nextBoolean()) {
                    TextComponentString txtComp = new TextComponentString(getColoredText("&k" + word + "&r "));
                    txtComp.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(word)));
                    newMessage.appendSibling(txtComp);
                } else {
                    TextComponentString txtComp = new TextComponentString(word + " ");
                    newMessage.appendSibling(txtComp);
                }
            }
            e.setMessage(newMessage);
        }
    }

    public static String getColoredText(String msg) {
        return msg.replaceAll("&", String.valueOf('\u00a7'));
    }

    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent ev) {
        RegenObjects.ITEMS.forEach(RenderUtil::setItemRender);
        RegenObjects.ITEM_BLOCKS.forEach(RenderUtil::setItemRender);

        RegenObjects.ITEMS = new ArrayList<>();
        RegenObjects.ITEM_BLOCKS = new ArrayList<>();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) e.getEntityLiving();
            if (player.getUniqueID().equals(Minecraft.getMinecraft().player.getUniqueID())) {
                ClientUtil.sendSkinResetPacket();
            }
        }
    }


    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = Minecraft.getMinecraft().player;

        float factor = 0.2F;
        if (player.getHeldItemMainhand().getItem() != Items.AIR || mc.gameSettings.thirdPersonView > 0) return;

        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);
        boolean flag = cap.getType() == TypeHandler.RegenType.LAY_FADE && cap.getState() == REGENERATING;
        e.setCanceled(flag);

        if (!cap.areHandsGlowing()) return;

        GlStateManager.pushMatrix();

        float leftHandedFactor = mc.gameSettings.mainHand.equals(EnumHandSide.RIGHT) ? 1 : -1;
        GlStateManager.translate(0.33F * leftHandedFactor, -0.23F, -0.5F); // move in place
        GlStateManager.translate(-.8F * player.swingProgress * leftHandedFactor, -.8F * player.swingProgress, -.4F * player.swingProgress); // compensate for 'punching' motion
        GlStateManager.translate(-(player.renderArmYaw - player.prevRenderArmYaw) / 400F, (player.renderArmPitch - player.prevRenderArmPitch) / 500F, 0); // compensate for 'swinging' motion

        RenderUtil.setupRenderLightning();
        GlStateManager.rotate((mc.player.ticksExisted + RenderUtil.renderTick) / 2F, 0, 1, 0);
        for (int i = 0; i < 15; i++) {
            GlStateManager.rotate((mc.player.ticksExisted + RenderUtil.renderTick) * i / 70F, 1, 1, 0);
            Vec3d primaryColor = cap.getPrimaryColor();
            Random rand = player.world.rand;
            RenderUtil.drawGlowingLine(new Vec3d((-factor / 2F) + rand.nextFloat() * factor, (-factor / 2F) + rand.nextFloat() * factor, (-factor / 2F) + rand.nextFloat() * factor), new Vec3d((-factor / 2F) + rand.nextFloat() * factor, (-factor / 2F) + rand.nextFloat() * factor, (-factor / 2F) + rand.nextFloat() * factor), 0.1F, primaryColor, 0);
        }
        RenderUtil.finishRenderLightning();

        GlStateManager.popMatrix();

    }

    @SubscribeEvent
    public static void onRenderCallBack(RenderCallbackEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) event.getEntityLiving();
            IRegeneration data = CapabilityRegeneration.getForPlayer(player);
            TypeHandler.RegenType type = data.getType();
            if (data.getState() == REGENERATING) {
                TypeHandler.getTypeInstance(type).getRenderer().onRenderCallBack(event);
            }

            if (data.getState() == GRACE_CRIT) {
                GlStateManager.translate(0, 0.125D, 0);
            }

            ModelBase model = event.getRenderer().getMainModel();
            if (model instanceof ModelPlayer) {
                ModelPlayer modelPlayer = (ModelPlayer) model;
                if (data.hasDroppedHand()) {
                    if (data.getCutoffHand() == EnumHandSide.RIGHT) {
                        modelPlayer.bipedRightArmwear.isHidden = modelPlayer.bipedRightArm.isHidden = true;
                    } else {
                        modelPlayer.bipedRightArmwear.isHidden = modelPlayer.bipedRightArm.isHidden = false;
                    }
                    if (data.getCutoffHand() == EnumHandSide.LEFT) {
                        modelPlayer.bipedLeftArmwear.isHidden = modelPlayer.bipedLeftArm.isHidden = true;
                    } else {
                        modelPlayer.bipedLeftArmwear.isHidden = modelPlayer.bipedLeftArm.isHidden = false;
                    }
                } else {
                    modelPlayer.bipedLeftArmwear.isHidden = modelPlayer.bipedLeftArm.isHidden = false;
                    modelPlayer.bipedRightArmwear.isHidden = modelPlayer.bipedRightArm.isHidden = false;
                }

            }
        }
    }


    @SubscribeEvent
    public static void onToolTip(ItemTooltipEvent event) {
        if (event.getItemStack().hasCapability(CapabilityArch.CAPABILITY, null)) {
            IArch data = CapabilityArch.getForStack(event.getItemStack());
            DnaHandler.IDna trait = DnaHandler.getDnaEntry(data.getSavedTrait());
            ItemArchInterface.readSync(event.getItemStack());
            if (data.getArchStatus() == IArch.ArchStatus.ARCH_ITEM) {
                event.getToolTip().add(new TextComponentTranslation("regeneration.tooltip.arch_trait", new TextComponentTranslation(trait.getLangKey()).getUnformattedComponentText()).getUnformattedComponentText());
                event.getToolTip().add(new TextComponentTranslation("regeneration.tooltip.stored_regens", data.getRegenAmount()).getUnformattedComponentText());
            }
        }
    }

    @SubscribeEvent
    public static void onRenderGameOverlayPre(RenderGameOverlayEvent.Pre e) {
        IRegeneration data = CapabilityRegeneration.getForPlayer(Minecraft.getMinecraft().player);
        if (RegenConfig.coolCustomBarThings) {
            if (data.getRegenerationsLeft() > 0 && data.getState() != ALIVE) {
                if (e.getType() == RenderGameOverlayEvent.ElementType.HOTBAR) {
                    GlStateManager.pushMatrix();
                    Minecraft mc = Minecraft.getMinecraft();
                    mc.getTextureManager().bindTexture(TEX);
                    float regensProgress = (float) data.getRegenerationsLeft() / RegenConfig.regenCapacity;
                    mc.ingameGUI.drawTexturedModalRect(e.getResolution().getScaledWidth() / 2 - 91, e.getResolution().getScaledHeight() - 86, 0, 0, 182, 5);
                    mc.ingameGUI.drawTexturedModalRect(e.getResolution().getScaledWidth() / 2 - 91, e.getResolution().getScaledHeight() - 86, 0, 5, (int) (182.0F * regensProgress), 5);

                    mc.ingameGUI.drawTexturedModalRect(e.getResolution().getScaledWidth() / 2 - 91, 10, 0, 0, 182, 5);
                    mc.ingameGUI.drawTexturedModalRect(e.getResolution().getScaledWidth() / 2 - 91, 10, 0, 5, (int) (182.0F * data.getProgress()), 5);

                    String text = data.areHandsGlowing() ? new TextComponentTranslation("transition.regeneration.hand_glow").getUnformattedComponentText() : data.getState().getText().getUnformattedComponentText();
                    int length = mc.fontRenderer.getStringWidth(text);
                    drawStringWithOutline(text, e.getResolution().getScaledWidth() / 2 - length / 2, 8, 16761115, 0);


                    String regensLeft = String.valueOf(data.getRegenerationsLeft());
                    int regensLeftLength = mc.fontRenderer.getStringWidth(regensLeft);
                    drawStringWithOutline(regensLeft, e.getResolution().getScaledWidth() / 2 - regensLeftLength / 2, e.getResolution().getScaledHeight() - 88, 16761115, 0);
                    GlStateManager.popMatrix();
                }
            }
        }
    }


    public static void drawStringWithOutline(String string, int posX, int posY, int fontColor, int outlineColor) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.fontRenderer.drawString(string, posX + 1, posY, outlineColor);
        mc.fontRenderer.drawString(string, posX - 1, posY, outlineColor);
        mc.fontRenderer.drawString(string, posX, posY + 1, outlineColor);
        mc.fontRenderer.drawString(string, posX, posY - 1, outlineColor);
        mc.fontRenderer.drawString(string, posX, posY, fontColor);
    }


}

package me.swirtzly.regeneration.client.skinhandling;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import me.swirtzly.regeneration.RegenConfig;
import me.swirtzly.regeneration.RegenerationMod;
import me.swirtzly.regeneration.client.image.ImageBufferDownloadAlt;
import me.swirtzly.regeneration.client.image.ImageDownloadAlt;
import me.swirtzly.regeneration.common.capability.CapabilityRegeneration;
import me.swirtzly.regeneration.common.capability.IRegeneration;
import me.swirtzly.regeneration.common.types.IRegenType;
import me.swirtzly.regeneration.common.types.TypeHandler;
import me.swirtzly.regeneration.network.MessageUpdateSkin;
import me.swirtzly.regeneration.network.NetworkHandler;
import me.swirtzly.regeneration.util.ClientUtil;
import me.swirtzly.regeneration.util.FileUtil;
import me.swirtzly.regeneration.util.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.SkinManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import sun.misc.BASE64Decoder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@SideOnly(Side.CLIENT)
public class SkinChangingHandler {

    public static final File SKIN_DIRECTORY = new File(RegenConfig.skins.skinDir + "/Regeneration/skins/");
    public static final File SKIN_DIRECTORY_STEVE = new File(SKIN_DIRECTORY, "/steve");
    public static final File SKIN_DIRECTORY_ALEX = new File(SKIN_DIRECTORY, "/alex");
    public static final Logger SKIN_LOG = LogManager.getLogger("Regeneration Skin Handler");
    public static final Map<UUID, SkinInfo> PLAYER_SKINS = new HashMap<>();
    private static final Random RAND = new Random();


    /**
     * Encode image to string
     *
     * @param imageFile The image to encode
     * @return encoded string
     */
    public static String imageToPixelData(File imageFile) throws IOException {
        byte[] imageBytes = IOUtils.toByteArray(new FileInputStream(imageFile));
        return Base64.getEncoder().encodeToString(imageBytes);
    }


    /**
     * Decode string to image
     *
     * @param imageString The string to decode
     * @return decoded image
     */
    public static BufferedImage toImage(String imageString) throws IOException {
        BufferedImage image = null;
        byte[] imageByte;
        BASE64Decoder decoder = new BASE64Decoder();
        imageByte = decoder.decodeBuffer(imageString);
        ByteArrayInputStream bis = new ByteArrayInputStream(imageByte);
        image = ImageIO.read(bis);
        bis.close();

        if (image == null) {
            throw new IllegalStateException("The image data was " + imageString + " but the image became null...");
        }

        return image;
    }

    /**
     * Chooses a random png file from Steve/Alex Directory (This really depends on the Clients preference)
     * It also checks image size of the select file, if it's too large, we'll just reset the player back to their Mojang skin,
     * else they will be kicked from their server. If the player has disabled skin changing on the client, it will just send a reset packet
     *
     * @param random - This kinda explains itself, doesn't it?
     * @param player - Player instance, used to check UUID to ensure it is the client player being involved in the scenario
     * @throws IOException
     */
    public static void sendSkinUpdate(Random random, EntityPlayer player) {
        if (Minecraft.getMinecraft().player.getUniqueID() != player.getUniqueID())
            return;
        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);

        if (RegenConfig.skins.changeMySkin) {

            String pixelData = "NONE";
            File skin = null;

            if (cap.getNextSkin().equals("NONE")) {
                boolean isAlex = cap.getPreferredModel().isAlex();
                skin = SkinChangingHandler.getRandomSkin(random, isAlex);
                RegenerationMod.LOG.info(skin + " was choosen");

                try {
                    pixelData = SkinChangingHandler.imageToPixelData(skin);
                    cap.setEncodedSkin(pixelData);
                    NetworkHandler.INSTANCE.sendToServer(new MessageUpdateSkin(pixelData, isAlex));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                pixelData = cap.getNextSkin();
                cap.setEncodedSkin(pixelData);
                NetworkHandler.INSTANCE.sendToServer(new MessageUpdateSkin(pixelData, cap.getNextSkinType().getMojangType().equals("slim")));
            }

        } else {
            ClientUtil.sendSkinResetPacket();
        }

    }

    private static File getRandomSkin(Random rand, boolean isAlex) {
        File skins = isAlex ? SKIN_DIRECTORY_ALEX : SKIN_DIRECTORY_STEVE;
        Collection<File> folderFiles = FileUtils.listFiles(skins, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        if (folderFiles.isEmpty()) {
            SKIN_LOG.info("The Skin folder was empty....Downloading some skins...");
            FileUtil.doSetupOnThread();
            folderFiles = FileUtils.listFiles(skins, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
        }
        SKIN_LOG.info("There were " + folderFiles.size() + " to chose from");
        return (File) folderFiles.toArray()[rand.nextInt(folderFiles.size())];
    }

    /**
     * Creates a SkinInfo object for later use
     *
     * @param player - Player instance involved
     * @param data   - The players regeneration capability instance
     * @return SkinInfo - A class that contains the SkinType and the resource location to use as a skin
     * @throws IOException
     */
    private static SkinInfo getSkinInfo(AbstractClientPlayer player, IRegeneration data) throws IOException {

        if (data == null || player.getName() == null || player.getUniqueID() == null) {
            return new SkinInfo(null, getSkinType(player));
        }

        ResourceLocation resourceLocation;
        SkinInfo.SkinType skinType = null;

        if (data.getEncodedSkin().toLowerCase().equals("none") || data.getEncodedSkin().equals(" ") || data.getEncodedSkin().equals("")) {
            resourceLocation = getMojangSkin(player);
            skinType = getSkinType(player);
        } else {
            BufferedImage bufferedImage = toImage(data.getEncodedSkin());
            bufferedImage = ClientUtil.ImageFixer.convertSkinTo64x64(bufferedImage);
            if (bufferedImage == null) {
                resourceLocation = DefaultPlayerSkin.getDefaultSkin(player.getUniqueID());
            } else {
                DynamicTexture tex = new DynamicTexture(bufferedImage);
                resourceLocation = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(player.getName().toLowerCase() + "_skin_" + System.currentTimeMillis(), tex);
                skinType = data.getSkinType();
            }
        }
        return new SkinInfo(resourceLocation, skinType);
    }

    public static ResourceLocation createGuiTexture(File file) {
        BufferedImage bufferedImage = null;
        try {
            bufferedImage = ImageIO.read(file);
            return Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("gui_skin_" + System.currentTimeMillis(), new DynamicTexture(bufferedImage));
        } catch (IOException e) {
            e.printStackTrace();
            return DefaultPlayerSkin.getDefaultSkinLegacy();
        }
    }


    /**
     * This is used when the clients skin is reset
     *
     * @param player - Player to get the skin of themselves
     * @return ResourceLocation from Mojang
     * @throws IOException
     */
    private static ResourceLocation getMojangSkin(AbstractClientPlayer player) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = Minecraft.getMinecraft().getSkinManager().loadSkinFromCache(player.getGameProfile());
        if (map.isEmpty()) {
            map = Minecraft.getMinecraft().getSessionService().getTextures(Minecraft.getMinecraft().getSessionService().fillProfileProperties(player.getGameProfile(), false), false);
        }
        if (map.containsKey(MinecraftProfileTexture.Type.SKIN)) {
            MinecraftProfileTexture profile = map.get(MinecraftProfileTexture.Type.SKIN);
            File dir = new File((File) ObfuscationReflectionHelper.getPrivateValue(SkinManager.class, Minecraft.getMinecraft().getSkinManager(), 2), profile.getHash().substring(0, 2));
            File file = new File(dir, profile.getHash());
            if (file.exists())
                file.delete();
            ResourceLocation location = new ResourceLocation("skins/" + profile.getHash());
            loadTexture(file, location, DefaultPlayerSkin.getDefaultSkinLegacy(), profile.getUrl(), player);
            setPlayerSkin(player, location);
            return player.getLocationSkin();
        }
        return DefaultPlayerSkin.getDefaultSkinLegacy();
    }

    private static ITextureObject loadTexture(File file, ResourceLocation resource, ResourceLocation def, String par1Str, AbstractClientPlayer player) {
        TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject object = texturemanager.getTexture(resource);
        if (object == null) {
            object = new ImageDownloadAlt(file, par1Str, def, new ImageBufferDownloadAlt(true));
            texturemanager.loadTexture(resource, object);
        }
        return object;
    }

    /**
     * Changes the ResourceLocation of a Players skin
     *
     * @param player  - Player instance involved
     * @param texture - ResourceLocation of intended texture
     */
    public static void setPlayerSkin(AbstractClientPlayer player, ResourceLocation texture) {
        if (player.getLocationSkin() == texture) {
            return;
        }
        NetworkPlayerInfo playerInfo = player.playerInfo;
        if (playerInfo == null)
            return;
        Map<MinecraftProfileTexture.Type, ResourceLocation> playerTextures = playerInfo.playerTextures;
        playerTextures.put(MinecraftProfileTexture.Type.SKIN, texture);
        if (texture == null)
            playerInfo.playerTexturesLoaded = false;
    }

    public static void setSkinType(AbstractClientPlayer player, SkinInfo.SkinType skinType) {
        if (skinType.getMojangType().equals(player.getSkinType())) return;
        NetworkPlayerInfo playerInfo = player.playerInfo;
        playerInfo.skinType = skinType.getMojangType();
    }

    public static SkinInfo.SkinType getSkinType(AbstractClientPlayer player) {
        Map<MinecraftProfileTexture.Type, MinecraftProfileTexture> map = Minecraft.getMinecraft().getSkinManager().loadSkinFromCache(player.getGameProfile());
        if (map.isEmpty()) {
            map = Minecraft.getMinecraft().getSessionService().getTextures(Minecraft.getMinecraft().getSessionService().fillProfileProperties(player.getGameProfile(), false), false);
        }
        MinecraftProfileTexture profile = map.get(MinecraftProfileTexture.Type.SKIN);
        String modelType = profile.getMetadata("model");

        if (modelType == null) {
            return SkinInfo.SkinType.STEVE;
        }

        return SkinInfo.SkinType.ALEX;
    }

    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Post e) {
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntityPlayer();
        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);
        IRegenType type = TypeHandler.getTypeInstance(cap.getType());

        if (cap.getState() == PlayerUtil.RegenState.REGENERATING) {
            type.getRenderer().onRenderRegeneratingPlayerPost(type, e, cap);
        }
    }


    @SubscribeEvent
    public void onRelog(EntityJoinWorldEvent e) {
        if (e.getEntity() instanceof AbstractClientPlayer) {
            AbstractClientPlayer clientPlayer = (AbstractClientPlayer) e.getEntity();
            PLAYER_SKINS.remove(clientPlayer.getUniqueID());
        }
    }

    /**
     * Subscription to RenderPlayerEvent.Pre to set players model and texture from hashmap
     *
     * @param e - RenderPlayer Pre Event
     */
    @SubscribeEvent
    public void onRenderPlayer(RenderPlayerEvent.Pre e) {
        if (MinecraftForgeClient.getRenderPass() == -1) return;
        AbstractClientPlayer player = (AbstractClientPlayer) e.getEntityPlayer();
        IRegeneration cap = CapabilityRegeneration.getForPlayer(player);
        IRegenType type = TypeHandler.getTypeInstance(cap.getType());

        if (player.ticksExisted == 20) {
            PLAYER_SKINS.remove(player.getUniqueID());
        }

        if (cap.getState() == PlayerUtil.RegenState.REGENERATING) {
            if (type.getAnimationProgress(cap) > 0.7) {
                setSkinFromData(player, cap);
            }
            type.getRenderer().onRenderRegeneratingPlayerPre(type, e, cap);
        } else if (!PLAYER_SKINS.containsKey(player.getUniqueID())) {
            setSkinFromData(player, cap);
        } else {
            SkinInfo skin = PLAYER_SKINS.get(player.getUniqueID());
            if (skin != null) {
                if (skin.getSkinTextureLocation() == null) {
                    setPlayerSkin(player, skin.getSkinTextureLocation());
                }
                if (skin.getSkintype() != null) {
                    setSkinType(player, skin.getSkintype());
                }
            }
        }
    }

    /**
     * Called by onRenderPlayer, sets model, sets texture, adds player and SkinInfo to map
     *
     * @param player - Player instance
     * @param cap    - Players Regen capability instance
     */
    private void setSkinFromData(AbstractClientPlayer player, IRegeneration cap) {
        SkinInfo skinInfo = null;
        try {
            skinInfo = SkinChangingHandler.getSkinInfo(player, cap);
        } catch (IOException e1) {
            SKIN_LOG.error("Error creating skin for: " + player.getName() + " " + e1.getMessage());
        }
        if (skinInfo != null) {
            SkinChangingHandler.setPlayerSkin(player, skinInfo.getSkinTextureLocation());
            SkinChangingHandler.setSkinType(player, skinInfo.getSkintype());
        }
        PLAYER_SKINS.put(player.getGameProfile().getId(), skinInfo);

    }


    public enum EnumChoices implements FileUtil.IEnum {
        ALEX(true), STEVE(false), EITHER(true);

        private boolean isAlex;

        EnumChoices(boolean b) {
            this.isAlex = b;
        }

        public boolean isAlex() {
            if (this == EITHER) {
                return RAND.nextBoolean();
            }
            return isAlex;
        }
    }

}

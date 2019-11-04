package me.rigamortis.seppuku.impl.module.render;

import com.mojang.realmsclient.gui.ChatFormatting;
import me.rigamortis.seppuku.Seppuku;
import me.rigamortis.seppuku.api.event.EventStageable;
import me.rigamortis.seppuku.api.event.network.EventReceivePacket;
import me.rigamortis.seppuku.api.event.render.EventRender2D;
import me.rigamortis.seppuku.api.event.render.EventRender3D;
import me.rigamortis.seppuku.api.event.world.EventChunk;
import me.rigamortis.seppuku.api.module.Module;
import me.rigamortis.seppuku.api.util.GLUProjection;
import me.rigamortis.seppuku.api.util.RenderUtil;
import me.rigamortis.seppuku.api.value.BooleanValue;
import me.rigamortis.seppuku.api.value.NumberValue;
import me.rigamortis.seppuku.api.value.OptionalValue;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import team.stiff.pomelo.impl.annotated.handler.annotation.Listener;

import java.util.ArrayList;
import java.util.List;

/**
 * created by noil on 11/3/2019 at 1:55 PM
 */
public final class PortalFinderModule extends Module {

    public final OptionalValue mode = new OptionalValue("Mode", new String[]{"Mode"}, 0, new String[]{"2D", "3D"});
    public final BooleanValue chat = new BooleanValue("Chat", new String[]{"C", "ChatMessages", "ChatNotifications"}, true);
    public final BooleanValue remove = new BooleanValue("Remove", new String[]{"R", "ChatMessages", "ChatNotifications"}, true);
    public final NumberValue<Integer> range = new NumberValue<Integer>("Range", new String[]{"R", "Distance"}, 200, Integer.class, 1, 2000, 1);
    public final NumberValue<Float> width = new NumberValue<Float>("Width", new String[]{"W", "Width"}, 0.5f, Float.class, 0.0f, 5.0f, 0.1f);

    private final List<Vec3d> portals = new ArrayList<>(512);

    private static final int COLOR = 0xFFFFFFFF;

    public PortalFinderModule() {
        super("PortalFinder", new String[]{"PortalFinder", "PFinder"}, "Highlights nearby portals.", "NONE", -1, Module.ModuleType.RENDER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.portals.clear();
    }

    @Listener
    public void render2D(EventRender2D event) {
        if (this.mode.getInt() == 0) {
            final Minecraft mc = Minecraft.getMinecraft();

            for (Vec3d portal : this.portals) {
                if (mc.player.getDistance(portal.x, portal.y, portal.z) < range.getInt()) {
                    final GLUProjection.Projection projection = GLUProjection.getInstance().project(portal.x - mc.getRenderManager().viewerPosX, portal.y - mc.getRenderManager().viewerPosY, portal.z - mc.getRenderManager().viewerPosZ, GLUProjection.ClampMode.NONE, true);
                    if (projection != null) {
                        RenderUtil.drawLine((float) projection.getX(), (float) projection.getY(), event.getScaledResolution().getScaledWidth() / 2, event.getScaledResolution().getScaledHeight() / 2, this.width.getFloat(), COLOR);
                    }
                } else if (this.remove.getBoolean()) {
                    this.portals.remove(portal);
                }
            }
        }
    }

    @Listener
    public void render3D(EventRender3D event) {
        if (this.mode.getInt() == 1) {
            final Minecraft mc = Minecraft.getMinecraft();

            for (Vec3d portal : this.portals) {
                if (mc.player.getDistance(portal.x, portal.y, portal.z) < range.getInt()) {
                    final boolean bobbing = mc.gameSettings.viewBobbing;
                    mc.gameSettings.viewBobbing = false;
                    mc.entityRenderer.setupCameraTransform(event.getPartialTicks(), 0);
                    final Vec3d forward = new Vec3d(0, 0, 1).rotatePitch(-(float) Math.toRadians(Minecraft.getMinecraft().player.rotationPitch)).rotateYaw(-(float) Math.toRadians(Minecraft.getMinecraft().player.rotationYaw));
                    RenderUtil.drawLine3D((float) forward.x, (float) forward.y + mc.player.getEyeHeight(), (float) forward.z, (float) (portal.x - mc.getRenderManager().renderPosX), (float) (portal.y - mc.getRenderManager().renderPosY), (float) (portal.z - mc.getRenderManager().renderPosZ), this.width.getFloat(), COLOR);
                    mc.gameSettings.viewBobbing = bobbing;
                    mc.entityRenderer.setupCameraTransform(event.getPartialTicks(), 0);
                } else if (this.remove.getBoolean()) {
                    this.portals.remove(portal);
                }
            }
        }
    }

    @Listener
    public void onReceivePacket(EventReceivePacket event) {
        if (event.getStage() == EventStageable.EventStage.POST) {
            if (event.getPacket() instanceof SPacketJoinGame) {
                this.portals.clear();
            }
        }
    }

    @Listener
    public void onChunkLoad(EventChunk event) {
        switch (event.getType()) {
            case LOAD:
                final Chunk chunk = event.getChunk();
                final ExtendedBlockStorage[] blockStoragesLoad = chunk.getBlockStorageArray();
                for (int i = 0; i < blockStoragesLoad.length; i++) {
                    final ExtendedBlockStorage extendedBlockStorage = blockStoragesLoad[i];
                    if (extendedBlockStorage == null) {
                        continue;
                    }

                    for (int x = 0; x < 16; ++x) {
                        for (int y = 0; y < 16; ++y) {
                            for (int z = 0; z < 16; ++z) {
                                final IBlockState blockState = extendedBlockStorage.get(x, y, z);
                                final int worldY = y + extendedBlockStorage.getYLocation();
                                if (blockState.getBlock().equals(Blocks.PORTAL)) {
                                    BlockPos position = new BlockPos(event.getChunk().getPos().getXStart() + x, worldY, event.getChunk().getPos().getZStart() + z);
                                    if (!isPortalCached(position.getX(), position.getY(), position.getZ())) {
                                        this.portals.add(new Vec3d(position.getX(), position.getY(), position.getZ()));
                                        if (this.chat.getBoolean()) {
                                            String portalLocation = String.format("Portal found! [X: %s, Y: %s, Z: %s]", position.getX(), position.getY(), position.getZ());
                                            Seppuku.INSTANCE.logChat(ChatFormatting.WHITE + portalLocation);
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case UNLOAD:
                break;
        }
    }

    private boolean isPortalCached(int x, int y, int z) {
        for (int i = this.portals.size() - 1; i >= 0; i--) {
            Vec3d searchPortal = this.portals.get(i);
            if (searchPortal.x == x && searchPortal.y == y && searchPortal.z == z)
                return true;
        }
        return false;
    }

    public List<Vec3d> getPortals() {
        return portals;
    }
}
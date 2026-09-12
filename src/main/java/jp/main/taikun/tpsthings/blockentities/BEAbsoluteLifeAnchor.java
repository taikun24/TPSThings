package jp.main.taikun.tpsthings.blockentities;

import jp.main.taikun.tpsthings.registries.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.UUID;

public class BEAbsoluteLifeAnchor extends BlockEntity {
    public BEAbsoluteLifeAnchor(BlockPos p_155229_, BlockState p_155230_) {
        super(ModBlockEntityTypes.ABSOLUTE_LIFE_ANCHOR.get(), p_155229_, p_155230_);
    }
    ServerPlayer player;
    public void setAsPlayersAnchor(ServerPlayer player){
        player.setRespawnPosition(Objects.requireNonNull(this.getLevel()).dimension(), this.getBlockPos(), 0.0f, true, false);
        this.player = player;
    }
    public void tick(){
        if (player!=null) {
            player.setHealth(player.getMaxHealth());
        }
    }
}

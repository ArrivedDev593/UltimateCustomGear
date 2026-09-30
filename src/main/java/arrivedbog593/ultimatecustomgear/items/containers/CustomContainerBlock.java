package arrivedbog593.ultimatecustomgear.items.containers;

import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.ContainerData;
import arrivedbog593.ultimatecustomgear.data.components.ContainerContents;
import arrivedbog593.ultimatecustomgear.items.blocks.CustomBlock;
import arrivedbog593.ultimatecustomgear.registry.ComponentRegistry;
import arrivedbog593.ultimatecustomgear.registry.ContainerRegistry;
import arrivedbog593.ultimatecustomgear.network.ContainerOpenData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the four container subtypes share: the inventory, the drop
 * behaviour, the comparator output, opening the menu and the scheduled tick.
 * <p>
 * WHAT IS **NOT** HERE, and why: blockstate properties. Each subtype declares
 * its own — a barrel points at six directions and carries an {@code open}
 * state, a chest is horizontal and animates its lid in a renderer instead. A
 * property declared here would be forced on subtypes that cannot use it, and
 * createBlockStateDefinition runs in the constructor, so it cannot be decided
 * per definition either.
 * <p>
 * Abstract on purpose: there is no such thing as a container without a subtype.
 */
public abstract class CustomContainerBlock extends CustomBlock implements EntityBlock {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    protected final ContainerContentData data;
    protected final ContainerData container;

    /**
     * Takes FINISHED properties, not the raw ones the registry hands out. The
     * shulker has to adjust them before construction — BlockStateBase freezes
     * them the moment the Block is built — so every subtype runs
     * CustomBlock.buildProperties itself and passes the result in.
     */
    protected CustomContainerBlock(ContainerContentData data, Properties properties) {
        super(properties);
        this.data = data;
        this.container = data.container != null ? data.container : new ContainerData();
    }

    public ContainerContentData getData() { return data; }

    /** Played when the first player opens this container. */
    public abstract SoundEvent openSound();

    /** Played when the last player closes it. */
    public abstract SoundEvent closeSound();

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return ContainerRegistry.CONTAINER_BE.get().create(pos, state);
    }


    /**
     * Where this block's inventory actually lives. A single container answers
     * with its own position; half of a double chest answers with its partner's,
     * because a double keeps ONE inventory and one of the halves is a shell.
     * <p>
     * Used by the menu, the comparator and the item-handler capability — every
     * path that has to reach the real contents rather than the block clicked.
     */
    public BlockPos inventoryPos(BlockState state, BlockPos pos) {
        return pos;
    }

    @Override
    protected @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level,
                                                        @NotNull BlockPos pos, @NotNull Player player,
                                                        @NotNull BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        // Two different obstruction rules behind one flag. A chest refuses when
        // a solid block sits above it; a shulker refuses when the volume its lid
        // would sweep is occupied — see CustomShulkerBlock.canOpen.
        if (!container.openableWhenObstructed() && !canOpen(state, level, pos)) {
            return InteractionResult.CONSUME;
        }

        // Resolved BEFORE the block entity is fetched: clicking the shell half
        // of a double chest has to open the partner's menu, not its own empty
        // one. Everything downstream — search, sort, transfer — then sees a
        // single Container and needs no idea that halves exist.
        BlockPos target = inventoryPos(state, pos);
        if (level.getBlockEntity(target) instanceof CustomContainerBlockEntity be) {
            // Size and columns must reach the client BEFORE the menu is built:
            // it has no block entity to read them from at that point.
            player.openMenu(be, buf -> new ContainerOpenData(
                    be.getContainerSize(),
                    // From the REAL size, not the declared one: a double chest is
                    // twice as big and its window widens to match.
                    container.columnsFor(be.getContainerSize()),
                    be.getSortCriterion(),
                    be.isSortDescending(),
                    // So the client can refuse a nested container LOCALLY instead
                    // of predicting the move and being corrected a tick later.
                    container.keepsContents(),
                    // No locked slot: a placed container does not live in the
                    // player's inventory, so there is nothing to freeze.
                    -1
            ).write(buf));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    /**
     * The block entity is ALREADY GONE by the time this runs — getBlockEntity
     * returns null here, confirmed in the log. Everything that needs the
     * inventory lives in onBlockEntityRemoved; only the comparator update, which
     * needs nothing, is left.
     */
    @Override
    protected void affectNeighborsAfterRemoval(@NotNull BlockState state,
                                               @NotNull ServerLevel level,
                                               @NotNull BlockPos pos, boolean movedByPiston) {
        level.updateNeighbourForOutputSignal(pos, this);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /**
     * What 1.21.1 did in onRemove: spill the contents, or store them in the
     * dropped item when the container keeps them.
     * <p>
     * Called from the block entity's preRemoveSideEffects, the only hook left
     * that runs for EVERY removal — player, explosion, piston, /setblock — while
     * the block entity still exists. playerWillDestroy only sees players, and
     * affectNeighborsAfterRemoval comes too late.
     * <p>
     * Storing happens HERE and not through a copy_components loot function: that
     * function copies components off the block entity, and this inventory lives
     * in our own component. The loot table for these containers is empty on
     * purpose, so this is the only source of the drop.
     */
    protected void onBlockEntityRemoved(BlockState state, Level level, BlockPos pos,
                                        CustomContainerBlockEntity be) {
        if (container.keepsContents()) {
            // Vanilla drops a shulker broken in creative ONLY when it holds
            // something; an empty one breaks like any other block. Pending
            // overflow is part of the snapshot, so it travels in the item too.
            if (!be.isCreativeBreak() || !be.isEmpty()) {
                ItemStack drop = new ItemStack(this);
                if (!be.isEmpty()) {
                    drop.set(ComponentRegistry.CONTAINER_CONTENTS.get(), be.snapshot());
                }
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), drop);
            }
            be.clearContent();
        } else {
            // Anything still waiting for the scheduled tick has to come out here
            // too: dropContents only walks the container itself.
            be.spillOverflow();
            Containers.dropContents(level, pos, be);
        }
    }

    /**
     * The creative rule lives in Player.destroyBlock, which skips the loot table
     * but not our drop, so the block entity is told who broke it. Stored on the
     * BLOCK ENTITY, not here: a Block is a singleton shared by every container
     * of this type, and a field here would be shared by every player at once.
     */
    @Override
    public @NotNull BlockState playerWillDestroy(@NotNull Level level, @NotNull BlockPos pos,
                                                 @NotNull BlockState state, @NotNull Player player) {
        if (!level.isClientSide()
                && player.getAbilities().instabuild
                && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity be) {
            be.markCreativeBreak();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Comparators read the fill level, like every vanilla container. */
    @Override
    protected boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level,
                                        @NotNull BlockPos pos, net.minecraft.core.@NotNull Direction direction) {
        // Same redirection the menu uses: the shell half of a double chest holds
        // nothing, so a comparator against it would read zero forever.
        if (level.getBlockEntity(inventoryPos(state, pos)) instanceof CustomContainerBlockEntity be) {
            return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromContainer(be);
        }
        return 0;
    }

    /**
     * Restores a stored inventory when the block is placed again. A saved
     * inventory can be LARGER than the block's current size if its JSON changed
     * 'slots' in between; the excess is dropped at the player's feet rather than
     * silently discarded.
     */
    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;

        ContainerContents stored = stack.get(ComponentRegistry.CONTAINER_CONTENTS.get());
        if (stored == null) return;

        if (level.getBlockEntity(pos) instanceof CustomContainerBlockEntity be) {
            stored.copyInto(be);
            for (ItemStack extra : stored.overflow(be.getContainerSize())) {
                LOGGER.warn("[CustomGear] Container '{}' was saved with more slots than it has "
                        + "now — dropping {} that no longer fits.", state.getBlock(), extra);
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), extra);
            }
            be.setChanged();
        }
    }

    /**
     * Two unrelated things schedule this tick: the overflow spill (1 tick, from
     * onLoad) and the opener recheck (5 ticks, from the openers counter). Both
     * are idempotent, so running them together costs nothing and saves a second
     * scheduling channel.
     */
    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level,
                        @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (level.getBlockEntity(pos) instanceof CustomContainerBlockEntity be) {
            be.spillOverflow();
            be.recheckOpen();
        }
    }

    /**
     * Block events are delivered to the BLOCK, not to the block entity, and the
     * default implementation drops them. Every container that animates learns
     * about openings through this channel, so it belongs here rather than in
     * one subtype.
     */
    @Override
    protected boolean triggerEvent(@NotNull BlockState state, @NotNull Level level,
                                   @NotNull BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        // Only orphan rescue needs ticking, and only where a definition is
        // missing. Returning a ticker for every container would run this check
        // 20 times a second on every loaded storage block, forever.
        if (level.isClientSide() || data.container != null) return null;
        return (lvl, pos, _, be) -> {
            if (be instanceof CustomContainerBlockEntity c && c.isOrphaned()) {
                LOGGER.warn("[CustomGear] Container at {} has no definition any more — "
                        + "dropping its contents and removing the block.", pos);
                Containers.dropContents(lvl, pos, c);
                lvl.removeBlock(pos, false);
            }
        };
    }

    /**
     * Whether this container may open where it stands. The chest rule — nothing
     * solid directly above — is the default; the shulker overrides it.
     */
    protected boolean canOpen(BlockState state, Level level, BlockPos pos) {
        return !level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above());
    }
}
package arrivedbog593.ultimatecustomgear.items.containers;

import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.items.blocks.CustomBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The vanilla chest shape: smaller than a full block, horizontal, with a lid
 * animated by a block entity renderer.
 * <p>
 * RENDERED BY CODE, NOT BY THE MODEL. Its block model declares a particle
 * texture and no elements, so nothing is baked and nothing is drawn from it —
 * it exists only so the block has something to point at. The geometry lives in
 * ChestRenderer, and the texture is a 64x64 unwrap rather than six faces.
 * <p>
 * TYPE IS ALWAYS DECLARED, even for a chest whose definition forbids doubling.
 * createBlockStateDefinition runs in the constructor, so a conditional property
 * would tie the SHAPE of the block-state to a JSON field — and the day someone
 * flips that field, every chest already placed in the world stops parsing its
 * state. Three entries in a map cost nothing; losing a chest costs a lot.
 */
public class CustomChestBlock extends CustomContainerBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;

    /**
     * 1 px in on all four sides, 14 tall — vanilla's own chest shape.
     * <p>
     * The halves of a double chest are 15 wide, so they meet with no seam, which
     * is why this is picked per type rather than being one constant.
     */
    private static final VoxelShape SHAPE_SINGLE = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    private static final VoxelShape SHAPE_NORTH  = Block.box(1.0, 0.0, 0.0, 15.0, 14.0, 15.0);
    private static final VoxelShape SHAPE_SOUTH  = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 16.0);
    private static final VoxelShape SHAPE_WEST   = Block.box(0.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    private static final VoxelShape SHAPE_EAST   = Block.box(1.0, 0.0, 1.0, 16.0, 14.0, 15.0);

    public CustomChestBlock(ContainerContentData data, Properties props) {
        super(data, CustomBlock.buildProperties(data, props));
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(TYPE, ChestType.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    /** Whether this definition allows two chests to merge. See ContainerData. */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canDouble() {
        return container.canDouble();
    }

    // ── Placement and joining ────────────────────────────────────────────────

    /**
     * Defers the merge by one tick.
     * <p>
     * onPlace runs from LevelChunk.setBlockState, BEFORE Level.setBlock
     * propagates updateShape to the neighbors — so at this point the OTHER half
     * is still SINGLE, and any size computed from the block-states is wrong. One
     * scheduled tick later everything has settled.
     */
    @Override
    protected void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos,
                           @NotNull BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide() || !canDouble()) return;
        if (state.getValue(TYPE) == ChestType.SINGLE) return;
        level.scheduleTick(getMainPos(state, pos), this, 1);
    }

    /**
     * Brings the pair's single inventory in line with the block-state and pulls
     * in whatever the shell was holding on its own. Idempotent: run twice, the
     * second pass finds the right size and an empty shell.
     */
    @Override
    protected void tick(@NotNull BlockState state, @NotNull ServerLevel level,
                        @NotNull BlockPos pos, @NotNull RandomSource random) {
        if (canDouble()
                && state.getValue(TYPE) != ChestType.SINGLE
                && isMainChest(state)
                && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity main
                && !main.isJoined()) {

            main.resizeToTarget();
            main.moveContentsToHighHalf();

            BlockPos shellPos = pos.relative(getConnectedDirection(state));
            if (level.getBlockEntity(shellPos) instanceof CustomContainerBlockEntity shell) {
                List<ItemStack> moved = shell.removeRange(0, shell.getContainerSize());
                if (!moved.isEmpty()) main.insertAll(moved);
            }
            main.setJoined(true);
        }
        super.tick(state, level, pos, random);
    }

    /**
     * Splits the inventory before the base class drops anything.
     * <p>
     * The upper half — slot count/2 onwards — belongs to the SHELL, regardless
     * of which side happens to be the main one. Break the shell, and it takes
     * that half with it; break the main, and it pushes that half across first,
     * so the survivor keeps it. Either way the player sees exactly what vanilla
     * does: one half's worth of items on the ground, the other half's still in
     * the chest that remains.
     */
    @Override
    protected void onBlockEntityRemoved(BlockState state, Level level, BlockPos pos,
                                        CustomContainerBlockEntity be) {
        // NOT in affectNeighborsAfterRemoval: both block entities have to still
        // exist for the split, and by then this one is gone — the split silently
        // did nothing, so breaking the main dropped all 2N and breaking the shell
        // dropped nothing.
        if (canDouble() && state.getValue(TYPE) != ChestType.SINGLE) {
            splitInventory(state, level, pos);
        }
        super.onBlockEntityRemoved(state, level, pos, be);
    }

    private void splitInventory(BlockState state, Level level, BlockPos pos) {
        BlockPos mainPos = getMainPos(state, pos);
        BlockPos otherPos = pos.relative(getConnectedDirection(state));

        // Half comes from the DEFINITION, never from halving the current size.
        // If the pair failed to grow for any reason, halving would cut a single
        // chest's worth of slots in two and destroy the difference.
        //
        // The SHELL owns the low indices — the rows drawn at the top — and the
        // main owns the high ones. The same split the pick-block copy uses, and it
        // has to stay the same, or breaking a chest would hand back a different
        // half than copying it did.
        if (isMainChest(state)) {
            // The main half is going away: hand the shell's own low half over,
            // keep the high one here for the base class to drop.
            if (level.getBlockEntity(pos) instanceof CustomContainerBlockEntity main
                    && level.getBlockEntity(otherPos) instanceof CustomContainerBlockEntity shell) {
                int half = main.declaredSize();
                List<ItemStack> low = main.removeRange(0, half);
                main.compactAndShrinkTo(half);
                shell.insertAll(low);
                shell.setSortMode(main.getSortCriterion(), main.isSortDescending());
                // The survivor is single now; a later neighbor must merge again.
                shell.setJoined(false);
            }
            return;
        }

        // The shell is going away: pull ITS half — the low indices — out of the
        // main and into this block entity, so the base class drops it from here.
        if (level.getBlockEntity(mainPos) instanceof CustomContainerBlockEntity main
                && level.getBlockEntity(pos) instanceof CustomContainerBlockEntity shell) {
            int half = main.declaredSize();
            List<ItemStack> low = main.removeRange(0, half);
            main.compactAndShrinkTo(half);
            shell.insertAll(low);
            // Nothing ever cleared this before: a main left single kept
            // Joined=true, and the next chest placed beside it never merged.
            main.setJoined(false);
        }
    }

    /**
     * Vanilla's rule, kept exactly: a chest joins the neighbor it was clicked
     * against, or failing that a neighbor to its side, and only when that
     * neighbor is the SAME block, faces the SAME way and is still SINGLE.
     * Crouching refuses to join, which is the only way to place two separate
     * chests side by side.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState()
                .setValue(FACING, facing)
                .setValue(TYPE, ChestType.SINGLE);

        if (!canDouble() || context.isSecondaryUseActive()) return state;

        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            ChestType type = candidateType(context, facing, clicked);
            if (type != ChestType.SINGLE) {
                return state.setValue(TYPE, type)
                        .setValue(FACING, facing);
            }
        }
        for (Direction side : new Direction[]{facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockState neighbour = context.getLevel().getBlockState(context.getClickedPos().relative(side));
            if (joinable(neighbour, facing)) {
                return state.setValue(TYPE,
                        side == facing.getCounterClockWise() ? ChestType.RIGHT : ChestType.LEFT);
            }
        }
        return state;
    }

    private ChestType candidateType(BlockPlaceContext context, Direction facing, Direction clicked) {
        BlockState neighbour = context.getLevel().getBlockState(
                context.getClickedPos().relative(clicked.getOpposite()));
        if (!joinable(neighbour, facing)) return ChestType.SINGLE;
        return clicked.getOpposite() == facing.getCounterClockWise()
                ? ChestType.RIGHT : ChestType.LEFT;
    }

    /** Same block, same facing, still single, and allowed to double. */
    private boolean joinable(BlockState neighbour, Direction facing) {
        return neighbour.is(this)
                && neighbour.getValue(TYPE) == ChestType.SINGLE
                && neighbour.getValue(FACING) == facing;
    }

    /**
     * Splits the pair when the other half goes away and joins when a compatible
     * neighbor appears. This is what makes breaking one half leave a working
     * single chest instead of a state that claims to have a partner.
     */
    @Override
    protected @NotNull BlockState updateShape(@NotNull BlockState state,
                                              net.minecraft.world.level.@NotNull LevelReader level,
                                              net.minecraft.world.level.@NotNull ScheduledTickAccess ticks,
                                              @NotNull BlockPos pos, @NotNull Direction direction,
                                              @NotNull BlockPos neighbourPos,
                                              @NotNull BlockState neighbourState,
                                              net.minecraft.util.@NotNull RandomSource random) {
        if (!canDouble()) return state;

        if (neighbourState.is(this) && direction.getAxis().isHorizontal()) {
            ChestType neighbourType = neighbourState.getValue(TYPE);
            if (state.getValue(TYPE) == ChestType.SINGLE
                    && neighbourType != ChestType.SINGLE
                    && state.getValue(FACING) == neighbourState.getValue(FACING)
                    && getConnectedDirection(neighbourState) == direction.getOpposite()) {
                return state.setValue(TYPE, neighbourType.getOpposite());
            }
        } else if (getConnectedDirection(state) == direction) {
            return state.setValue(TYPE, ChestType.SINGLE);
        }
        return state;
    }

    /** Which way the other half lies. LEFT looks clockwise, RIGHT is the other way. */
    public static Direction getConnectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT
                ? facing.getClockWise()
                : facing.getCounterClockWise();
    }

    /**
     * The half that owns the inventory. A double chest keeps everything in ONE
     * block entity and leaves the other as a shell — see CustomContainerBlockEntity.
     * LEFT is the owner, arbitrarily but consistently.
     */
    public static BlockPos getMainPos(BlockState state, BlockPos pos) {
        if (state.getValue(TYPE) == ChestType.RIGHT) {
            return pos.relative(getConnectedDirection(state));
        }
        return pos;
    }

    public static boolean isMainChest(BlockState state) {
        return state.getValue(TYPE) != ChestType.RIGHT;
    }

    /** Half of a double chest reaches its partner's inventory — see getMainPos. */
    @Override
    public BlockPos inventoryPos(BlockState state, BlockPos pos) {
        return getMainPos(state, pos);
    }

    // ── Shape and rendering ──────────────────────────────────────────────────

    @Override
    protected @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                           @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (state.getValue(TYPE) == ChestType.SINGLE) return SHAPE_SINGLE;
        return switch (getConnectedDirection(state)) {
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST  -> SHAPE_WEST;
            default    -> SHAPE_EAST;
        };
    }

    // NO getRenderShape OVERRIDE. Up to 1.21.1 a block drawn by its block entity
    // had to report ENTITYBLOCK_ANIMATED so the baked model was skipped. That
    // constant is gone — RenderShape is now only INVISIBLE or MODEL — and the
    // job moved to the model itself: the generated block model declares a
    // particle texture and NO elements, so there is nothing to draw and the
    // renderer is left to it. See BlockModelGenerator.

    /**
     * The lid animates on the CLIENT, so this ticker is the mirror image of the
     * one in the base class: that one runs server side to rescue orphans, this
     * one runs client side to advance the animation.
     */
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (!level.isClientSide()) return super.getTicker(level, state, type);
        return (lvl, pos, st, be) -> {
            if (be instanceof CustomChestBlockEntity c) {
                CustomChestBlockEntity.lidAnimateTick(c);
            }
        };
    }

    @Override
    public SoundEvent openSound()  { return SoundEvents.CHEST_OPEN; }

    @Override
    public SoundEvent closeSound() { return SoundEvents.CHEST_CLOSE; }
}
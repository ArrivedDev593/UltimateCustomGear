package arrivedbog593.ultimatecustomgear.items.containers;

import arrivedbog593.ultimatecustomgear.data.ContainerContentData;
import arrivedbog593.ultimatecustomgear.data.components.ContainerContents;
import arrivedbog593.ultimatecustomgear.registry.ComponentRegistry;
import arrivedbog593.ultimatecustomgear.registry.ContainerRegistry;
import arrivedbog593.ultimatecustomgear.menu.CustomContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueOutput;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Backing inventory for every custom storage block. One block entity type
 * serves all of them; the size comes from the block's own JSON.
 * <p>
 * Slot count is read from BlockRegistry at construction, which means it is
 * fixed for the lifetime of the block entity: changing {@code slots} needs a
 * restart, not a reload. Everything the container does that is NOT sizing —
 * names, drop behaviour, obstruction — reads the live BlockData and does
 * follow /customgear reload.
 */
public abstract class CustomContainerBlockEntity extends BaseContainerBlockEntity {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");
    private final int size;
    private NonNullList<ItemStack> items;
    /** True when the JSON that defined this container no longer exists. */
    private boolean orphaned;
    /** Stacks the configured size no longer fits, waiting to be dropped. */
    private List<ItemStack> pendingSpill = List.of();
    /**
     * Sort mode, remembered per placed chest. NOT carried by the dropped item:
     * breaking and replacing resets it, which is what the storage mods do.
     * <p>
     * The server stores these but never interprets them — sorting happens on the
     * client, which is the only side that knows the player's language. This is
     * just somewhere durable to keep the choice between openings.
     */
    private byte sortCriterion;
    private boolean sortDescending;

    /**
     * Whether the join has already been carried out.
     * <p>
     * The tick that forms a pair fires more than once — the opener recheck and
     * the spill share the same channel — and redoing the merge shuffles the
     * inventory on every pass. Persisted so a chunk reload does not redo it
     * either.
     */
    private boolean joined;

    public boolean isJoined() { return joined; }

    public void setJoined(boolean joined) {
        this.joined = joined;
        setChanged();
    }

    /**
     * Set by playerWillDestroy right before a creative player removes the block,
     * read by preRemoveSideEffects. Never saved: it only has to survive the
     * few calls between the two.
     */
    private boolean creativeBreak;

    public void markCreativeBreak() { this.creativeBreak = true; }

    public boolean isCreativeBreak() { return creativeBreak; }

    /**
     * Runs for every removal while this block entity still exists — the
     * replacement for 1.21.1's Block.onRemove. The default would drop the
     * contents loose, which is wrong for keeps_contents and for half of a pair,
     * so the block decides instead.
     */
    @Override
    public void preRemoveSideEffects(@NotNull BlockPos pos, @NotNull BlockState state) {
        if (level != null && state.getBlock() instanceof CustomContainerBlock block) {
            block.onBlockEntityRemoved(state, level, pos, this);
        } else {
            super.preRemoveSideEffects(pos, state);
        }
    }


    protected CustomContainerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.size = sizeFor(state);
        this.items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    private static int sizeFor(BlockState state) {
        ContainerContentData data = ContainerRegistry.CONTAINER_MAP.get(
                BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        // Absent from the map means the definition changed type or vanished.
        // The map only ever holds containers, so there is nothing else to test.
        if (data == null || data.container == null || data.container.slots <= 0) {
            return 0;
        }
        return data.container.slots;
    }


    protected ContainerContentData data() {
        return ContainerRegistry.CONTAINER_MAP.get(
                BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()));
    }

    /**
     * The real list size, not the configured one. An orphaned container has a
     * configured size of 0 but still holds whatever the NBT carried, and
     * Containers.dropContents iterates this — returning 0 would drop nothing
     * and lose the items the rescue exists to save.
     */
    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> newItems) {
        this.items = newItems;
    }

    @Override
    protected @NotNull Component getDefaultName() {
        // The lang file generated for the block already carries the user's name.
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory) {
        ContainerContentData d = data();
        int columns = (d != null && d.container != null)
                ? d.container.columnsFor(items.size()) : 9;
        return new CustomContainerMenu(containerId, inventory, this, items.size(), columns);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.@NotNull ValueInput input) {
        super.loadAdditional(input);
        this.orphaned = (size == 0);

        // NOT ContainerHelper: it writes the slot index as a BYTE, so anything
        // past slot 255 is lost. That is where a 300-slot container dropped to
        // ~256 slots' worth of items on world save while the ITEM kept all of
        // them — the item uses ContainerContents, the block entity did not.
        ContainerContents stored = input.read("Contents", ContainerContents.CODEC)
                .orElse(ContainerContents.EMPTY);

        if (orphaned) {
            // No configured size to trim against: keep everything so the rescue
            // ticker can drop it.
            this.items = NonNullList.withSize(stored.size(), ItemStack.EMPTY);
            stored.copyInto(this);
        } else {
            // The definition wins, in both directions, and 'the definition' for
            // half of a double chest means twice its declared slots. Growing
            // gives empty slots straight away; shrinking hands the excess to the
            // scheduled tick.
            int target = targetSize();
            this.items = NonNullList.withSize(target, ItemStack.EMPTY);
            stored.copyInto(this);
            this.pendingSpill = stored.overflow(target);
        }

        this.sortCriterion = input.getByteOr("SortCriterion", (byte) 0);
        this.sortDescending = input.getBooleanOr("SortDescending", false);
        this.joined = input.getBooleanOr("Joined", false);
    }

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.@NotNull ValueOutput output) {
        super.saveAdditional(output);
        output.store("Contents", ContainerContents.CODEC, snapshot());

        output.putByte("SortCriterion", sortCriterion);
        output.putBoolean("SortDescending", sortDescending);

        // Without this the merge tick runs again on every world load, because
        // isJoined() comes back false. It then pushes an ALREADY merged
        // inventory into the high half a second time: what does not fit gets
        // re-inserted from slot 0, and the contents end up split in two.
        output.putBoolean("Joined", joined);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // The drop cannot happen in loadAdditional: level is still null there.
        // One scheduled tick is cheaper than a permanent ticker on every chest.
        if (level != null && !level.isClientSide() && !pendingSpill.isEmpty()) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /** Drops what the current size cannot hold. Called from the block's tick. */
    public void spillOverflow() {
        if (level == null || level.isClientSide() || pendingSpill.isEmpty()) return;
        LOGGER.info("[CustomGear] Container at {} no longer fits {} stacks after its slot count "
                + "dropped to {} — dropping them.", worldPosition, pendingSpill.size(), size);
        for (ItemStack stack : pendingSpill) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(),
                    worldPosition.getZ(), stack);
        }
        pendingSpill = List.of();
        setChanged();
    }

    // ── Resizing for double chests ───────────────────────────────────────────

    /**
     * The size this container SHOULD have right now, which is not always the
     * declared one: half of a double chest holds twice its definition.
     * <p>
     * Read from the definition and the blockstate, never from the item list —
     * that is what the old loadAdditional bug did, sizing the block entity from
     * its own NBT so a wrong size fed itself forever.
     */
    private int targetSize() {
        ContainerContentData d = data();
        if (d == null || d.container == null || d.container.slots <= 0) return 0;
        int declared = d.container.slots;
        BlockState state = getBlockState();
        boolean doubled = state.hasProperty(CustomChestBlock.TYPE)
                && state.getValue(CustomChestBlock.TYPE) != ChestType.SINGLE;
        return doubled ? declared * 2 : declared;
    }

    /** The size the definition declares, ignoring whether this is half a pair. */
    public int declaredSize() {
        ContainerContentData d = data();
        return (d == null || d.container == null) ? 0 : Math.max(0, d.container.slots);
    }

    /**
     * Grows to {@code newSize} and pushes the existing contents to the END.
     * <p>
     * The main half owns the HIGH indices, so when a pair forms its own stacks
     * have to move up there. Growing without this leaves them in the low half —
     * which belongs to the shell — and splitting the pair afterwards would hand
     * the main's own items to the neighbour. Joining and splitting have to be
     * reversible.
     */
    public void growToEnd(int newSize) {
        if (newSize <= items.size()) return;
        int shift = newSize - items.size();
        NonNullList<ItemStack> bigger = NonNullList.withSize(newSize, ItemStack.EMPTY);
        for (int i = 0; i < items.size(); i++) bigger.set(i + shift, items.get(i));
        this.items = bigger;
        setChanged();
        closeOpenMenus();
    }

    /**
     * Takes the stacks in [from, to) out of this container and returns them,
     * leaving those slots empty.
     */
    public List<ItemStack> removeRange(int from, int to) {
        List<ItemStack> taken = new java.util.ArrayList<>();
        for (int i = from; i < to && i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                taken.add(stack.copy());
                items.set(i, ItemStack.EMPTY);
            }
        }
        setChanged();
        return taken;
    }

    /**
     * Slides everything down to the front, then shrinks.
     * <p>
     * Needed because the half that STAYS lives in the high indices: shrinking
     * first would hand every one of them to the pending spill and drop the
     * survivor's own contents on the floor.
     */
    public void compactAndShrinkTo(int newSize) {
        List<ItemStack> kept = new java.util.ArrayList<>();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) kept.add(stack.copy());
        }
        Collections.fill(items, ItemStack.EMPTY);
        for (int i = 0; i < kept.size() && i < items.size(); i++) items.set(i, kept.get(i));
        shrinkTo(newSize);
    }


    /** Shrinks to {@code newSize}; anything past it goes to the pending spill. */
    public void shrinkTo(int newSize) {
        if (newSize < 0 || newSize >= items.size()) return;
        List<ItemStack> spilled = new java.util.ArrayList<>(pendingSpill);
        for (int i = newSize; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) spilled.add(items.get(i).copy());
        }
        NonNullList<ItemStack> smaller = NonNullList.withSize(newSize, ItemStack.EMPTY);
        for (int i = 0; i < newSize; i++) smaller.set(i, items.get(i));
        this.items = smaller;
        this.pendingSpill = spilled;
        setChanged();
        closeOpenMenus();
        if (level != null && !level.isClientSide() && !pendingSpill.isEmpty()) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /** Drops a list of stacks into this container, from slot 0 up. */
    public void insertAll(List<ItemStack> stacks) {
        int slot = 0;
        for (ItemStack stack : stacks) {
            while (slot < items.size() && !items.get(slot).isEmpty()) slot++;
            if (slot >= items.size()) {
                // Nothing is lost: what does not fit joins the pending spill and
                // reaches the ground through the same scheduled tick.
                List<ItemStack> spilled = new java.util.ArrayList<>(pendingSpill);
                spilled.add(stack);
                this.pendingSpill = spilled;
                continue;
            }
            items.set(slot, stack);
        }
        setChanged();
        if (level != null && !level.isClientSide() && !pendingSpill.isEmpty()) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /**
     * Brings the inventory in line with the blockstate. Called after a pair
     * forms or splits, on the half that keeps the inventory.
     */
    public void resizeToTarget() {
        int target = targetSize();
        if (target > items.size()) growToEnd(target);
        else if (target < items.size()) shrinkTo(target);
    }

    /**
     * Slides everything into the high half, where the main's own contents belong
     * once a pair has formed.
     * <p>
     * Anything that does not fit goes BACK where it was rather than vanishing —
     * this runs from a tick that can fire several times for the same pair, and
     * on the second pass the low half already holds what the shell handed over.
     */
    public void moveContentsToHighHalf() {
        int half = declaredSize();
        if (half <= 0 || items.size() <= half) return;

        List<ItemStack> low = removeRange(0, half);
        if (low.isEmpty()) return;

        int slot = half;
        int returned = 0;
        for (ItemStack stack : low) {
            while (slot < items.size() && !items.get(slot).isEmpty()) slot++;
            if (slot < items.size()) {
                items.set(slot, stack);
            } else {
                // No room upstairs: put it back in the low half it came from.
                while (returned < half && !items.get(returned).isEmpty()) returned++;
                if (returned < half) items.set(returned, stack);
            }
        }
        setChanged();
    }

    public byte getSortCriterion()     { return sortCriterion; }
    public boolean isSortDescending()  { return sortDescending; }

    public void setSortMode(byte criterion, boolean descending) {
        this.sortCriterion = criterion;
        this.sortDescending = descending;
        setChanged();
    }

    /** Contents including anything still pending, for saving and for the drop. */
    public ContainerContents snapshot() {
        return ContainerContents.from(this, pendingSpill);
    }

    /** Pending stacks count as contents: breaking first must not lose them. */
    @Override
    public boolean isEmpty() {
        return pendingSpill.isEmpty() && super.isEmpty();
    }

    @Override
    public void clearContent() {
        super.clearContent();
        pendingSpill = List.of();
    }

    public boolean isOrphaned() {
        return orphaned;
    }

    // ── Open state ────────────────────────────────────────────────────────────

    /**
     * Drives the {@code open} blockstate property and the open/close sound.
     * <p>
     * Vanilla's own counter, not a hand-rolled one: it already handles the cases
     * that are easy to get wrong — a player logging out with the container open,
     * the chunk unloading, two players sharing one container, and spectators not
     * counting. It reschedules a recheck tick itself, which the block picks up.
     */
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state) {
            if (state.getBlock() instanceof CustomContainerBlock block) {
                playOpenSound(state, block.openSound());
            }
            setOpenProperty(state, true);
        }

        @Override
        protected void onClose(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state) {
            if (state.getBlock() instanceof CustomContainerBlock block) {
                playOpenSound(state, block.closeSound());
            }
            setOpenProperty(state, false);
        }

        @Override
        protected void openerCountChanged(Level level, @NotNull BlockPos pos, BlockState state,
                                          int count, int openCount) {
            // Block event 1 is vanilla's chest channel: the server broadcasts the
            // opener count and each client drives its own lid from it. A barrel
            // ignores this — it has no lid controller in play, and its visible
            // state is the blockstate instead.
            level.blockEvent(pos, state.getBlock(), 1, openCount);

            // The shell half has no opener counter of its own, so nothing would
            // ever tell its lid to move. Sending the same event to its position
            // is what makes both lids of a double chest open together.
            if (state.hasProperty(CustomChestBlock.TYPE)
                    && state.getValue(CustomChestBlock.TYPE) != ChestType.SINGLE) {
                BlockPos other = pos.relative(CustomChestBlock.getConnectedDirection(state));
                BlockState otherState = level.getBlockState(other);
                if (otherState.is(state.getBlock())) {
                    level.blockEvent(other, otherState.getBlock(), 1, openCount);
                }
            }
        }

        @Override
        public boolean isOwnContainer(@NotNull Player player) {
            return player.containerMenu instanceof CustomContainerMenu menu
                    && menu.getContainer() == CustomContainerBlockEntity.this;
        }
    };

    @Override
    public void startOpen(@NotNull ContainerUser user) {
        if (!isRemoved() && !user.getLivingEntity().isSpectator() && level != null) {
            openersCounter.incrementOpeners(user.getLivingEntity(), level, worldPosition,
                    getBlockState(), user.getContainerInteractionRange());
        }
    }

    @Override
    public void stopOpen(@NotNull ContainerUser user) {
        if (!isRemoved() && !user.getLivingEntity().isSpectator() && level != null) {
            openersCounter.decrementOpeners(user.getLivingEntity(), level, worldPosition, getBlockState());
        }
    }

    /** Called from the block's scheduled tick — see CustomContainerBlock.tick. */
    public void recheckOpen() {
        if (level == null || level.isClientSide()) return;
        openersCounter.recheckOpeners(level, worldPosition, getBlockState());
    }

    /**
     * Guarded by hasProperty: only the barrel-shaped container carries an open
     * state. A chest animates its lid in a renderer instead, and setValue on a
     * property the block never declared throws.
     */
    private void setOpenProperty(BlockState state, boolean open) {
        if (level == null || !state.hasProperty(BlockStateProperties.OPEN)) return;
        level.setBlock(worldPosition, state.setValue(BlockStateProperties.OPEN, open), 3);
    }

    /**
     * Offset half a block toward the face that opens, as vanilla's barrel does.
     * A chest has no six-way FACING, so it falls back to the block's centre.
     */
    private void playOpenSound(BlockState state, SoundEvent sound) {
        if (level == null) return;
        Vec3i normal = state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING).getUnitVec3i()
                : Vec3i.ZERO;
        level.playSound(null,
                worldPosition.getX() + 0.5 + normal.getX() / 2.0,
                worldPosition.getY() + 0.5 + normal.getY() / 2.0,
                worldPosition.getZ() + 0.5 + normal.getZ() / 2.0,
                sound, SoundSource.BLOCKS,
                0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    /**
     * Kicks anyone out of this container's screen.
     * <p>
     * Size and column count travel in the OPENING packet, so a player already
     * looking at the screen when the pair forms or splits keeps a layout that no
     * longer matches the inventory behind it. Reopening is one click; a window
     * whose slots do not line up is a bug report.
     */
    private void closeOpenMenus() {
        if (level == null || level.isClientSide()) return;
        for (Player player : List.copyOf(level.players())) {
            if (player.containerMenu instanceof CustomContainerMenu menu
                    && menu.getContainer() == this) {
                player.closeContainer();
            }
        }
    }

    /**
     * Refuses anything carrying an inventory of its own, but only in containers
     * that carry theirs in the dropped item.
     * <p>
     * A chest keeps its contents in the block entity, so a shulker inside it
     * nests nothing — that is ordinary storage and vanilla allows it. The rule
     * only matters where nesting would nest NBT: each level multiplies the size
     * of the outer stack, which travels the network every time it moves in an
     * inventory, and at a few hundred slots that reaches the packet limit and
     * disconnects the player.
     * <p>
     * An EMPTY container is refused too. Allowing it and refusing the full one
     * is the kind of rule people find by accident and report as a bug.
     */
    @Override
    public boolean canPlaceItem(int slot, @NotNull ItemStack stack) {
        ContainerContentData d = data();
        if (d == null || d.container == null || !d.container.keepsContents()) return true;
        return !ContainerNesting.isContainer(stack);
    }

    /**
     * What Ctrl+pick puts inside the copied item.
     * <p>
     * Only two things call this — the server's pick-block handler and vanilla's
     * own shulker drop — so overriding it does not touch how the world is saved.
     * saveWithFullMetadata, which chunk serialisation uses, is final and keeps
     * writing the whole inventory.
     * <p>
     * HALF OF A DOUBLE CHEST COPIES ITS OWN HALF. In vanilla each half really
     * holds its 27 slots, so picking one gives you those. Here the main half
     * holds all 2N and the shell holds nothing, so without this picking the main
     * would hand you BOTH halves — duplicating them, since the original keeps
     * them — and picking the shell would hand you an empty chest.
     */
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.@NotNull Builder components) {
        super.collectImplicitComponents(components);

        // super attaches minecraft:container from THIS block entity's real
        // inventory. On the main half of a pair that is all 2N stacks, which
        // then arrive on placement alongside the half this mod wrote — the same
        // contents twice, by two different routes. One way in and one way out:
        // vanilla's copy goes, ours stays.
        components.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);

        ContainerContents half = halfForPick();
        if (half != null) {
            components.set(ComponentRegistry.CONTAINER_CONTENTS.get(), half);
            return;
        }

        // A single container writes the same component, for the same reason: the
        // default path attaches minecraft:container alongside block_entity_data,
        // and applying that on placement fights with what the mod reads. One way
        // in and one way out, whether the container is half of a pair or not.
        if (!isEmpty()) {
            components.set(ComponentRegistry.CONTAINER_CONTENTS.get(), snapshot());
        }
    }

    /**
     * Strips from block_entity_data what already travels as a component.
     * <p>
     * saveToItem attaches the whole block entity NBT, which on the main half of
     * a pair is all 2N slots — not the half collectImplicitComponents wrote.
     * On placement that arrives first, does not fit the single chest being
     * placed, and the excess is dropped on the ground while the component's
     * half goes inside. The same contents, twice, by two routes.
     * <p>
     * "Joined" goes too. It describes the pair this block WAS part of, and the
     * copy is always placed as a new block: carried over, it told the tick the
     * new pair was already merged, so the merge never ran — the menu showed a
     * single chest and the other half's items were unreachable.
     */
    @Override
    public void removeComponentsFromTag(@NotNull ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("Contents");
        output.discard("Joined");
    }

    /**
     * The slice this block should hand over, or null when it is not half of a
     * pair and the ordinary save is right.
     * <p>
     * The upper half belongs to the SHELL and the lower to the MAIN, the same
     * split breaking the pair uses — see CustomChestBlock.splitInventory.
     */
    private @Nullable ContainerContents halfForPick() {
        BlockState state = getBlockState();

        if (!state.hasProperty(CustomChestBlock.TYPE)
                || state.getValue(CustomChestBlock.TYPE) == ChestType.SINGLE
                || level == null) {
            return null;
        }

        BlockPos mainPos = CustomChestBlock.getMainPos(state, worldPosition);
        if (!(level.getBlockEntity(mainPos) instanceof CustomContainerBlockEntity main)) {
            return null;
        }

        int half = main.declaredSize();
        boolean isMain = mainPos.equals(worldPosition);
        int from = isMain ? half : 0;
        int to   = isMain ? main.getContainerSize() : half;

        List<ContainerContents.Entry> entries = new ArrayList<>();
        for (int i = from; i < to && i < main.getContainerSize(); i++) {
            ItemStack stack = main.getItem(i);
            if (!stack.isEmpty()) entries.add(new ContainerContents.Entry(i - from, stack.copy()));
        }
        return new ContainerContents(half, entries);
    }

}

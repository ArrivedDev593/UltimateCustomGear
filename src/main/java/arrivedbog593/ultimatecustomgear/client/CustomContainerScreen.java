package arrivedbog593.ultimatecustomgear.client;

import arrivedbog593.ultimatecustomgear.config.CustomGearConfig;
import arrivedbog593.ultimatecustomgear.menu.CustomContainerMenu;
import arrivedbog593.ultimatecustomgear.network.SortContainerPayload;
import arrivedbog593.ultimatecustomgear.network.SortModePayload;
import arrivedbog593.ultimatecustomgear.network.TransferItemsPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * Screen for every custom container, at any size.
 * <p>
 * NO GUI TEXTURE. The frame is drawn from rectangles and bevels in the vanilla
 * palette, because a texture would have to exist per size and sizes come from
 * user JSON. The storage mods this is modeled on slice a fixed 256x256 image
 * into three strips instead, which is why their column count is capped — the
 * texture decides the width.
 * <p>
 * ONE SILHOUETTE, NOT TWO PANELS. The container area and the player inventory
 * share a single outline shaped like a T: wide on top, narrower below, with the
 * border running around the whole contour and no seam between them. Two
 * separate frames read as two windows; this reads as one GUI, which is what the
 * fixed textures achieve for free.
 * <p>
 * VISIBLE ROWS ARE A PROPERTY OF THE WINDOW, NOT OF THE CONTAINER. How many
 * rows fit is computed from the actual window height on every build and resize,
 * so the same container scrolls on one player's setup and not on another's.
 * <p>
 * COLUMNS RE-FLOW. There is no horizontal scrolling, so when the declared
 * column count is wider than the window, the same slots are laid out in fewer
 * columns and more rows instead. That keeps every slot reachable.
 * <p>
 * SEARCH IS PURELY COSMETIC, AND THAT IS THE WHOLE TRICK. Filtering only
 * reorders and hides what is DRAWN; the menu keeps working with real slot
 * indices and never learns a query exists. Shift-click therefore keeps working
 * untouched, and nothing about the filter has to reach the server.
 */
public class CustomContainerScreen extends AbstractContainerScreen<CustomContainerMenu> {

    // ── Layout constants ──────────────────────────────────────────────────────

    private static final int SLOT = 18;
    /** Frame thickness around the slot area. */
    private static final int BORDER = 8;
    /** Title bar height above the first slot row. */
    private static final int TOP = 18;
    /**
     * Height of the "Inventory" label strip.
     * <p>
     * ONE value for both shapes. When the silhouette steps, the last three rows
     * of this strip are the wide box closing itself rather than blank panel, so
     * the visible clearance differs even though the number does not — which is
     * exactly how the storage mods this is modeled on behave.
     */
    private static final int LABEL_H = 15;
    /** Gap between the third inventory row and the hotbar. */
    private static final int HOTBAR_GAP = 4;
    private static final int PLAYER_WIDTH = 9 * SLOT;
    /** Breathing room between the GUI and the screen edges. */
    private static final int SCREEN_MARGIN = 8;

    /** Everything below the label strip, inside the narrow section. */
    private static final int BOTTOM_BLOCK =
            3 * SLOT + HOTBAR_GAP + SLOT + BORDER;

    private static final int SCROLLBAR_WIDTH = 8;

    /** Height of the search field. Flat, so it needs no room for bevels. */
    private static final int SEARCH_H = 10;
    /** Collapsed width: just enough for the magnifier glyph. */
    private static final int SEARCH_MIN = 10;
    /** Flat search background — no bevel, hence no palette entry. */
    private static final int SEARCH_BG = 0xFF777777;
    /** Expansion duration */
    private static final int SEARCH_ANIM_MS = 200;


    private static final Identifier ICONS =
            Identifier.fromNamespaceAndPath("customgear", "textures/gui/icons.png");
    private static final int ICON = 12;
    private static final int SHEET_W = 48;
    private static final int SHEET_H = 12;

    /**
     * Where hidden slots go. Slot coordinates are relative to topPos, and -100
     * is only off-screen when the GUI is tall enough to push topPos near zero —
     * which is true while scrolling but NOT for a small container that is merely
     * being filtered. AbstractContainerScreen renders every slot without a
     * bound check, so a shallow value makes hidden items float above the frame.
     */
    private static final int HIDDEN_Y = -10000;

    /** Bevel thickness inside the outline. */
    private static final int BEVEL = 2;

    // ── Palette ───────────────────────────────────────────────────────────────
    // Vanilla's inventory frame, from the outside in:
    //   1px black outline, 2px light bevel, fill, 2px dark bevel, 1px black.
    // The black outline is what was missing before, and it is most of why the
    // frame did not read as vanilla — every GUI in the game has it.

    private static final int OUTLINE     = 0xFF000000;
    private static final int PANEL       = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK  = 0xFF555555;
    private static final int SLOT_FILL   = 0xFF8B8B8B;
    private static final int SLOT_DARK   = 0xFF373737;
    private static final int BAR_THUMB   = 0xFFC0C0C0;
    private static final int BAR_BEVEL   = 0xFF808080;
    private static final int LABEL_TEXT  = 0xFF404040;
    /** Translucent whitewash over the search button while hovered. */
    private static final int HOVER_WASH  = 0x30FFFFFF;

    /** Search text while typing. White reads well over the sunken cell's gray. */
    private static final int SEARCH_TEXT      = 0xFFFFFFFF;
    /** Search text once focus is elsewhere: still legible, clearly inactive. */
    private static final int SEARCH_TEXT_IDLE = 0xFFE0E0E0;

    /** Softer than BEVEL_LIGHT: the small buttons are recessed, not raised. */
    private static final int BUTTON_LIGHT = 0xFFAAAAAA;

    // ── State ─────────────────────────────────────────────────────────────────

    /** Columns actually drawn — may be fewer than declared if the window is narrow. */
    private int columns;
    /** Rows the container needs at the current column count, ignoring any filter. */
    private int unfilteredRows;
    /** Rows the current view needs — the filtered count when a query is active. */
    private int totalRows;
    /** Rows that fit on screen right now. Fixed by the window, never by the filter. */
    private int visibleRows;
    /** First visible row of the current view. */
    private int rowOffset;

    private boolean draggingBar;

    private EditBox searchBox;
    /** Current animated width. Updated once per frame, read by everything else. */
    private int searchAnimWidth = SEARCH_MIN;
    /** Width the current animation started from, and when. */
    private int searchAnimFrom = SEARCH_MIN;
    private long searchAnimStart;
    /** The state the animation is heading towards, to notice when it flips. */
    private boolean searchAnimExpanded;

    /** Mirrors the block's remembered mode, read from the menu at init. */
    private SortCriterion sortCriterion = SortCriterion.NAME;
    private boolean sortDescending;

    /** True when the cursor is over one of the player's own slots, hotbar included. */
    private boolean overPlayerInventory() {
        return hoveredSlot != null && hoveredSlot.index >= menu.getPlayerSlotStart();
    }

    /** Survives init() so a window resize does not wipe what was typed. */
    private String query;
    /**
     * The query outlives the screen: closing and reopening a container keeps the
     * filter. Static rather than per-container because the state is a user
     * intent ("I am looking for X"), not a property of any one chest.
     */
    private static String lastQuery = "";
    /** Null while the query is blank: that is the "no filtering at all" shortcut. */
    private Predicate<ItemStack> filter;
    /** Container slot indices currently shown, in display order. Null means all of them. */
    private int[] filtered;
    /** Per-slot fingerprint, to notice content changes without a listener. */
    private int[] fingerprint = new int[0];

    public CustomContainerScreen(CustomContainerMenu menu, Inventory inv, Component title) {
        // Placeholder size. The base class demands one in its constructor now, but
        // the real frame depends on the window, which only init() can measure — it
        // overwrites both fields there (see the access transformer).
        super(menu, inv, title, 176, 166);
        if (CustomGearConfig.KEEP_SEARCH_PHRASE.get()) {
            this.query = lastQuery;
        } else {
            // Opted out: drop whatever was remembered so it cannot leak into
            // the next container the moment the option is turned back on.
            lastQuery = "";
            this.query = "";
        }
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        // Widest column count the window can take, never below the player
        // inventory's own 9 — narrower than that, and the layout makes no sense.
        int maxColumns = Math.max(9,
                (width - 2 * SCREEN_MARGIN - 2 * BORDER - SCROLLBAR_WIDTH) / SLOT);
        columns = Math.min(menu.getColumns(), maxColumns);
        unfilteredRows = ceilDiv(menu.getSize(), columns);

        int maxRows = Math.max(1,
                (height - 2 * SCREEN_MARGIN - TOP - LABEL_H - BOTTOM_BLOCK) / SLOT);
        // Sized from the UNFILTERED row count: a query must never resize the frame.
        visibleRows = Math.min(unfilteredRows, maxRows);

        imageWidth = Math.max(lowerWidth(), columns * SLOT + 2 * BORDER + gutter());
        imageHeight = upperHeight() + LABEL_H + BOTTOM_BLOCK;

        // Everything below needs leftPos and topPos, which super.init() sets.
        super.init();

        titleLabelX = BORDER;
        titleLabelY = 6;
        inventoryLabelX = lowerX() - leftPos + BORDER;
        inventoryLabelY = upperHeight() + 3;

        buildSearchBox();
        // Snap on open: a restored query should already be expanded, not slide in.
        searchAnimExpanded = searchExpanded();
        searchAnimWidth = searchAnimExpanded ? searchFullWidth() : SEARCH_MIN;
        if (searchAnimExpanded) {
            searchBox.setWidth(Math.max(1, searchAnimWidth - 4));
            // setValue computed the scroll offset against the collapsed width, and
            // moving the cursor to where it already is recomputes nothing. Bounce
            // it to the start and back so the widget re-measures at the real width.
            searchBox.moveCursorTo(0, false);
            searchBox.moveCursorToEnd(false);
        }
        compileFilter();
        refilter();
        sortCriterion  = SortCriterion.byId(menu.getSortCriterion());
        sortDescending = menu.isSortDescending();
    }

    private void buildSearchBox() {
        // Built collapsed on purpose: searchExpanded() reads searchBox, which is
        // still null here, so it answers false. If the restored query is not
        // blank the first frame expands the field.

        searchBox = new EditBox(font, searchX() + 2, searchY() + 1, searchWidth() - 4, 8,
                Component.translatable("customgear.container.search"));
        searchBox.setBordered(false);          // the frame is drawn by bevelCell
        searchBox.setMaxLength(64);
        searchBox.setTextColor(SEARCH_TEXT_IDLE);
        searchBox.setHint(Component.literal("\uD83D\uDD0D"));
        searchBox.setValue(query);
        // Responder AFTER setValue: setValue fires it, and recompiling here
        // would reset rowOffset on every window resize.
        searchBox.setResponder(this::onQueryChanged);
        addRenderableWidget(searchBox);
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    // Upper part: full imageWidth, holds the title and the container rows.
    // Lower part: 9 columns wide, centred, holds the player inventory.
    // When the container is 9 wide or narrower, the two match, and the shape is a
    // plain rectangle — the step lengths simply become zero.

    private int upperHeight() { return TOP + visibleRows * SLOT; }
    private int lowerWidth()  { return PLAYER_WIDTH + 2 * BORDER; }
    private int lowerX()      { return leftPos + (imageWidth - lowerWidth()) / 2; }

    private int containerGridX() {
        return leftPos + (imageWidth - gutter() - columns * SLOT) / 2;
    }

    /** Extra width reserved on the right for the scrollbar — zero when it is not drawn. */
    private int gutter() {
        return hasScrollbar() ? SCROLLBAR_WIDTH : 0;
    }

    /**
     * Whether the frame reserves room for the bar. Decided by the UNFILTERED
     * container so that filtering never changes imageWidth.
     */
    private boolean hasScrollbar() { return unfilteredRows > visibleRows; }

    /** Whether there is anything to scroll right now — the filter can flatten this. */
    private boolean canScroll() { return totalRows > visibleRows; }

    /** Rows the view can travel. Never negative, even when a filter emptied it. */
    private int scrollRange() { return Math.max(0, totalRows - visibleRows); }

    /**
     * Expanded while it has focus or something typed in it. Deriving this rather
     * than storing it is what makes an active filter impossible to hide: the
     * field cannot collapse while it is the reason slots are missing.
     */
    private boolean searchExpanded() {
        return searchBox != null && (searchBox.isFocused() || !searchBox.getValue().isEmpty());
    }

    /** The sort buttons own the right end of the strip; the field grows up to them. */
    private int searchRight() {
        return leftPos + imageWidth - gutter() - BORDER - 2 * BUTTON - 4;
    }

    private int sortCriterionX() { return leftPos + imageWidth - gutter() - BORDER - BUTTON; }
    private int sortButtonX()    { return sortCriterionX() - BUTTON - 2; }
    private int sortY()          { return searchY(); }
    private int searchY()     { return topPos + 5; }
    private int searchFullWidth() { return searchRight() - leftPos - BORDER; }

    /** The animated width, not the target. Everything positions off this. */
    private int searchWidth() { return searchAnimWidth; }


    private static final int BUTTON = 10;

    /** Aligned with the label's baseline, like the storage mods do it. */
    private int transferY()    { return topPos + inventoryLabelY - 1; }
    private int transferOutX() { return lowerX() + lowerWidth() - BORDER - BUTTON; }
    private int transferInX()  { return transferOutX() - BUTTON - 2; }

    private boolean over(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON && mouseY >= y && mouseY < y + BUTTON;
    }

    /**
     * Advances the expansion. Driven by wall clock rather than by ticks so the
     * slide runs at the same speed regardless of framerate or server lag.
     */
    private void tickSearchAnimation() {
        boolean expanded = searchExpanded();
        if (expanded != searchAnimExpanded) {
            searchAnimExpanded = expanded;
            searchAnimFrom = searchAnimWidth;
            searchAnimStart = System.currentTimeMillis();
            if (expanded) {
                searchBox.moveCursorTo(0, false);
                searchBox.moveCursorToEnd(false);
            }
        }
        int target = expanded ? searchFullWidth() : SEARCH_MIN;
        float t = Math.min((System.currentTimeMillis() - searchAnimStart) / (float) SEARCH_ANIM_MS, 1f);
        searchAnimWidth = searchAnimFrom + Math.round((target - searchAnimFrom) * easeInOutCubic(t));
    }

    /** Slow at both ends, quick in the middle. */
    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    /** True while the field is wider than its collapsed footprint, mid-slide included. */
    private boolean searchOccupiesStrip() { return searchAnimWidth > SEARCH_MIN; }


    /** Grows leftwards from a fixed right edge, so the magnifier never moves. */
    private int searchX()     { return searchRight() - searchWidth(); }

    private boolean overSearch(double mouseX, double mouseY) {
        return mouseX >= searchX() && mouseX < searchX() + searchWidth()
                && mouseY >= searchY() && mouseY < searchY() + SEARCH_H;
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= barX() && mouseX < barX() + SCROLLBAR_WIDTH
                && mouseY >= barY() && mouseY < barY() + barHeight();
    }

    // ── Slot placement ────────────────────────────────────────────────────────

    /**
     * Position in the shown list is where a slot is DRAWN; the index inside it
     * is what the menu uses. Hidden slots go to HIDDEN_Y, far enough off-screen
     * that they stay invisible even when the topPos is large.
     */
    private void layoutSlots() {
        int size = menu.getSize();
        int gridX = containerGridX() - leftPos;
        int shown = shownCount();

        // Park everything first: a slot that dropped out of the filter has to
        // leave, and walking only the shown list would never touch it.
        for (int i = 0; i < size; i++) {
            menu.slots.get(i).y = HIDDEN_Y;
        }

        for (int position = 0; position < shown; position++) {
            int row = position / columns - rowOffset;
            if (row < 0 || row >= visibleRows) continue;

            Slot slot = menu.slots.get(filtered == null ? position : filtered[position]);
            slot.x = gridX + (position % columns) * SLOT + 1;
            slot.y = TOP + row * SLOT + 1;
        }

        int playerX = lowerX() - leftPos + BORDER + 1;
        int playerY = upperHeight() + LABEL_H + 1;
        for (int i = 0; i < 27; i++) {
            Slot slot = menu.slots.get(size + i);
            slot.x = playerX + (i % 9) * SLOT;
            slot.y = playerY + (i / 9) * SLOT;
        }
        for (int i = 0; i < 9; i++) {
            Slot slot = menu.slots.get(size + 27 + i);
            slot.x = playerX + i * SLOT;
            slot.y = playerY + 3 * SLOT + HOTBAR_GAP;
        }
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    /** How many slots the current view shows. */
    private int shownCount() { return filtered == null ? menu.getSize() : filtered.length; }

    private void onQueryChanged(String text) {
        query = text;
        if (CustomGearConfig.KEEP_SEARCH_PHRASE.get()) lastQuery = text;
        rowOffset = 0;
        compileFilter();
        refilter();
    }

    private void compileFilter() {
        Minecraft mc = Minecraft.getInstance();
        filter = ContainerSearch.parse(query, Item.TooltipContext.of(mc.level), mc.player);
    }

    /**
     * Rebuilds the list of shown slot indices. Nothing on the server ever sees
     * the difference: the menu still addresses slots by their real index.
     */
    private void refilter() {
        if (filter == null) {
            filtered = null;
            totalRows = unfilteredRows;
        } else {
            int size = menu.getSize();
            int[] buffer = new int[size];
            int found = 0;
            for (int i = 0; i < size; i++) {
                if (filter.test(menu.slots.get(i).getItem())) buffer[found++] = i;
            }
            filtered = Arrays.copyOf(buffer, found);
            totalRows = Math.max(1, ceilDiv(found, columns));
        }
        rowOffset = Math.clamp(rowOffset, 0, scrollRange());
        layoutSlots();
    }

    /**
     * The client never runs broadcastChanges, so a ContainerListener would never
     * fire here — server syncs go through Slot.set, which does not notify. A
     * per-slot fingerprint catches both those and locally predicted changes, and
     * running it from containerTick coalesces a whole inventory sync into one
     * recompute instead of three hundred.
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (filter == null) return;    // nothing is hidden, nothing to recompute
        if (contentChanged()) refilter();
    }

    private boolean contentChanged() {
        int size = menu.getSize();
        if (fingerprint.length != size) fingerprint = new int[size];

        boolean changed = false;
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            int current = stack.isEmpty()
                    ? 0
                    : System.identityHashCode(stack.getItem()) * 31 + stack.getCount();
            if (fingerprint[i] != current) {
                fingerprint[i] = current;
                changed = true;
            }
        }
        return changed;
    }

    // ── Search field ──────────────────────────────────────────────────────────

    /** Both calls are needed: the screen tracks the focused child, the widget its cursor. */
    private void focusSearch() {
        setFocused(searchBox);
        searchBox.setFocused(true);
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

    /**
     * Works out the order and sends it. The client decides because sorting by
     * display name needs the language files, which a dedicated server has none
     * of — this is the whole reason the packet carries an order instead of a
     * criterion.
     */
    private void requestSort() {
        // One entry per distinct type, with the totals COUNT needs.
        List<ItemStack> types = new ArrayList<>();
        Map<ItemStack, Integer> totals = new IdentityHashMap<>();

        for (int i = 0; i < menu.getSize(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (stack.isEmpty()) continue;

            ItemStack seen = null;
            for (ItemStack t : types) {
                if (ItemStack.isSameItemSameComponents(t, stack)) { seen = t; break; }
            }
            if (seen == null) {
                seen = stack.copyWithCount(1);
                types.add(seen);
                totals.put(seen, 0);
            }
            totals.put(seen, totals.get(seen) + stack.getCount());
        }

        types.sort(sortCriterion.comparator(totals));
        // Descending is the same list backwards: the server never learns there
        // is such a thing as a direction.
        if (sortDescending) types = types.reversed();

        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new SortContainerPayload(types));
    }

    private void cycleSortCriterion() {
        sortCriterion = sortCriterion.next();
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new SortModePayload((byte) sortCriterion.ordinal(), sortDescending));
    }

    private void toggleSortDirection() {
        sortDescending = !sortDescending;
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(
                new SortModePayload((byte) sortCriterion.ordinal(), sortDescending));
    }

    // ── Background ────────────────────────────────────────────────────────────

    /** Draw the background of the container screen. */
    @Override
    public void extractBackground(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        drawSilhouette(g);
        drawSortButtons(g, mouseX, mouseY);

        int gridX = containerGridX();
        int gridY = topPos + TOP;
        int shown = shownCount();
        for (int row = 0; row < visibleRows; row++) {
            int firstPosition = (row + rowOffset) * columns;
            if (firstPosition >= shown) break;
            // The last drawn row may be partially filled — shown slots are a
            // count, not a grid, and a filter makes that the common case.
            int inThisRow = Math.min(columns, shown - firstPosition);
            for (int col = 0; col < inThisRow; col++) {
                slot(g, gridX + col * SLOT, gridY + row * SLOT);
            }
        }

        if (hasScrollbar()) drawScrollbar(g);
        drawSearchWidgets(g, mouseX, mouseY);

        int slotsX = lowerX() + BORDER;
        int slotsY = topPos + upperHeight() + LABEL_H;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, slotsX + col * SLOT, slotsY + row * SLOT);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, slotsX + col * SLOT, slotsY + 3 * SLOT + HOTBAR_GAP);
        }
        drawTransferButtons(g, mouseX, mouseY);
    }

    /** Drawn from renderBg so the EditBox, which has no border of its own, sits on top. */
    private void drawSearchWidgets(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // The width follows focus and content, so the widget is repositioned
        // every frame rather than rebuilt.
        tickSearchAnimation();
        boolean expanded = searchExpanded();
        searchBox.setX(searchX() + 2);
        searchBox.setWidth(Math.max(1, searchWidth() - 4));
        searchBox.setTextColor(searchBox.isFocused() ? SEARCH_TEXT : SEARCH_TEXT_IDLE);
        searchBox.visible = true;

        // Flat fill, no bevel: the field is meant to read as a recess in the
        // title bar, not as another slot.
        g.fill(searchX(), searchY(), searchX() + searchWidth(), searchY() + SEARCH_H, SEARCH_BG);
        if (!expanded && overSearch(mouseX, mouseY)) {
            g.fill(searchX(), searchY(), searchX() + SEARCH_MIN, searchY() + SEARCH_H, HOVER_WASH);
        }
    }

    /** The icon changes with Shift so the modifier is discoverable, not documented. */
    private void drawTransferButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean all = Minecraft.getInstance().hasShiftDown();
        drawButton(g, transferInX(),  transferY(), all ? 0 : 1, mouseX, mouseY);
        drawButton(g, transferOutX(), transferY(), all ? 2 : 3, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor g, int x, int y, int icon, int mouseX, int mouseY) {
        buttonCell(g, x, y);
        // The 12px icon overhangs the 10px frame by a pixel on each side, so the
        // glyph reads bigger than the button that holds it.
        drawIcon(g, x - 1, y - 1, icon);
        if (over(mouseX, mouseY, x, y)) {
            g.fill(x + 1, y + 1, x + BUTTON - 1, y + BUTTON - 1, HOVER_WASH);
        }
    }

    /** Sort arrow plus the criterion letter, at the right end of the title bar. */
    private void drawSortButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // Cells 0 and 2 are the solid arrows: up for ascending, down for the
        // reversed order, so the direction is visible without a tooltip.
        drawButton(g, sortButtonX(), sortY(), sortDescending ? 2 : 0, mouseX, mouseY);

        buttonCell(g, sortCriterionX(), sortY());
        Component letter = sortCriterion.letter();
        g.text(font, letter,
                sortCriterionX() + (BUTTON - font.width(letter)) / 2,
                sortY() + 2, LABEL_TEXT, false);
        if (over(mouseX, mouseY, sortCriterionX(), sortY())) {
            g.fill(sortCriterionX() + 1, sortY() + 1,
                    sortCriterionX() + BUTTON - 1, sortY() + BUTTON - 1, HOVER_WASH);
        }
    }

    // ── Frame drawing ─────────────────────────────────────────────────────────

    /** Outline inset where a single bevel turns — top-left, bottom-right. */
    private static int tightCorner(int d) { return Math.max(0, 2 - d); }

    /** Outline inset where the light and dark bevels meet — top-right, bottom-left. */
    private static int wideCorner(int d) { return Math.max(0, 3 - d); }

    /**
     * The frame is two rounded boxes, not one silhouette with a step in it.
     * The wide box closes with an ordinary rounded bottom — byte for byte the
     * same pattern the narrow box uses at the very bottom of the GUI — and the
     * narrow box is drawn over its last three rows. Only the two concave
     * corners where they meet need anything special.
     */
    private void drawSilhouette(GuiGraphicsExtractor g) {
        int x0 = leftPos;
        int x1 = leftPos + imageWidth;
        int y0 = topPos;
        int yStep = topPos + upperHeight() + LABEL_H;   // first row of the narrow box
        int y1 = topPos + imageHeight;

        int lx0 = lowerX();
        int lx1 = lowerX() + lowerWidth();

        if (lx0 <= x0) {
            // 9 columns or fewer: the two boxes are the same width, so drawing
            // them separately would leave a black seam across the middle.
            drawBox(g, x0, y0, x1, y1, true, true);
            return;
        }

        drawBox(g, x0, y0, x1, yStep, true, false);
        drawBox(g, lx0, yStep, lx1, y1, false, true);
        drawJunction(g, lx0, lx1, yStep);
    }

    /**
     * One rounded box. {@code roundTop} is false for the narrow box, whose top
     * edge is square because the box above it supplies the border there.
     * {@code outerBottom} marks the bottom corner as belonging to the outside of
     * the whole shape, which is the only place the dark bevel widens to three.
     */
    private void drawBox(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1,
                         boolean roundTop, boolean outerBottom) {
        for (int y = y0; y < y1; y++) {
            int dTop = roundTop ? y - y0 : Integer.MAX_VALUE / 2;
            int dBot = y1 - 1 - y;

            int left  = x0 + Math.max(tightCorner(dTop), wideCorner(dBot));
            int right = x1 - Math.max(wideCorner(dTop), tightCorner(dBot));
            if (left >= right) continue;

            g.fill(left, y, left + 1, y + 1, OUTLINE);
            g.fill(right - 1, y, right, y + 1, OUTLINE);

            int innerL = left + 1;
            int innerR = right - 1;
            if (innerL >= innerR) continue;

            if (dTop == 0 || dBot == 0) {
                g.fill(innerL, y, innerR, y + 1, OUTLINE);
            } else if (dTop == 1) {
                g.fill(innerL, y, innerR, y + 1, BEVEL_LIGHT);
            } else if (dBot == 1) {
                g.fill(innerL, y, innerR, y + 1, BEVEL_DARK);
            } else if (dTop == 2) {
                // The dark bevel has not started yet: a single fill pixel sits
                // between the light bevel and the outline.
                g.fill(innerL, y, innerR - 1, y + 1, BEVEL_LIGHT);
                g.fill(innerR - 1, y, innerR, y + 1, PANEL);
            } else if (dBot == 2) {
                g.fill(innerL, y, innerL + 1, y + 1, PANEL);
                g.fill(innerL + 1, y, innerR, y + 1, BEVEL_DARK);
            } else {
                int lw = (roundTop && dTop == 3) ? 3 : BEVEL;
                int rw = (outerBottom && dBot == 3) ? 3 : BEVEL;
                int fillL = Math.min(innerL + lw, innerR);
                int fillR = Math.max(innerR - rw, fillL);
                g.fill(innerL, y, fillL, y + 1, BEVEL_LIGHT);
                g.fill(fillL,  y, fillR, y + 1, PANEL);
                g.fill(fillR,  y, innerR, y + 1, BEVEL_DARK);
            }
        }
    }

    /**
     * The concave corners. Over the three rows above the step the narrow box's
     * body already shows through the wide one's closing bevel, one pixel wider
     * per row down. On the light side the turn is softened with two pixels of
     * slot gray; on the dark side the bevel runs straight into the band.
     */
    private void drawJunction(GuiGraphicsExtractor g, int lx0, int lx1, int yStep) {
        for (int i = 3; i >= 1; i--) {
            int inset = 4 - i;
            if (lx0 + inset < lx1 - inset) {
                g.fill(lx0 + inset, yStep - i, lx1 - inset, yStep - i + 1, PANEL);
            }
        }
        g.fill(lx1 - 3, yStep - 1, lx1, yStep, BEVEL_DARK);
        g.fill(lx0,     yStep - 1, lx0 + 3, yStep,     BEVEL_LIGHT);
        g.fill(lx0,     yStep - 1, lx0 + 1, yStep,     SLOT_FILL);
        g.fill(lx0 + 1, yStep - 2, lx0 + 2, yStep - 1, SLOT_FILL);
        g.fill(lx0 - 1, yStep,     lx0,     yStep + 1, OUTLINE);
    }

    /** Icon cells: 0 up solid, 1 up split, 2 down solid, 3 down split. */
    private void drawIcon(GuiGraphicsExtractor g, int x, int y, int col) {
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, col * ICON, 0, ICON, ICON, SHEET_W, SHEET_H);
    }

    // ── Cell primitives ───────────────────────────────────────────────────────

    private void slot(GuiGraphicsExtractor g, int x, int y) {
        bevelCell(g, x, y, SLOT, SLOT);
    }

    /** Sunken cell. Each bevel is a continuous L that owns its own corner. */
    private void bevelCell(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_FILL);
        g.fill(x, y,     x + w - 1, y + 1,     SLOT_DARK);
        g.fill(x, y + 1, x + 1,     y + h - 1, SLOT_DARK);
        g.fill(x + 1,     y + h - 1, x + w, y + h,     BEVEL_LIGHT);
        g.fill(x + w - 1, y + 1,     x + w, y + h - 1, BEVEL_LIGHT);
    }

    /**
     * The small square buttons in the label strip. Recessed like a slot but with
     * a softer highlight: white would make them louder than the frame they sit in.
     */
    private void buttonCell(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + BUTTON, y + BUTTON, SLOT_FILL);
        g.fill(x, y,              x + BUTTON - 1, y + 1,          BUTTON_LIGHT);
        g.fill(x, y + 1,          x + 1,          y + BUTTON - 1, BUTTON_LIGHT);
        g.fill(x + 1,          y + BUTTON - 1, x + BUTTON, y + BUTTON,     BEVEL_DARK);
        g.fill(x + BUTTON - 1, y + 1,          x + BUTTON, y + BUTTON - 1, BEVEL_DARK);
    }

    // ── Scrollbar ─────────────────────────────────────────────────────────────

    private int barX() { return leftPos + imageWidth - BORDER - SCROLLBAR_WIDTH; }
    private int barY() { return topPos + TOP; }
    private int barHeight() { return visibleRows * SLOT; }

    private int thumbHeight() {
        // A filter can leave fewer rows than fit: the thumb then fills the track
        // and there is nothing to travel.
        if (!canScroll()) return barHeight();
        return Math.max(12, barHeight() * visibleRows / totalRows);
    }

    private int thumbY() {
        int travel = barHeight() - thumbHeight();
        int range = scrollRange();
        return barY() + (range == 0 ? 0 : travel * rowOffset / range);
    }

    private void drawScrollbar(GuiGraphicsExtractor g) {
        int x = barX();
        int y = barY();
        int h = barHeight();

        g.fill(x, y, x + SCROLLBAR_WIDTH, y + h, OUTLINE);

        int ty = thumbY();
        int th = thumbHeight();
        g.fill(x, ty, x + SCROLLBAR_WIDTH, ty + th, BAR_THUMB);
        g.fill(x, ty + th - 1, x + SCROLLBAR_WIDTH, ty + th, BAR_BEVEL);
        g.fill(x + SCROLLBAR_WIDTH - 1, ty, x + SCROLLBAR_WIDTH, ty + th, BAR_BEVEL);
    }

    private void scrollTo(int newOffset) {
        int clamped = Math.clamp(newOffset, 0, scrollRange());
        if (clamped != rowOffset) {
            rowOffset = clamped;
            layoutSlots();
        }
    }

    private void dragTo(double mouseY) {
        int travel = barHeight() - thumbHeight();
        if (travel <= 0) return;
        double rel = (mouseY - barY() - thumbHeight() / 2.0) / travel;
        scrollTo((int) Math.round(rel * scrollRange()));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (canScroll()) {
            scrollTo(rowOffset - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        // Mouse bindings never reach keyPressed, so the same shortcut has to be
        // checked here too — the player can move it between keyboard and mouse.
        //
        // NOT over the player's own inventory: middle-click there is vanilla's
        // "copy stack" in creative, and sorting the chest instead would take a
        // gesture the player already knows and do something else with it. Empty
        // space and the container's own grid are fair game.
        if (CustomGearKeys.SORT.matchesMouse(event) && !overPlayerInventory()) {
            requestSort();
            playClick();
            return true;
        }
        if (handleTransferKeys(null, event)) return true;
        if (overSearch(mouseX, mouseY)) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                focusSearch();
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                searchBox.setValue("");   // right-click clears
                return true;
            }
        }
        if (over(mouseX, mouseY, sortCriterionX(), sortY())) {
            cycleSortCriterion();
            playClick();
            return true;
        }
        // Left sorts, right flips the direction — the same "right-click modifies
        // rather than executes" gesture the search field already uses.
        if (over(mouseX, mouseY, sortButtonX(), sortY())) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                requestSort();
                playClick();
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                toggleSortDirection();
                playClick();
                return true;
            }
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            boolean all = Minecraft.getInstance().hasShiftDown();
            if (over(mouseX, mouseY, transferInX(), transferY())) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new TransferItemsPayload(true, all));
                playClick();
                return true;
            }
            if (over(mouseX, mouseY, transferOutX(), transferY())) {
                net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new TransferItemsPayload(false, all));
                playClick();
                return true;
            }
        } else if (button == InputConstants.MOUSE_BUTTON_RIGHT && (over(mouseX, mouseY, transferInX(), transferY()) || over(mouseX, mouseY, transferOutX(), transferY()))) {
            playClick();   // right-click on the transfer buttons does nothing but sound
        }
        if (canScroll() && overScrollbar(mouseX, mouseY)) {
            draggingBar = true;
            dragTo(mouseY);
            return true;
        }
        // Clicking elsewhere stops typing. The field stays expanded if anything
        // is written, so the filter never hides its own cause.
        if (searchBox.isFocused() && !overSearch(mouseX, mouseY)) {
            searchBox.setFocused(false);
            setFocused(null);
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The vanilla UI click, so the buttons feel like the rest of the game's menus. */
    private void playClick() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseDragged(@NotNull MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    /**
     * Fires a transfer binding if one matches. Shift means "everything", exactly
     * as it does on the buttons — one gesture, two ways to reach it.
     * <p>
     * NOT over the player's own inventory, same rule as sorting: those slots
     * belong to vanilla's gestures.
     */
    private boolean handleTransferKeys(@Nullable KeyEvent keyEvent, @Nullable MouseButtonEvent mouseEvent) {
        if (overPlayerInventory()) return false;

        boolean all = Minecraft.getInstance().hasShiftDown();
        boolean in  = mouseEvent != null
                ? CustomGearKeys.TRANSFER_IN.matchesMouse(mouseEvent)
                : CustomGearKeys.TRANSFER_IN.matches(keyEvent);
        boolean out = mouseEvent != null
                ? CustomGearKeys.TRANSFER_OUT.matchesMouse(mouseEvent)
                : CustomGearKeys.TRANSFER_OUT.matches(keyEvent);

        if (in) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new TransferItemsPayload(true, all));
            playClick();
            return true;
        }
        if (out) {
            net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(new TransferItemsPayload(false, all));
            playClick();
            return true;
        }
        return false;
    }


    /**
     * Escape collapses the field before it closes the screen — losing the whole
     * container because you wanted to cancel a search is the wrong default. With
     * the field focused every other key is a character, including the inventory
     * key, which would otherwise close the GUI on every "e".
     */
    @Override
    public boolean keyPressed(@NotNull KeyEvent event) {
        if (searchBox.isFocused() && event.key() == InputConstants.KEY_ESCAPE) {
            searchBox.setFocused(false);
            setFocused(null);
            return true;
        }
        // Not while typing: if the binding is a letter, a letter in the search
        // field is a letter.
        if (!searchBox.isFocused() && CustomGearKeys.SORT.matches(event)) {
            requestSort();
            playClick();
            return true;
        }
        if (!searchBox.isFocused() && handleTransferKeys(event, null)) return true;
        if (searchBox.isFocused()) {
            if (searchBox.keyPressed(event)) return true;
            if (searchBox.canConsumeInput()) return true;
        }
        return super.keyPressed(event);
    }


    // ── Foreground ────────────────────────────────────────────────────────────

    /** A long container name is trimmed to the frame instead of spilling past it. */
    @Override
    protected void extractLabels(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // While the field is expanded, it owns the whole strip, so the name steps
        // aside rather than being squeezed into an unreadable stub.
        if (!searchExpanded()) {
            int maxTitleWidth = imageWidth - 2 * BORDER - gutter() - SEARCH_MIN - 2;
            Component shown = title;
            if (font.width(shown) > maxTitleWidth) {
                shown = Component.literal(font.plainSubstrByWidth(
                        title.getString(), maxTitleWidth - font.width("...")) + "...");
            }
            g.text(font, shown, titleLabelX, titleLabelY, LABEL_TEXT, false);
        }
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_TEXT, false);

        // Without this a query that matches nothing looks like an empty container
        // rather than a filtered one.
        if (filter != null && shownCount() == 0) {
            g.text(font, Component.translatable("customgear.container.no_results"),
                    containerGridX() - leftPos, TOP + 4, LABEL_TEXT, false);
        }
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // No background pass here: extractBackground already ran, and the hovered
        // slot tooltip is part of super now — the explicit renderTooltip call this
        // replaced would draw it a second time.
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        // Only over the collapsed magnifier: the help would cover the field itself.
        if (!searchOccupiesStrip() && overSearch(mouseX, mouseY)) {
            g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("customgear.container.search"),
                            hint("customgear.container.search.mod", ChatFormatting.GRAY),
                            hint("customgear.container.search.tag", ChatFormatting.GRAY),
                            hint("customgear.container.search.tooltip", ChatFormatting.GRAY),
                            hint("customgear.container.search.id", ChatFormatting.GRAY)),
                    mouseX, mouseY);
        }
        drawTransferTooltip(g, mouseX, mouseY, transferInX(),  "in");
        drawTransferTooltip(g, mouseX, mouseY, transferOutX(), "out");
        if (over(mouseX, mouseY, sortButtonX(), sortY())) {
            g.setComponentTooltipForNextFrame(font, List.of(
                            Component.translatable("customgear.container.sort"),
                            hint("customgear.container.sort.reverse", ChatFormatting.DARK_GRAY)),
                    mouseX, mouseY);
        }
        if (over(mouseX, mouseY, sortCriterionX(), sortY())) {
            g.setComponentTooltipForNextFrame(font, List.of(sortCriterion.label()), mouseX, mouseY);
        }
    }

    /** The Shift hint disappears once Shift is held: it would be describing the present. */
    private void drawTransferTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, int x, String key) {
        if (!over(mouseX, mouseY, x, transferY())) return;

        boolean all = Minecraft.getInstance().hasShiftDown();
        List<Component> lines = new ArrayList<>(2);
        lines.add(Component.translatable(
                "customgear.container.transfer_" + key + (all ? ".all" : "")));
        if (!all) {
            lines.add(hint("customgear.container.transfer.shift", ChatFormatting.DARK_GRAY));
        }
        g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
    }

    /** Secondary tooltip line: dimmer than the title so the two read as a hierarchy. */
    private static Component hint(String key, ChatFormatting color) {
        return Component.translatable(key).withStyle(color);
    }

    private static int ceilDiv(int a, int b) {
        return a / b + (a % b > 0 ? 1 : 0);
    }
}
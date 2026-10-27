package arrivedbog593.ultimatecustomgear.network;

import arrivedbog593.ultimatecustomgear.compat.CuriosCompat;
import arrivedbog593.ultimatecustomgear.config.CustomGearConfig;
import arrivedbog593.ultimatecustomgear.config.CustomGearConfig.HandshakeMode;
import arrivedbog593.ultimatecustomgear.items.containers.BackpackAnchor;
import arrivedbog593.ultimatecustomgear.items.containers.CustomBackpackItem;
import arrivedbog593.ultimatecustomgear.menu.CustomContainerMenu;
import arrivedbog593.ultimatecustomgear.util.ContentHasher;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Configuration-phase handshake that verifies the joining client LOADED the
 * same ultimatecustomgear content as the server.
 * <p>
 * The server's config (content_handshake_mode) decides what a mismatch means:
 * <ul>
 *   <li>ENFORCE (default) — the client is disconnected with a message
 *       showing both hashes and how to fix it.</li>
 *   <li>WARN — the client is let in; the server logs the mismatch and the
 *       client shows itself a chat warning after joining.</li>
 *   <li>OFF — the configuration task isn't registered; no check happens.</li>
 * </ul>
 * The mode travels INSIDE the payload: the client obeys what the server
 * sends, so a client's local config can never weaken a server's enforcement.
 * <p>
 * Both sides compare {@link ContentHasher#current()} — the hash captured when
 * content was loaded into memory — NOT the disk state at connection time
 * (item attributes bake at registration; editing a JSON after startup changes
 * the disk but not the running game).
 */
public final class CustomGearNetworking {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    /**
     * Payload protocol version — bumped to "3" when the enforce flag was
     * added to HashCheckPayload. Clients and servers on different protocol
     * versions are rejected by NeoForge's channel negotiation with a clear
     * version-mismatch message instead of a codec crash.
     * <p>
     * Bump it only when the wire format of an EXISTING payload changes.
     * Adding a payload doesn't need it: negotiation already refuses a peer
     * that lacks a required channel (that is how 1.6.x and 1.7.x keep apart),
     * and a bump would also cut off every release that shares this protocol.
     */
    private static final String PROTOCOL_VERSION = "3";

    /**
     * Set on the CLIENT when it joins a WARN-mode server with mismatching
     * files. Client-side join handling reads and clears it to show a chat
     * warning (see ClientSetup). Volatile: written on the network thread,
     * read on the client thread.
     */
    public static volatile boolean pendingMismatchWarning = false;

    private CustomGearNetworking() {}

    // ── Registration (both called from CustomGearMod on the MOD event bus) ───

    public static void registerPayloads(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);

        registrar.configurationToClient(
                HashCheckPayload.TYPE,
                HashCheckPayload.STREAM_CODEC,
                CustomGearNetworking::handleHashCheckOnClient);

        registrar.configurationToServer(
                HashCheckAckPayload.TYPE,
                HashCheckAckPayload.STREAM_CODEC,
                CustomGearNetworking::handleAckOnServer);

        registrar.playToServer(TransferItemsPayload.TYPE, TransferItemsPayload.STREAM_CODEC,
                CustomGearNetworking::handleTransfer);

        registrar.playToServer(SortContainerPayload.TYPE, SortContainerPayload.STREAM_CODEC,
                CustomGearNetworking::handleSort);

        registrar.playToServer(SortModePayload.TYPE, SortModePayload.STREAM_CODEC,
                CustomGearNetworking::handleSortMode);

        registrar.playToServer(OpenBackpackPayload.TYPE, OpenBackpackPayload.STREAM_CODEC,
                CustomGearNetworking::handleOpenBackpack);
    }

    public static void registerConfigurationTasks(final RegisterConfigurationTasksEvent event) {
        // OFF mode: skip the whole handshake — don't even register the task.
        if (CustomGearConfig.HANDSHAKE_MODE.get() == HandshakeMode.OFF) {
            LOGGER.debug("[CustomGear] Content handshake disabled by config (OFF)");
            return;
        }
        event.register(new HashCheckTask());
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** CLIENT side: compare the server's hash against what WE loaded at startup. */
    private static void handleHashCheckOnClient(HashCheckPayload payload, IPayloadContext context) {
        String localHash = ContentHasher.current();

        if (!payload.serverHash().equals(localHash)) {
            if (payload.enforce()) {
                LOGGER.warn("[CustomGear] Content hash mismatch — server: {}, local: {}. Disconnecting.",
                        payload.serverHash(), localHash);
                context.disconnect(Component.translatable(
                        "customgear.network.hash_mismatch",
                        payload.serverHash(), localHash));
                return;
            }
            // WARN mode: proceed, but remember to warn the player in chat
            // once they're actually in the world.
            LOGGER.warn("[CustomGear] Content hash mismatch (server allows it — WARN mode). "
                            + "Server: {}, local: {}. Tooltips/names may not match server values.",
                    payload.serverHash(), localHash);
            pendingMismatchWarning = true;
        } else {
            LOGGER.info("[CustomGear] Content hash verified with server ({})", localHash);
        }

        context.reply(new HashCheckAckPayload(localHash));
    }

    /** SERVER side: verify the client's hash according to the configured mode. */
    private static void handleAckOnServer(HashCheckAckPayload payload, IPayloadContext context) {
        String serverHash = ContentHasher.current();

        if (!payload.clientHash().equals(serverHash)) {
            if (CustomGearConfig.HANDSHAKE_MODE.get() == HandshakeMode.ENFORCE) {
                LOGGER.warn("[CustomGear] Client sent mismatching content hash '{}' (server: '{}') — disconnecting",
                        payload.clientHash(), serverHash);
                context.disconnect(Component.translatable(
                        "customgear.network.hash_mismatch",
                        serverHash, payload.clientHash()));
                return;
            }
            // WARN mode: let them in, leave a trace for the server owner.
            LOGGER.warn("[CustomGear] Client joined with mismatching content (WARN mode). "
                    + "Server: {}, client: {}", serverHash, payload.clientHash());
        }

        context.finishCurrentTask(HashCheckTask.TYPE);
    }

    /**
     * The menu the player has open is the only authority on what they can touch.
     * A client can ask to transfer, but never says what — so the worst a forged
     * packet achieves is a transfer the player could have done by hand.
     */
    public static void handleTransfer(TransferItemsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CustomContainerMenu menu) {
                menu.transfer(payload.toStorage(), payload.everything());
            }
        });
    }

    public static void handleSort(SortContainerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CustomContainerMenu menu) {
                menu.sort(payload.order());
            }
        });
    }

    public static void handleSortMode(SortModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof CustomContainerMenu menu) {
                menu.setSortMode(payload.criterion(), payload.descending());
            }
        });
    }

    /**
     * Opens the backpack the player is carrying, in this order: the selected
     * hotbar slot, then the offhand, then the first one found walking the
     * inventory — which puts the hotbar ahead of the rest, since it is numbered
     * first.
     * <p>
     * The two hands come first because "whatever I am holding" is what a player
     * means by the key, and it is the only way to choose between two backpacks
     * without inventing a rule.
     */
    public static void handleOpenBackpack(OpenBackpackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            int selected = player.getInventory().selected;
            if (tryOpen(player, new BackpackAnchor.InventorySlot(selected))) return;
            if (tryOpen(player, new BackpackAnchor.Offhand())) return;

            // Equipped beats loose: someone wearing a storage ring is wearing it
            // to have it at hand, and a backpack lying in the inventory covering
            // it would be the opposite of what they asked for. This is where we
            // part from Sophisticated, which checks Curios last.
            for (BackpackAnchor anchor : CuriosCompat.equippedBackpacks(player)) {
                if (tryOpen(player, anchor)) return;
            }

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (tryOpen(player, new BackpackAnchor.InventorySlot(i))) return;
            }
        });
    }

    /** Opens the stack this anchor points at, if it is a backpack at all. */
    private static boolean tryOpen(Player player, BackpackAnchor anchor) {
        ItemStack stack = anchor.resolve(player);
        if (!(stack.getItem() instanceof CustomBackpackItem backpack)) return false;
        backpack.open(player, anchor, stack);
        return true;
    }

    // ── Configuration task ────────────────────────────────────────────────────

    /**
     * Runs on the server for every connecting client during the configuration
     * phase (only registered in ENFORCE/WARN modes). Sends the server's
     * captured content hash plus the enforcement flag; the task stays pending
     * (holding the login) until {@link #handleAckOnServer} completes it or a
     * side disconnects.
     */
    public static class HashCheckTask implements ICustomConfigurationTask {

        public static final ConfigurationTask.Type TYPE =
                new ConfigurationTask.Type("ultimatecustomgear:hash_check");

        @Override
        public void run(@NotNull Consumer<CustomPacketPayload> sender) {
            String serverHash = ContentHasher.current();
            boolean enforce = CustomGearConfig.HANDSHAKE_MODE.get() == HandshakeMode.ENFORCE;
            LOGGER.debug("[CustomGear] Sending content hash to connecting client: {} (enforce={})",
                    serverHash, enforce);
            sender.accept(new HashCheckPayload(serverHash, enforce));
        }

        @Override
        public @NotNull ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}
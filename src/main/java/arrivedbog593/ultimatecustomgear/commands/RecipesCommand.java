package arrivedbog593.ultimatecustomgear.commands;

import arrivedbog593.ultimatecustomgear.util.RecipeQueries;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * /customgear recipes [item] — lists every recipe that produces an item,
 * grouped by type, with the id and type a recipe_patch needs. Both are
 * click-to-copy. Without an item, it uses the one in the player's hand.
 * <p>
 * Reads the server's live recipes, patches already applied, so it also answers
 * "did my patch work?". Open to every player: it only reads, and JEI shows the
 * same recipes anyway.
 */
public final class RecipesCommand {

    private RecipesCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return Commands.literal("recipes")
                .executes(ctx -> listHeld(ctx.getSource()))
                .then(Commands.argument("item", ItemArgument.item(context))
                        .executes(ctx -> list(ctx.getSource(), ItemArgument.getItem(ctx, "item").item().value())));
    }

    /** No argument: the item in the main hand, else the off hand. */
    private static int listHeld(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (held.isEmpty() && player != null) held = player.getOffhandItem();

        if (held.isEmpty()) {
            // Also the console and command blocks: they have no hands.
            source.sendFailure(Component.translatable("customgear.command.recipes.no_item"));
            return 0;
        }
        return list(source, held.getItem());
    }

    private static int list(CommandSourceStack source, Item item) {
        MinecraftServer server = source.getServer();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        RecipeQueries.Matches found = RecipeQueries.find(
                server.getRecipeManager(), server.registryAccess(), item);

        if (found.main().isEmpty() && found.secondary().isEmpty()) {
            source.sendFailure(Component.translatable("customgear.command.recipes.none", itemId));
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                "customgear.command.recipes.header", itemId, found.main().size()), false);
        if (found.main().isEmpty()) {
            source.sendSuccess(() -> Component.literal("  —").withStyle(ChatFormatting.GRAY), false);
        }
        printGrouped(source, found.main());

        if (!found.secondary().isEmpty()) {
            // Removing one of these removes the whole recipe, main output included.
            source.sendSuccess(() -> Component.translatable(
                    "customgear.command.recipes.secondary", found.secondary().size())
                    .withStyle(ChatFormatting.YELLOW), false);
            printGrouped(source, found.secondary());
        }
        return found.main().size() + found.secondary().size();
    }

    /** One gold line per recipe type, then its recipes. */
    private static void printGrouped(CommandSourceStack source, List<RecipeHolder<?>> recipes) {
        Identifier currentType = null;
        for (RecipeHolder<?> holder : recipes) {
            Identifier type = RecipeQueries.typeId(holder);
            if (!Objects.equals(type, currentType)) {
                currentType = type;
                Component typeLine = Component.literal("  ").append(copyable(type.toString(), ChatFormatting.GOLD));
                source.sendSuccess(() -> typeLine, false);
            }
            Component line = Component.literal("    ")
                    .append(copyable(RecipeQueries.id(holder).toString(), ChatFormatting.WHITE))
                    .append(Component.literal("  ← " + inputs(holder)).withStyle(ChatFormatting.GRAY));
            source.sendSuccess(() -> line, false);
        }
    }

    private static Component copyable(String text, ChatFormatting color) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withClickEvent(new ClickEvent.CopyToClipboard(text))
                .withHoverEvent(new HoverEvent.ShowText(
                        Component.translatable("customgear.command.recipes.copy"))));
    }

    /**
     * What goes in, briefly: "9× gold_nugget", "raw_gold", "oak_planks (+10)"
     * for an ingredient that accepts several items. A recipe that doesn't list
     * its ingredients the vanilla way (some machines) shows "—".
     */
    private static String inputs(RecipeHolder<?> holder) {
        List<Ingredient> ingredients;
        try {
            ingredients = holder.value().placementInfo().ingredients();
        } catch (Exception e) {
            // A mod's recipe that can't say what it takes outside a crafting grid.
            return "—";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Ingredient ingredient : ingredients) {
            List<Holder<Item>> options = ingredient.items().toList();
            if (options.isEmpty()) continue;
            String name = shortId(options.getFirst().value())
                    + (options.size() > 1 ? " (+" + (options.size() - 1) + ")" : "");
            counts.merge(name, 1, Integer::sum);
        }
        if (counts.isEmpty()) return "—";

        StringJoiner out = new StringJoiner(", ");
        counts.forEach((name, n) -> out.add(n > 1 ? n + "× " + name : name));
        return out.toString();
    }

    /** "gold_nugget" for vanilla items, the full id for everything else. */
    private static String shortId(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
    }
}

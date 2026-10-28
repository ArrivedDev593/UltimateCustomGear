package arrivedbog593.ultimatecustomgear.util;

import arrivedbog593.ultimatecustomgear.data.GearData;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * Applies and removes gear-driven effects (held effects, set bonuses,
 * piece effects) using SHORT REFRESHED DURATIONS instead of infinite ones.
 * <p>
 * Why not infinite (-1) duration + removeEffect:
 * <ul>
 *   <li>removeEffect deletes the whole effect — if the player drank a real
 *       Strength potion while holding a strength-granting sword, swapping
 *       items wiped their potion too.</li>
 *   <li>If tracking was lost (logout while holding the item, death, crash),
 *       the infinite effect stayed in the player's NBT forever — a ghost
 *       effect with no owner.</li>
 * </ul>
 * How the refreshed-duration scheme works (beacon-style):
 * <ul>
 *   <li>Effects are applied with {@link #EFFECT_DURATION_TICKS} (240 = 12s)
 *       and re-applied every second by the tick handlers. Remaining duration
 *       therefore stays in [220, 240] — always above the 200-tick threshold
 *       where the HUD starts blinking icons, so the icon looks steady.</li>
 *   <li>Vanilla addEffect only UPGRADES: it never replaces an active effect
 *       with a higher amplifier, or same amplifier and longer duration. A
 *       real potion (minutes long) therefore always wins over our refresh,
 *       and our refresh takes over seamlessly when the potion expires.
 *       Potion protection is automatic — no special-casing needed.</li>
 *   <li>If tracking is ever lost, the effect self-expires in at most 12
 *       seconds. Ghost effects are structurally impossible.</li>
 *   <li>After unequipping, an effect may linger up to 12s if not explicitly
 *       removed — same behavior as vanilla beacons. Handlers that track
 *       equipment changes call {@link #removeEffects} for snappier removal.</li>
 * </ul>
 */
public class EffectUtils {

    private static final Logger LOGGER = LogManager.getLogger("CustomGear");

    /**
     * Duration applied on each refresh. Must stay above 200 (HUD blink
     * threshold) plus the 20-tick refresh interval; 240 gives headroom.
     * Also the upper bound {@link #removeEffects} uses to distinguish
     * our effects from real potions.
     */
    public static final int EFFECT_DURATION_TICKS = 240;

    /**
     * Applies a set of effects to a player with a short, renewable duration.
     * Call every ~20 ticks while the condition (item held, set worn) still
     * holds; vanilla extends the duration on every call.
     */
    public static void applyEffects(Player player, List<GearData.EffectData> effects) {
        if (effects == null) return;

        for (GearData.EffectData effectData : effects) {
            Holder<MobEffect> effectHolder = resolve(effectData.effect);
            if (effectHolder == null) continue;

            // addEffect only upgrades (higher amplifier, or same amplifier and
            // longer duration), so this refresh can never downgrade or shorten
            // a real potion the player has active.
            player.addEffect(new MobEffectInstance(
                    effectHolder,
                    EFFECT_DURATION_TICKS,
                    effectData.amplifier,
                    true,  // ambient
                    false  // no particles
            ));
        }
    }

    /**
     * Removes a set of effects from a player — but ONLY if the active effect
     * looks like ours: the same amplifier and a duration within our renewable
     * range. An effect with a longer duration or a different amplifier is a
     * legitimate potion of the player's and is left alone.
     * <p>
     * Known and accepted edge case: a real potion in its last 12 seconds with
     * the same amplifier can't be told apart from our effect and gets
     * removed — at most, the last 12s of a potion.
     */
    public static void removeEffects(Player player, List<GearData.EffectData> effects) {
        if (effects == null) return;

        for (GearData.EffectData effectData : effects) {
            Holder<MobEffect> effectHolder = resolve(effectData.effect);
            if (effectHolder == null) continue;

            MobEffectInstance existing = player.getEffect(effectHolder);
            if (existing == null) continue;

            boolean looksLikeOurs =
                    existing.getAmplifier() == effectData.amplifier
                            && existing.getDuration() <= EFFECT_DURATION_TICKS
                            && existing.getDuration() != -1;

            if (looksLikeOurs) {
                player.removeEffect(effectHolder);
            }
            // Otherwise: a real potion (longer/stronger) — leave it alone.
            // If OUR effect was suppressed under it, it simply won't be
            // refreshed anymore and vanishes with the potion's own expiry.
        }
    }

    private static Holder<MobEffect> resolve(String effectId) {
        Identifier rl;
        try {
            rl = Identifier.parse(effectId);
        } catch (Exception e) {
            LOGGER.error("[CustomGear] Malformed effect ID skipped: {}", effectId);
            return null;
        }
        Holder<MobEffect> holder = BuiltInRegistries.MOB_EFFECT
                .get(rl)
                .orElse(null);
        if (holder == null) {
            LOGGER.error("[CustomGear] Effect not found: {}", effectId);
        }
        return holder;
    }
}
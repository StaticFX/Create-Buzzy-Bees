package de.devin.cbbees.compat;

import net.minecraft.world.entity.player.Player;

/** Compatibility access for clearing flight state saved by pre-1.3 CBBees versions. */
public final class PlayerFlightCompat {
    private PlayerFlightCompat() {}

    /**
     * This deliberately accesses the deprecated raw field. The supported mayFly() API also
     * includes NeoForge's creative-flight attribute, which must not be cleared by this migration.
     */
    @SuppressWarnings("deprecation")
    public static void clearLegacyFlight(Player player) {
        if (!player.getAbilities().mayfly) {
            return;
        }

        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }
}

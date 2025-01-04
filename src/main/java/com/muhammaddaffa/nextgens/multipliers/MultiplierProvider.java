package com.muhammaddaffa.nextgens.multipliers;

import com.muhammaddaffa.nextgens.sellwand.models.SellwandData;
import com.muhammaddaffa.nextgens.users.models.User;
import org.bukkit.entity.Player;

public interface MultiplierProvider {

    /**
     * Returns the additive multiplier value for a sale.
     * Should return the multiplier amount to be added to the base multiplier.
     * For example, return 2.0 for a 2x multiplier.
     */
    double getMultiplier(Player player, User user, SellwandData sellwand);

}

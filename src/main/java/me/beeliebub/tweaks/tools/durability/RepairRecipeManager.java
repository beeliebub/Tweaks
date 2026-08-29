package me.beeliebub.tweaks.tools.durability;

import me.beeliebub.tweaks.Tweaks;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.Recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Main-thread recipe owner for the live-editable repair kit recipe. */
public final class RepairRecipeManager {

    private final Tweaks plugin;
    private final RepairKitItem kitItem;
    private final NamespacedKey recipeKey;
    private Recipe registered;

    public RepairRecipeManager(Tweaks plugin, RepairKitItem kitItem) {
        this.plugin = plugin;
        this.kitItem = kitItem;
        this.recipeKey = new NamespacedKey(plugin, "repair_kit");
    }

    public boolean registerConfigured() {
        if (!plugin.getConfig().getBoolean("tools.repair-kit.enabled", true)) {
            remove();
            return true;
        }
        List<String> cells = configuredCells();
        return applyConfiguredGrid(null, cells);
    }

    public boolean refresh() {
        return registerConfigured();
    }

    public boolean applyConfiguredGrid(me.beeliebub.tweaks.core.config.ConfigSetting setting, List<String> cells) {
        if (cells == null || cells.size() != 9) return false;
        if (!plugin.getConfig().getBoolean("tools.repair-kit.enabled", true)) {
            remove();
            return true;
        }
        List<Material> materials = new ArrayList<>(9);
        boolean hasIngredient = false;
        for (String raw : cells) {
            Material material = "air".equalsIgnoreCase(String.valueOf(raw))
                    ? Material.AIR : Material.matchMaterial(raw);
            if (material == null || (!material.isAir() && !material.isItem())) return false;
            materials.add(material);
            if (!material.isAir()) hasIngredient = true;
        }
        if (!hasIngredient) return false;
        Recipe candidate = buildRecipe(materials);
        if (candidate == null) return false;
        if (collidesWithExistingRecipe(candidate)) {
            plugin.getLogger().warning("Repair kit recipe uses the same ingredients as an existing recipe; update rejected.");
            return false;
        }

        Recipe previous = registered;
        if (previous != null) Bukkit.removeRecipe(recipeKey);
        boolean added;
        try {
            added = Bukkit.addRecipe(candidate);
        } catch (RuntimeException ex) {
            added = false;
            plugin.getLogger().warning("Repair kit recipe registration failed: " + ex.getMessage());
        }
        if (!added) {
            if (previous != null) Bukkit.addRecipe(previous);
            return false;
        }
        registered = candidate;
        return true;
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    public void remove() {
        Bukkit.removeRecipe(recipeKey);
        registered = null;
    }

    private Recipe buildRecipe(List<Material> materials) {
        ItemStack result = kitItem.create(1);
        boolean shapeless = plugin.getConfig().getBoolean("tools.repair-kit.recipe-shapeless", false);
        if (shapeless) {
            ShapelessRecipe recipe = new ShapelessRecipe(recipeKey, result);
            for (Material material : materials) if (!material.isAir()) recipe.addIngredient(material);
            return recipe;
        }
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, result);
        StringBuilder first = new StringBuilder(3);
        StringBuilder second = new StringBuilder(3);
        StringBuilder third = new StringBuilder(3);
        StringBuilder[] rows = {first, second, third};
        char symbol = 'A';
        for (int i = 0; i < materials.size(); i++) {
            Material material = materials.get(i);
            rows[i / 3].append(material.isAir() ? ' ' : symbol);
            symbol++;
        }
        recipe.shape(first.toString(), second.toString(), third.toString());
        symbol = 'A';
        for (Material material : materials) {
            if (!material.isAir()) recipe.setIngredient(symbol, material);
            symbol++;
        }
        return recipe;
    }

    private List<String> configuredCells() {
        List<String> configured = plugin.getConfig().getStringList("tools.repair-kit.recipe");
        List<String> cells = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) cells.add(i < configured.size() ? configured.get(i) : "air");
        return cells;
    }

    /**
     * Bukkit permits multiple recipes with the same ingredients, but their result selection is
     * then implementation-order dependent. Reject an input signature already owned by another
     * recipe so a live edit cannot shadow a vanilla or third-party craft.
     */
    private boolean collidesWithExistingRecipe(Recipe candidate) {
        String candidateSignature = ingredientSignature(candidate);
        if (candidateSignature == null) return true;
        Iterator<Recipe> recipes = Bukkit.recipeIterator();
        while (recipes.hasNext()) {
            Recipe existing;
            try {
                existing = recipes.next();
            } catch (RuntimeException ex) {
                continue;
            }
            if (existing == candidate) continue;
            if (existing instanceof Keyed keyed && recipeKey.equals(keyed.getKey())) continue;
            String existingSignature;
            try {
                existingSignature = ingredientSignature(existing);
            } catch (RuntimeException ex) {
                // A malformed datapack or third-party recipe must not abort our own registration.
                continue;
            }
            if (candidateSignature.equals(existingSignature)) return true;
        }
        return false;
    }

    private String ingredientSignature(Recipe recipe) {
        List<String> ingredients = new ArrayList<>();
        if (recipe instanceof ShapedRecipe shaped) {
            var choices = shaped.getIngredientMap();
            for (String row : shaped.getShape()) {
                for (int i = 0; i < row.length(); i++) {
                    char symbol = row.charAt(i);
                    if (symbol == ' ') continue;
                    ItemStack ingredient = choices.get(symbol);
                    if (ingredient == null || ingredient.isEmpty()) return null;
                    ingredients.add(ingredient.getType().name());
                }
            }
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            for (ItemStack ingredient : shapeless.getIngredientList()) {
                if (ingredient == null || ingredient.isEmpty()) return null;
                ingredients.add(ingredient.getType().name());
            }
        } else {
            return null;
        }
        Collections.sort(ingredients);
        return String.join(",", ingredients);
    }
}

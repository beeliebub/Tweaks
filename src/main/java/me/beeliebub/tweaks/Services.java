package me.beeliebub.tweaks;

import me.beeliebub.tweaks.economy.EconomyManager;
import me.beeliebub.tweaks.economy.HouseAccount;
import me.beeliebub.tweaks.economy.HousePaymentService;
import me.beeliebub.tweaks.enchantments.Replant;
import me.beeliebub.tweaks.enchantments.Telekinesis;
import me.beeliebub.tweaks.enchantments.quality.QualityRegistry;
import me.beeliebub.tweaks.itemadmin.ItemFilterCommand;
import me.beeliebub.tweaks.minigames.blackjack.BlackjackListener;
import me.beeliebub.tweaks.minigames.roulette.RouletteListener;
import me.beeliebub.tweaks.minigames.resource.ResourceHunt;
import me.beeliebub.tweaks.minigames.resource.ResourceHuntItems;
import me.beeliebub.tweaks.permissions.PermissionManager;
import me.beeliebub.tweaks.playeradmin.PlayerAdminManager;
import me.beeliebub.tweaks.profiles.StorageManager;
import me.beeliebub.tweaks.profiles.WorldProfileTable;
import me.beeliebub.tweaks.protection.region.PendingStampsStore;
import me.beeliebub.tweaks.protection.region.ProtectionManager;
import me.beeliebub.tweaks.protection.ui.RegionSelectionManager;
import me.beeliebub.tweaks.ranks.RankManager;
import me.beeliebub.tweaks.worldmanagement.MoonSystem;
import org.bukkit.Material;

/**
 * Bootstrap-time store for the manager/service instances each per-package
 * {@code XxxBootstrap.register(Tweaks, Services)} call constructs and that a later
 * tier, one of {@link Tweaks}'s public getters, or {@code onDisable()} needs to read
 * back. This is deliberately not a dependency-injection container: every field is a
 * named, typed, hand-populated slot - no reflection, no {@code get(Class<T>)} lookup,
 * no autowiring. A field belongs here only if it is read by a strictly later bootstrap
 * tier, by a public {@code Tweaks} getter, or by {@code onDisable()}; everything else
 * stays a local variable inside the registrar that constructs it.
 *
 * <p>Package-private fields give {@link Tweaks} itself null-tolerant direct access,
 * matching today's partial-boot-safe getters/onDisable null checks. Every other
 * package must go through the public accessors: {@code setXxx} is write-once (throws
 * on a second call, so a bootstrap-ordering bug surfaces at boot instead of silently
 * overwriting a published dependency), and {@code xxx()} throws
 * {@link IllegalStateException} if read before it is published - turning a would-be
 * mid-game NPE into a boot-time failure at the offending registrar's call site.
 *
 * <p>{@code protectionSelectionTool} is the one exception to the write-once rule: it
 * has a meaningful non-null default ({@code Material.STONE_AXE}) that a bad config
 * value falls back to, so it uses a plain overwriting setter instead.
 *
 * <p>{@link Tweaks} is never stored here - it stays an explicit first parameter of
 * every {@code register} call. Storing the plugin handle in this holder would turn it
 * into the service locator this design exists to avoid.
 */
public final class Services {

    StorageManager storageManager;
    WorldProfileTable worldProfileTable;
    EconomyManager economyManager;
    HouseAccount houseAccount;
    HousePaymentService housePaymentService;
    RankManager rankManager;
    PermissionManager permissionManager;
    ProtectionManager protectionManager;
    PendingStampsStore pendingStampsStore;
    RegionSelectionManager regionSelectionManager;
    Material protectionSelectionTool = Material.STONE_AXE;
    ResourceHuntItems resourceHuntItems;
    ResourceHunt resourceHunt;
    MoonSystem moonSystem;
    PlayerAdminManager playerAdminManager;
    QualityRegistry qualityRegistry;
    ItemFilterCommand itemFilterCommand;
    Telekinesis telekinesis;
    Replant replant;
    BlackjackListener blackjackListener;
    RouletteListener rouletteListener;

    public void setStorageManager(StorageManager storageManager) {
        if (this.storageManager != null) throw new IllegalStateException("storageManager already published");
        this.storageManager = storageManager;
    }

    public StorageManager storageManager() {
        if (storageManager == null) throw new IllegalStateException("storageManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return storageManager;
    }

    public void setWorldProfileTable(WorldProfileTable worldProfileTable) {
        if (this.worldProfileTable != null) throw new IllegalStateException("worldProfileTable already published");
        this.worldProfileTable = worldProfileTable;
    }

    public WorldProfileTable worldProfileTable() {
        if (worldProfileTable == null) throw new IllegalStateException("worldProfileTable not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return worldProfileTable;
    }

    public void setEconomyManager(EconomyManager economyManager) {
        if (this.economyManager != null) throw new IllegalStateException("economyManager already published");
        this.economyManager = economyManager;
    }

    public EconomyManager economyManager() {
        if (economyManager == null) throw new IllegalStateException("economyManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return economyManager;
    }

    public void setHouseAccount(HouseAccount houseAccount) {
        if (this.houseAccount != null) throw new IllegalStateException("houseAccount already published");
        this.houseAccount = houseAccount;
    }

    public HouseAccount houseAccount() {
        if (houseAccount == null) throw new IllegalStateException("houseAccount not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return houseAccount;
    }

    public void setHousePaymentService(HousePaymentService housePaymentService) {
        if (this.housePaymentService != null) throw new IllegalStateException("housePaymentService already published");
        this.housePaymentService = housePaymentService;
    }

    public HousePaymentService housePaymentService() {
        if (housePaymentService == null) throw new IllegalStateException("housePaymentService not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return housePaymentService;
    }

    public void setRankManager(RankManager rankManager) {
        if (this.rankManager != null) throw new IllegalStateException("rankManager already published");
        this.rankManager = rankManager;
    }

    public RankManager rankManager() {
        if (rankManager == null) throw new IllegalStateException("rankManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return rankManager;
    }

    public void setPermissionManager(PermissionManager permissionManager) {
        if (this.permissionManager != null) throw new IllegalStateException("permissionManager already published");
        this.permissionManager = permissionManager;
    }

    public PermissionManager permissionManager() {
        if (permissionManager == null) throw new IllegalStateException("permissionManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return permissionManager;
    }

    public void setProtectionManager(ProtectionManager protectionManager) {
        if (this.protectionManager != null) throw new IllegalStateException("protectionManager already published");
        this.protectionManager = protectionManager;
    }

    public ProtectionManager protectionManager() {
        if (protectionManager == null) throw new IllegalStateException("protectionManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return protectionManager;
    }

    public void setPendingStampsStore(PendingStampsStore pendingStampsStore) {
        if (this.pendingStampsStore != null) throw new IllegalStateException("pendingStampsStore already published");
        this.pendingStampsStore = pendingStampsStore;
    }

    public PendingStampsStore pendingStampsStore() {
        if (pendingStampsStore == null) throw new IllegalStateException("pendingStampsStore not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return pendingStampsStore;
    }

    public void setRegionSelectionManager(RegionSelectionManager regionSelectionManager) {
        if (this.regionSelectionManager != null) throw new IllegalStateException("regionSelectionManager already published");
        this.regionSelectionManager = regionSelectionManager;
    }

    public RegionSelectionManager regionSelectionManager() {
        if (regionSelectionManager == null) throw new IllegalStateException("regionSelectionManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return regionSelectionManager;
    }

    /** Plain overwriting setter - see the class Javadoc for why this field skips the write-once rule. */
    public void setProtectionSelectionTool(Material protectionSelectionTool) {
        this.protectionSelectionTool = protectionSelectionTool;
    }

    public Material protectionSelectionTool() {
        return protectionSelectionTool;
    }

    public void setResourceHuntItems(ResourceHuntItems resourceHuntItems) {
        if (this.resourceHuntItems != null) throw new IllegalStateException("resourceHuntItems already published");
        this.resourceHuntItems = resourceHuntItems;
    }

    public ResourceHuntItems resourceHuntItems() {
        if (resourceHuntItems == null) throw new IllegalStateException("resourceHuntItems not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return resourceHuntItems;
    }

    public void setResourceHunt(ResourceHunt resourceHunt) {
        if (this.resourceHunt != null) throw new IllegalStateException("resourceHunt already published");
        this.resourceHunt = resourceHunt;
    }

    public ResourceHunt resourceHunt() {
        if (resourceHunt == null) throw new IllegalStateException("resourceHunt not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return resourceHunt;
    }

    public void setMoonSystem(MoonSystem moonSystem) {
        if (this.moonSystem != null) throw new IllegalStateException("moonSystem already published");
        this.moonSystem = moonSystem;
    }

    public MoonSystem moonSystem() {
        if (moonSystem == null) throw new IllegalStateException("moonSystem not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return moonSystem;
    }

    public void setPlayerAdminManager(PlayerAdminManager playerAdminManager) {
        if (this.playerAdminManager != null) throw new IllegalStateException("playerAdminManager already published");
        this.playerAdminManager = playerAdminManager;
    }

    public PlayerAdminManager playerAdminManager() {
        if (playerAdminManager == null) throw new IllegalStateException("playerAdminManager not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return playerAdminManager;
    }

    public void setQualityRegistry(QualityRegistry qualityRegistry) {
        if (this.qualityRegistry != null) throw new IllegalStateException("qualityRegistry already published");
        this.qualityRegistry = qualityRegistry;
    }

    public QualityRegistry qualityRegistry() {
        if (qualityRegistry == null) throw new IllegalStateException("qualityRegistry not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return qualityRegistry;
    }

    public void setItemFilterCommand(ItemFilterCommand itemFilterCommand) {
        if (this.itemFilterCommand != null) throw new IllegalStateException("itemFilterCommand already published");
        this.itemFilterCommand = itemFilterCommand;
    }

    public ItemFilterCommand itemFilterCommand() {
        if (itemFilterCommand == null) throw new IllegalStateException("itemFilterCommand not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return itemFilterCommand;
    }

    public void setTelekinesis(Telekinesis telekinesis) {
        if (this.telekinesis != null) throw new IllegalStateException("telekinesis already published");
        this.telekinesis = telekinesis;
    }

    public Telekinesis telekinesis() {
        if (telekinesis == null) throw new IllegalStateException("telekinesis not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return telekinesis;
    }

    public void setReplant(Replant replant) {
        if (this.replant != null) throw new IllegalStateException("replant already published");
        this.replant = replant;
    }

    public Replant replant() {
        if (replant == null) throw new IllegalStateException("replant not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return replant;
    }

    public void setBlackjackListener(BlackjackListener blackjackListener) {
        if (this.blackjackListener != null) throw new IllegalStateException("blackjackListener already published");
        this.blackjackListener = blackjackListener;
    }

    public BlackjackListener blackjackListener() {
        if (blackjackListener == null) throw new IllegalStateException("blackjackListener not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return blackjackListener;
    }

    public void setRouletteListener(RouletteListener rouletteListener) {
        if (this.rouletteListener != null) throw new IllegalStateException("rouletteListener already published");
        this.rouletteListener = rouletteListener;
    }

    public RouletteListener rouletteListener() {
        if (rouletteListener == null) throw new IllegalStateException("rouletteListener not yet published - check bootstrap tier ordering in Tweaks.onEnable()");
        return rouletteListener;
    }
}

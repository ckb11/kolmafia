package internal.helpers;

import internal.helpers.Cleanups.OrderedRunnable;
import internal.network.FakeHttpClientBuilder;
import internal.network.FakeHttpResponse;
import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.AscensionClass;
import net.sourceforge.kolmafia.AscensionPath.Path;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.FamiliarData;
import net.sourceforge.kolmafia.KoLAdventure;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.KoLCharacter.Gender;
import net.sourceforge.kolmafia.KoLConstants;
import net.sourceforge.kolmafia.KoLConstants.MafiaState;
import net.sourceforge.kolmafia.ModifierType;
import net.sourceforge.kolmafia.Modifiers;
import net.sourceforge.kolmafia.MonsterData;
import net.sourceforge.kolmafia.PastaThrallData;
import net.sourceforge.kolmafia.RestrictedItemType;
import net.sourceforge.kolmafia.StaticEntity;
import net.sourceforge.kolmafia.VYKEACompanionData;
import net.sourceforge.kolmafia.VYKEACompanionData.VYKEACompanionType;
import net.sourceforge.kolmafia.ZodiacSign;
import net.sourceforge.kolmafia.chat.ChatManager;
import net.sourceforge.kolmafia.chat.EnableMessage;
import net.sourceforge.kolmafia.combat.MonsterStatusTracker;
import net.sourceforge.kolmafia.equipment.Slot;
import net.sourceforge.kolmafia.objectpool.EffectPool;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.persistence.*;
import net.sourceforge.kolmafia.preferences.Preferences;
import net.sourceforge.kolmafia.request.BasementRequest;
import net.sourceforge.kolmafia.request.CampgroundRequest;
import net.sourceforge.kolmafia.request.CharPaneRequest;
import net.sourceforge.kolmafia.request.ChateauRequest;
import net.sourceforge.kolmafia.request.ClanLoungeRequest;
import net.sourceforge.kolmafia.request.CoinMasterPurchaseRequest;
import net.sourceforge.kolmafia.request.EquipmentRequest;
import net.sourceforge.kolmafia.request.FightRequest;
import net.sourceforge.kolmafia.request.FloristRequest;
import net.sourceforge.kolmafia.request.GenericRequest;
import net.sourceforge.kolmafia.request.GenericRequest.TopMenuStyle;
import net.sourceforge.kolmafia.request.MallPurchaseRequest;
import net.sourceforge.kolmafia.request.PurchaseRequest;
import net.sourceforge.kolmafia.request.StandardRequest;
import net.sourceforge.kolmafia.request.coinmaster.HermitRequest;
import net.sourceforge.kolmafia.session.ChoiceControl;
import net.sourceforge.kolmafia.session.ChoiceManager;
import net.sourceforge.kolmafia.session.ClanManager;
import net.sourceforge.kolmafia.session.EquipmentManager;
import net.sourceforge.kolmafia.session.EquipmentRequirement;
import net.sourceforge.kolmafia.session.GoalManager;
import net.sourceforge.kolmafia.session.LimitMode;
import net.sourceforge.kolmafia.session.MallPriceManager;
import net.sourceforge.kolmafia.session.ResultProcessor;
import net.sourceforge.kolmafia.session.StoreManager;
import net.sourceforge.kolmafia.session.TurnCounter;
import net.sourceforge.kolmafia.shop.ShopRowDatabase;
import net.sourceforge.kolmafia.shop.ShopRowDatabase.ShopRowData;
import net.sourceforge.kolmafia.utilities.HttpUtilities;
import net.sourceforge.kolmafia.utilities.Statics;
import net.sourceforge.kolmafia.utilities.TestStatics;

public class Player {

  /**
   * Ensures that the character stats are sufficient to equip an item
   *
   * @param itemName The item of interest
   * @return Restores the stat to the old value
   */
  public static Cleanups withStatsRequiredForEquipment(final String itemName) {
    int itemId = ItemDatabase.getItemId(itemName, 1, false);
    return withStatsRequiredForEquipment(itemId);
  }

  /**
   * Ensures that the character stats are sufficient to equip an item
   *
   * @param item The item of interest
   * @return Restores the stat to the old value
   */
  public static Cleanups withStatsRequiredForEquipment(AdventureResult item) {
    return withStatsRequiredForEquipment(item.getItemId());
  }

  /**
   * Ensures that the character stats are sufficient to equip an item
   *
   * @param itemId The item of interest
   * @return Restores the stat to the old value
   */
  public static Cleanups withStatsRequiredForEquipment(final int itemId) {
    String requirement = EquipmentDatabase.getEquipRequirement(itemId);
    EquipmentRequirement req = new EquipmentRequirement(requirement);

    return req.isMuscle()
        ? withMuscleAtLeast(req.getAmount())
        : req.isMysticality()
            ? withMysticalityAtLeast(req.getAmount())
            : req.isMoxie() ? withMoxieAtLeast(req.getAmount()) : new Cleanups();
  }

  /**
   * Equip the given slot with the given item
   *
   * @param slot Slot to equip
   * @param itemName Item to equip to slot
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withEquipped(final Slot slot, final String itemName) {
    return withEquipped(slot, AdventureResult.tallyItem(itemName));
  }

  /**
   * Equip the given item in its default slot
   *
   * @param itemId Item to equip
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withEquipped(final int itemId) {
    return withAllEquipped(itemId);
  }

  /**
   * Find slots to equip all the given items
   *
   * @param itemIds Items to equip
   * @return Restores items previously equipped to slots
   */
  public static Cleanups withAllEquipped(final int... itemIds) {
    var cleanups = new Cleanups();
    cleanups.addCleanups(
        Arrays.stream(itemIds)
            .mapToObj(
                id -> withEquipped(EquipmentRequest.chooseEquipmentSlot(id), ItemPool.get(id, 1)))
            .collect(Collectors.toList()));
    return cleanups;
  }

  /**
   * Equip the given slot with the given item
   *
   * @param slot Slot to equip
   * @param itemId Item to equip to slot
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withEquipped(final Slot slot, final int itemId) {
    return withEquipped(slot, ItemPool.get(itemId));
  }

  /**
   * Unequips the given slot
   *
   * @param slot Slot to unequip
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withUnequipped(final Slot slot) {
    return withEquipped(slot, (String) null);
  }

  /**
   * Equip the given slot with the given item
   *
   * @param slot Slot to equip
   * @param item Item to equip to slot
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withEquipped(final Slot slot, final AdventureResult item) {
    var cleanups = new Cleanups();
    // Do this first so that Equipment lists and outfits will update appropriately
    cleanups.add(withStatsRequiredForEquipment(item));

    var old = EquipmentManager.getEquipment(slot);
    EquipmentManager.setEquipment(slot, item.getItemId() == -1 ? EquipmentRequest.UNEQUIP : item);
    EquipmentManager.updateNormalOutfits();
    KoLCharacter.recalculateAdjustments();
    // may have access to a new item = may have access to a new concoction
    ConcoctionDatabase.refreshConcoctions();
    cleanups.add(
        new Cleanups(
            () -> {
              EquipmentManager.setEquipment(slot, old);
              EquipmentManager.updateNormalOutfits();
              KoLCharacter.recalculateAdjustments();
              ConcoctionDatabase.refreshConcoctions();
            }));
    return cleanups;
  }

  /**
   * Equip the given outfit
   *
   * @param outfitId Outfit to equip
   * @return Restores previous equipment
   */
  public static Cleanups withOutfit(final int outfitId) {
    var cleanups = new Cleanups();
    cleanups.addCleanups(
        Arrays.stream(EquipmentDatabase.getOutfit(outfitId).getPieces())
            .map(piece -> withEquipped(piece.getItemId()))
            .collect(Collectors.toList()));
    return cleanups;
  }

  /**
   * Equip the given number of fake hands
   *
   * @param fakeHands Number of fake hands to equip
   * @return Restores previous equipment
   */
  public static Cleanups withFakeHands(final int fakeHands) {
    final int oldCount = EquipmentManager.getFakeHands();
    EquipmentManager.setFakeHands(fakeHands);
    return new Cleanups(() -> EquipmentManager.setFakeHands(oldCount));
  }

  /**
   * Equip the given slot with the given item
   *
   * @param item Item to equip to slot
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withHatTrickHat(final int item) {
    return withHatTrickHats(List.of(item));
  }

  /**
   * Equip the given slot with the given item
   *
   * @param items Items to equip to slot
   * @return Restores item previously equipped to slot
   */
  public static Cleanups withHatTrickHats(final List<Integer> items) {
    var cleanups = new Cleanups();
    // Do this first so that Equipment lists and outfits will update appropriately
    for (var item : items) {
      cleanups.add(withStatsRequiredForEquipment(item));
    }

    var old = EquipmentManager.getHatTrickHats();
    EquipmentManager.setHatTrickHats(new ArrayList<>(items));
    EquipmentManager.updateNormalOutfits();
    KoLCharacter.recalculateAdjustments();
    // may have access to a new item = may have access to a new concoction
    ConcoctionDatabase.refreshConcoctions();
    cleanups.add(
        new Cleanups(
            () -> {
              EquipmentManager.setHatTrickHats(old);
              EquipmentManager.updateNormalOutfits();
              KoLCharacter.recalculateAdjustments();
              ConcoctionDatabase.refreshConcoctions();
            }));
    return cleanups;
  }

  /**
   * Clears Inventory
   *
   * @return Clears inventory
   */
  public static Cleanups withNoItems() {
    KoLConstants.inventory.clear();
    return new Cleanups(KoLConstants.inventory::clear);
  }

  /**
   * Puts the given item into the player's inventory
   *
   * @param itemName Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItem(final String itemName) {
    return withItem(itemName, 1);
  }

  /**
   * Puts an amount of the given item into the player's inventory
   *
   * @param itemName Item to give
   * @param count Quantity of item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItem(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItem(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's inventory
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItem(final int itemId) {
    return withItem(itemId, 1);
  }

  /**
   * Puts an amount of the given item into the player's inventory
   *
   * @param itemId Item to give
   * @param count Quantity of item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItem(final int itemId, final int count) {
    return withItem(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's inventory
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItem(final AdventureResult item) {
    return addToList(item, KoLConstants.inventory);
  }

  /**
   * Puts the given items into the player's inventory
   *
   * @param itemIds Items to give
   * @return Restores the number of these items to the old values
   */
  public static Cleanups withItems(final int... itemIds) {
    var cleanups = new Cleanups();
    cleanups.addCleanups(
        Arrays.stream(itemIds).mapToObj(Player::withItem).collect(Collectors.toList()));
    return cleanups;
  }

  /**
   * Ensures that a given item is not in the player's inventory
   *
   * @param itemId Item to not have
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withoutItem(final int itemId) {
    return withItem(itemId, 0);
  }

  /**
   * Puts the given item into the player's closet
   *
   * @param itemName Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInCloset(final String itemName) {
    return withItemInCloset(itemName, 1);
  }

  /**
   * Puts an amount of the given item into the player's closet
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInCloset(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItemInCloset(itemId, count);
  }

  /**
   * Puts an amount of the given item into the player's closet
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInCloset(final int itemId) {
    return withItemInCloset(ItemPool.get(itemId, 1));
  }

  /**
   * Puts an amount of the given item into the player's closet
   *
   * @param itemId Item to give
   * @param count Quantity of item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInCloset(final int itemId, final int count) {
    return withItemInCloset(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's closet
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInCloset(final AdventureResult item) {
    return addToList(item, KoLConstants.closet);
  }

  /**
   * Puts the given item into the player's storage
   *
   * @param itemName Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStorage(final String itemName) {
    return withItemInStorage(itemName, 1);
  }

  /**
   * Puts an amount of the given item into the player's storage
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStorage(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItemInStorage(itemId, count);
  }

  /**
   * Puts the given item into the player's storage
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStorage(final int itemId) {
    return withItemInStorage(itemId, 1);
  }

  /**
   * Puts an amount of the given item into the player's storage
   *
   * @param itemId Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStorage(final int itemId, final int count) {
    return withItemInStorage(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's storage
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStorage(final AdventureResult item) {
    return addToList(item, KoLConstants.storage);
  }

  /**
   * Puts an amount of the given item into the player's display
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInDisplay(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItemInDisplay(itemId, count);
  }

  /**
   * Puts the given item into the player's display
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInDisplay(final int itemId) {
    return withItemInDisplay(itemId, 1);
  }

  /**
   * Puts an amount of the given item into the player's display
   *
   * @param itemId Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInDisplay(final int itemId, final int count) {
    return withItemInDisplay(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's display
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInDisplay(final AdventureResult item) {
    return addToList(item, KoLConstants.collection);
  }

  /**
   * Puts the given item into the player's freepulls
   *
   * @param itemName Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInFreepulls(final String itemName) {
    return withItemInFreepulls(itemName, 1);
  }

  /**
   * Puts an amount of the given item into the player's freepulls
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInFreepulls(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItemInFreepulls(itemId, count);
  }

  /**
   * Puts the given item into the player's freepulls
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInFreepulls(final int itemId) {
    return withItemInFreepulls(itemId, 1);
  }

  /**
   * Puts an amount of the given item into the player's freepulls
   *
   * @param itemId Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInFreepulls(final int itemId, final int count) {
    return withItemInFreepulls(ItemPool.get(itemId, count));
  }

  /**
   * Puts the given item into the player's freepulls
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInFreepulls(final AdventureResult item) {
    return addToList(item, KoLConstants.freepulls);
  }

  /**
   * Puts the given item into the player's clan stash
   *
   * @param itemName Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStash(final String itemName) {
    return withItemInStash(itemName, 1);
  }

  /**
   * Puts an amount of the given item into the player's shop
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInShop(
      final String itemName, final int count, long price, int limit) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withItemInShop(itemId, count, price, limit);
  }

  /**
   * Puts the given item into the player's shop
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInShop(final int itemId, long price, int limit) {
    return withItemInShop(itemId, 1, price, limit);
  }

  /**
   * Puts an amount of the given item into the player's shop
   *
   * @param itemId Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInShop(final int itemId, final int count, long price, int limit) {
    return withItemInShop(ItemPool.get(itemId, count), price, limit);
  }

  /**
   * Puts the given item into the player's shop
   *
   * @param item Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInShop(final AdventureResult item, long price, int limit) {
    int oldQuantity = StoreManager.shopAmount(item.getItemId());
    long oldPrice = StoreManager.getPrice(item.getItemId());
    int oldLimit = StoreManager.getLimit(item.getItemId());
    StoreManager.addItem(item.getItemId(), item.getCount(), price, limit);
    return new Cleanups(
        () -> {
          if (oldQuantity > 0) {
            StoreManager.updateItem(item.getItemId(), oldQuantity, oldPrice, oldLimit);
          } else {
            StoreManager.removeItem(item.getItemId(), StoreManager.shopAmount(item.getItemId()));
          }
        });
  }

  /**
   * Puts an amount of the given item into the player's clan stash
   *
   * @param itemName Item to give
   * @param count Quantity to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withItemInStash(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    AdventureResult item = ItemPool.get(itemId, count);
    return addToList(item, ClanManager.getStash());
  }

  private static Cleanups addToList(final AdventureResult item, final List<AdventureResult> list) {
    var old = item.getCount(list);
    AdventureResult.addResultToList(list, item);
    EquipmentManager.updateEquipmentLists();
    // may have access to a new item = may have access to a new concoction
    ConcoctionDatabase.refreshConcoctions();

    return new Cleanups(
        () -> {
          AdventureResult.removeResultFromList(list, item);
          if (old != 0) AdventureResult.addResultToList(list, item.getInstance(old));
          EquipmentManager.updateEquipmentLists();
          ConcoctionDatabase.refreshConcoctions();
        });
  }

  /**
   * Puts the given item into the player's clan lounge
   *
   * @param itemId Item to give
   * @return Removes the item from the lounge
   */
  public static Cleanups withClanLoungeItem(final int itemId) {
    ClanLoungeRequest.setClanLoungeItem(itemId, 1);
    return new Cleanups(() -> ClanLoungeRequest.setClanLoungeItem(itemId, 0));
  }

  /**
   * Resets clan info to initial state
   *
   * @return Resets to initial state
   */
  public static Cleanups withClan() {
    return withClan(0, "");
  }

  /**
   * Sets Clan ID and name as desired (presumably because saved HTML has a specific clan name).
   *
   * @param clanId clan ID
   * @param clanName clanName
   * @return Resets to initial state
   */
  public static Cleanups withClan(final int clanId, final String clanName) {
    ClanManager.setClan(clanId, clanName);
    return new Cleanups(() -> ClanManager.clearCache(true));
  }

  /**
   * Restores Clan Furniture after cleanup
   *
   * @return Resets to previous state
   */
  public static Cleanups withClanFurniture() {
    var old = ClanManager.getClanRumpus();

    return new Cleanups(
        () -> {
          var current = new ArrayList<>(ClanManager.getClanRumpus());
          for (var item : current) {
            if (!old.contains(item)) ClanManager.removeFromRumpus(item);
          }
        });
  }

  /**
   * Adds the given set of furniture to the player's Clan Rumpus Room
   *
   * @param furniture Furniture items to install
   * @return Resets to previous value
   */
  public static Cleanups withClanFurniture(final String... furniture) {
    var cleanups = new Cleanups();
    var rumpus = ClanManager.getClanRumpus();

    for (var f : furniture) {
      var old = rumpus.contains(f);
      ClanManager.addToRumpus(f);

      cleanups.add(
          () -> {
            if (!old) {
              ClanManager.removeFromRumpus(f);
            }
          });
    }

    return cleanups;
  }

  /**
   * Sets the player's meat to the given quantity
   *
   * @param meat Amount of meat to have
   * @return Restores the meat to the previous amount
   */
  public static Cleanups withMeat(final long meat) {
    var old = KoLCharacter.getAvailableMeat();
    KoLCharacter.setAvailableMeat(meat);
    ConcoctionDatabase.refreshConcoctions();
    return new Cleanups(
        () -> {
          KoLCharacter.setAvailableMeat(old);
          ConcoctionDatabase.refreshConcoctions();
        });
  }

  /**
   * Sets the player's closeted meat to the given quantity
   *
   * @param meat Amount of meat to have in closet
   * @return Restores the meat to the previous amount
   */
  public static Cleanups withMeatInCloset(final long meat) {
    var old = KoLCharacter.getClosetMeat();
    KoLCharacter.setClosetMeat(meat);
    return new Cleanups(() -> KoLCharacter.setClosetMeat(old));
  }

  /**
   * Puts item in player's inventory and ensures player meets requirements to equip
   *
   * @param itemName Name of the item
   * @return Removes item from player's inventory and resets stats
   */
  public static Cleanups withEquippableItem(final String itemName) {
    return withEquippableItem(itemName, 1);
  }

  /**
   * Puts number of items in player's inventory and ensures player meets requirements to equip
   *
   * @param itemName Name of the item
   * @param count Number of items
   * @return Removes item from player's inventory and resets stats
   */
  public static Cleanups withEquippableItem(final String itemName, final int count) {
    int itemId = ItemDatabase.getItemId(itemName, count, false);
    return withEquippableItem(ItemPool.get(itemId, count));
  }

  /**
   * Puts item in player's inventory and ensures player meets requirements to equip
   *
   * @param itemId Item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withEquippableItem(final int itemId) {
    return withEquippableItem(itemId, 1);
  }

  /**
   * Puts number of items in player's inventory and ensures player meets requirements to equip
   *
   * @param itemId Item to give
   * @param count Quantity of item to give
   * @return Restores the number of this item to the old value
   */
  public static Cleanups withEquippableItem(final int itemId, final int count) {
    return withEquippableItem(ItemPool.get(itemId, count));
  }

  /**
   * Puts item in player's inventory and ensures player meets requirements to equip
   *
   * @param item Item to add
   * @return Removes item from player's inventory and resets stats
   */
  public static Cleanups withEquippableItem(final AdventureResult item) {
    var cleanups = new Cleanups();
    // Do this first so that Equipment lists and outfits will update appropriately
    cleanups.add(withStatsRequiredForEquipment(item));
    cleanups.add(withItem(item));
    return cleanups;
  }

  /**
   * Adds familiar to player's terrarium, but does not take it out
   *
   * @param familiarId Familiar to add
   * @return Removes familiar from the terrarium
   */
  public static Cleanups withFamiliarInTerrarium(final int familiarId) {
    return withFamiliarInTerrarium(familiarId, 0);
  }

  /**
   * Adds familiar to player's terrarium, but does not take it out
   *
   * @param familiarId Familiar to add
   * @param experience Experience for familiar to have
   * @return Removes familiar from the terrarium
   */
  public static Cleanups withFamiliarInTerrarium(final int familiarId, final int experience) {
    var familiar = FamiliarData.registerFamiliar(familiarId, experience);
    KoLCharacter.addFamiliar(familiar);
    return new Cleanups(() -> KoLCharacter.removeFamiliar(familiar));
  }

  /**
   * Adds familiar to player's terrarium, but does not take it out
   *
   * @param familiarId Familiar to add
   * @param itemId Item for familiar to equip
   * @return Removes familiar from the terrarium
   */
  public static Cleanups withFamiliarInTerrariumWithItem(final int familiarId, final int itemId) {
    var familiar = FamiliarData.registerFamiliar(familiarId, 0);
    familiar.setItem(ItemPool.get(itemId, 1));
    KoLCharacter.addFamiliar(familiar);
    return new Cleanups(() -> KoLCharacter.removeFamiliar(familiar));
  }

  /**
   * Takes familiar as player's current familiar
   *
   * @param familiarId Familiar to take
   * @return Reset current familiar
   */
  public static Cleanups withFamiliar(final int familiarId) {
    return withFamiliar(familiarId, 0);
  }

  /**
   * Takes familiar as player's current familiar
   *
   * @param familiarId Familiar to take
   * @param experience Experience for familiar to have
   * @return Reset current familiar
   */
  public static Cleanups withFamiliar(final int familiarId, final int experience) {
    return withFamiliar(familiarId, experience, null);
  }

  /**
   * Takes familiar as player's current familiar
   *
   * @param familiarId Familiar to take
   * @param name Name for familiar to have
   * @return Reset current familiar
   */
  public static Cleanups withFamiliar(final int familiarId, final String name) {
    return withFamiliar(familiarId, 1, name);
  }

  /**
   * Takes familiar as player's current familiar
   *
   * @param familiarId Familiar to take
   * @param experience Experience for familiar to have
   * @param name Name for familiar to have
   * @return Reset current familiar
   */
  public static Cleanups withFamiliar(
      final int familiarId, final int experience, final String name) {
    var old = KoLCharacter.getFamiliar();
    var fam = FamiliarData.registerFamiliar(familiarId, experience);
    if (name != null) fam.setName(name);
    KoLCharacter.setFamiliar(fam);
    return new Cleanups(
        () -> {
          KoLCharacter.setFamiliar(old);
          KoLCharacter.removeFamiliar(fam);
        });
  }

  /**
   * Puts familiar in the Buddy Bjorn
   *
   * @param familiarId Familiar to bjorn
   * @return Removes familiar from buddy bjorn
   */
  public static Cleanups withBjorned(final int familiarId) {
    return withBjorned(new FamiliarData(familiarId));
  }

  /**
   * Puts familiar in the Buddy Bjorn
   *
   * @param familiar Familiar to bjorn
   * @return Removes familiar from buddy bjorn
   */
  public static Cleanups withBjorned(final FamiliarData familiar) {
    var old = KoLCharacter.getBjorned();
    KoLCharacter.setBjorned(familiar);
    return new Cleanups(() -> KoLCharacter.setBjorned(old));
  }

  /**
   * Puts familiar in the Crown of Thrones
   *
   * @param familiarId Familiar to enthrone
   * @return Removes familiar from Crown of Thrones
   */
  public static Cleanups withEnthroned(final int familiarId) {
    return withEnthroned(new FamiliarData(familiarId));
  }

  /**
   * Puts familiar in the Crown of Thrones
   *
   * @param familiar Familiar to enthrone
   * @return Removes familiar from Crown of Thrones
   */
  public static Cleanups withEnthroned(final FamiliarData familiar) {
    var old = KoLCharacter.getEnthroned();
    KoLCharacter.setEnthroned(familiar);
    return new Cleanups(() -> KoLCharacter.setEnthroned(old));
  }

  /**
   * Takes thrall as player's current thrall, and sets class to Pastamancer
   *
   * @param bindSkillId Skill id for binding skill
   * @param level Level for thrall to have
   * @return Reset current familiar
   */
  public static Cleanups withThrall(int bindSkillId, int level) {
    var type = PastaThrallData.skillIdToData(bindSkillId);
    if (type == null) return new Cleanups();

    PastaThrallData.initialize();

    var classCleanups = withClass(AscensionClass.PASTAMANCER);
    var old = KoLCharacter.currentPastaThrall();
    var newThrall =
        KoLCharacter.getPastaThrallList().stream()
            .filter(thrall -> PastaThrallData.dataToId(thrall.getData()) == type.id)
            .findAny()
            .orElseThrow();
    var propertyCleanups = withProperty(type.settingName, String.valueOf(level));
    newThrall.updateFromSetting();
    KoLCharacter.setPastaThrall(newThrall);
    return new Cleanups(
        classCleanups,
        propertyCleanups,
        new Cleanups(
            () -> {
              KoLCharacter.setPastaThrall(old);
              old.updateFromSetting();
            }));
  }

  /**
   * Add florist, set up plants in a location, and go to that location
   *
   * @param locationId Location to put plants
   * @param plants Plants to put there
   * @return Reset location, florist status, plants
   */
  public static Cleanups withFlorist(int locationId, FloristRequest.Florist... plants) {
    KoLAdventure location = AdventureDatabase.getAdventure(locationId);
    FloristRequest.setFloristFriarAvailable(true);
    for (var plant : plants) {
      FloristRequest.addPlant(location.getAdventureName(), plant.id());
    }
    return new Cleanups(
        withLocation(location.getAdventureName()), new Cleanups(FloristRequest::reset));
  }

  /**
   * Add VYKEA companion
   *
   * @param companionTypeId Id of companion type (e.g. VYKEACompanionData.LAMP)
   * @param level Level of companion
   * @return Reset to previous companion or no companion
   */
  public static Cleanups withVykea(VYKEACompanionType companionTypeId, int level) {
    var propertyCleanups =
        new Cleanups(
            withProperty("_VYKEACompanionName", "DÕZEQÍRHRU"),
            withProperty("_VYKEACompanionLevel", level),
            withProperty("_VYKEACompanionType", VYKEACompanionData.typeToString(companionTypeId)));
    VYKEACompanionData.settingsToVYKEACompanion();
    return new Cleanups(
        propertyCleanups, new Cleanups(VYKEACompanionData::settingsToVYKEACompanion));
  }

  /**
   * Clears active effects
   *
   * @return Clears effects
   */
  public static Cleanups withNoEffects() {
    KoLConstants.activeEffects.clear();
    return new Cleanups(KoLConstants.activeEffects::clear);
  }

  /**
   * Gives player a number of turns of the given effect
   *
   * @param effectId Effect to add
   * @param turns Turns of effect to give
   * @return Removes effect
   */
  public static Cleanups withEffect(final int effectId, final int turns) {
    var effect = EffectPool.get(effectId, turns);
    KoLConstants.activeEffects.add(effect);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLConstants.activeEffects.remove(effect);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Gives player a number of turns of the given effect
   *
   * @param effectName Effect to add
   * @param turns Turns of effect to give
   * @return Removes effect
   */
  public static Cleanups withEffect(final String effectName, final int turns) {
    return withEffect(EffectDatabase.getEffectId(effectName), turns);
  }

  /**
   * Gives player one turn of the given effect
   *
   * @param effectName Effect to add
   * @return Removes effect
   */
  public static Cleanups withEffect(final String effectName) {
    return withEffect(effectName, 1);
  }

  /**
   * Gives player one turn of the given effect
   *
   * @param effectId Effect to add
   * @return Removes effect
   */
  public static Cleanups withEffect(final int effectId) {
    return withEffect(effectId, 1);
  }

  /**
   * Gives player (effectively) infinite turns of an effect
   *
   * @param effectName Effect to add intrinsicly
   * @return Removes effect
   */
  public static Cleanups withIntrinsicEffect(final String effectName) {
    return withEffect(effectName, Integer.MAX_VALUE);
  }

  /**
   * Gives player (effectively) infinite turns of an effect
   *
   * @param effectId Effect to add intrinsicly
   * @return Removes effect
   */
  public static Cleanups withIntrinsicEffect(final int effectId) {
    return withEffect(effectId, Integer.MAX_VALUE);
  }

  /**
   * Gives player a skill
   *
   * @param skillName Skill to add
   * @return Removes the skill
   */
  public static Cleanups withSkill(final String skillName) {
    KoLCharacter.addAvailableSkill(skillName);
    return new Cleanups(() -> KoLCharacter.removeAvailableSkill(skillName));
  }

  /**
   * Gives player a skill
   *
   * @param skillId Skill to add
   * @return Removes the skill
   */
  public static Cleanups withSkill(final int skillId) {
    KoLCharacter.addAvailableSkill(skillId);
    return new Cleanups(() -> KoLCharacter.removeAvailableSkill(skillId));
  }

  /**
   * Ensures player does not have a skill
   *
   * @param skillId Skill to ensure is removed
   * @return Removes the skill if it was gained
   */
  public static Cleanups withoutSkill(final int skillId) {
    KoLCharacter.removeAvailableSkill(skillId);
    return new Cleanups(() -> KoLCharacter.removeAvailableSkill(skillId));
  }

  /**
   * Ensures player does not have a skill
   *
   * @param skillName Skill to ensure is removed
   * @return Removes the skill if it was gained
   */
  public static Cleanups withoutSkill(final String skillName) {
    return withoutSkill(SkillDatabase.getSkillId(skillName));
  }

  /**
   * Sets player's substats to given values.
   *
   * @param muscle Muscle substats
   * @param mysticality Mysticality substats
   * @param moxie Moxie substats
   * @return Resets substats to zero
   */
  public static Cleanups withSubStats(final long muscle, final long mysticality, final long moxie) {
    KoLCharacter.setStatPoints(
        (int) Math.floor(Math.sqrt(muscle)),
        muscle,
        (int) Math.floor(Math.sqrt(mysticality)),
        mysticality,
        (int) Math.floor(Math.sqrt(moxie)),
        moxie);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLCharacter.setStatPoints(0, 0, 0, 0, 0, 0);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Sets player's stats to given values. Substats are set to stat squared
   *
   * @param muscle Muscle mainstat
   * @param mysticality Mysticality mainstat
   * @param moxie Moxie mainstat
   * @return Resets stats to zero
   */
  public static Cleanups withStats(final int muscle, final int mysticality, final int moxie) {
    return withSubStats(muscle * muscle, mysticality * mysticality, moxie * moxie);
  }

  /**
   * Sets player's muscle to given value. Substats are set to stat squared
   *
   * @param muscle Desired muscle
   * @return Resets muscle to zero
   */
  public static Cleanups withMuscle(final int muscle) {
    return withMuscle(muscle, muscle);
  }

  /**
   * Sets player's muscle to given value. Substats are set to stat squared
   *
   * @param muscle Desired muscle
   * @param buffedMuscle Buffed muscle value
   * @return Resets muscle to zero
   */
  public static Cleanups withMuscle(final int muscle, final int buffedMuscle) {
    KoLCharacter.setMuscle(buffedMuscle, (long) muscle * muscle);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(() -> KoLCharacter.setMuscle(0, 0));
  }

  /**
   * Sets player's muscle to given value unless the current value is higher. Substats are set to
   * stat squared
   *
   * @param muscle Desired minimum muscle
   * @return Resets muscle to zero
   */
  public static Cleanups withMuscleAtLeast(final int muscle) {
    return withMuscle(Math.max(muscle, KoLCharacter.getBaseMuscle()));
  }

  /**
   * Sets player's mysticality to given value. Substats are set to stat squared
   *
   * @param mysticality Desired mysticality
   * @return Resets mysticality to zero
   */
  public static Cleanups withMysticality(final int mysticality) {
    return withMysticality(mysticality, mysticality);
  }

  /**
   * Sets player's mysticality to given value. Substats are set to stat squared
   *
   * @param mysticality Desired mysticality
   * @param buffedMysticality Buffed mysticality value
   * @return Resets mysticality to zero
   */
  public static Cleanups withMysticality(final int mysticality, final int buffedMysticality) {
    KoLCharacter.setMysticality(buffedMysticality, (long) mysticality * mysticality);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(() -> KoLCharacter.setMysticality(0, 0));
  }

  /**
   * Sets player's mysticality to given value unless the current value is higher. Substats are set
   * to stat squared
   *
   * @param mysticality Desired minimum mysticality
   * @return Resets mysticality to zero
   */
  public static Cleanups withMysticalityAtLeast(final int mysticality) {
    return withMysticality(Math.max(mysticality, KoLCharacter.getBaseMysticality()));
  }

  /**
   * Sets player's moxie to given value. Substats are set to stat squared
   *
   * @param moxie Desired moxie
   * @return Resets moxie to zero
   */
  public static Cleanups withMoxie(final int moxie) {
    return withMoxie(moxie, moxie);
  }

  /**
   * Sets player's moxie to given value. Substats are set to stat squared
   *
   * @param moxie Desired moxie
   * @param buffedMoxie Buffed moxie value
   * @return Resets moxie to zero
   */
  public static Cleanups withMoxie(final int moxie, final int buffedMoxie) {
    KoLCharacter.setMoxie(buffedMoxie, (long) moxie * moxie);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(() -> KoLCharacter.setMoxie(0, 0));
  }

  /**
   * Sets player's moxie to given value unless the current value is higher. Substats are set to stat
   * squared
   *
   * @param moxie Desired minimum moxie
   * @return Resets moxie to zero
   */
  public static Cleanups withMoxieAtLeast(final int moxie) {
    return withMoxie(Math.max(moxie, KoLCharacter.getBaseMoxie()));
  }

  /**
   * Sets the player's gender to the given value.
   *
   * @param gender Required gender
   * @return Resets gender to unknown
   */
  public static Cleanups withGender(final Gender gender) {
    KoLCharacter.setGender(gender);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(() -> KoLCharacter.setGender(Gender.UNKNOWN));
  }

  /**
   * Sets the player's level to the given value.
   *
   * @param level Required level
   * @return Resets level to zero
   */
  public static Cleanups withLevel(final int level) {
    int previousLevel = KoLCharacter.getLevel();
    KoLCharacter.setLevel(level);
    return new Cleanups(() -> KoLCharacter.setLevel(previousLevel));
  }

  /**
   * Sets the player's current, maximum and base HP
   *
   * @param current Desired current HP
   * @param maximum Desired buffed maximum HP
   * @param base Desired base maximum HP
   * @return Resets these values to zero
   */
  public static Cleanups withHP(final long current, final long maximum, final long base) {
    KoLCharacter.setHP(current, maximum, base);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLCharacter.setHP(0, 0, 0);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Sets the player's current, maximum and base MP
   *
   * @param current Desired current MP
   * @param maximum Desired buffed maximum MP
   * @param base Desired base maximum MP
   * @return Resets these values to zero
   */
  public static Cleanups withMP(final long current, final long maximum, final long base) {
    KoLCharacter.setMP(current, maximum, base);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLCharacter.setMP(0, 0, 0);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Sets King Liberated
   *
   * @return Resets King Liberated
   */
  public static Cleanups withKingLiberated() {
    var cleanups = new Cleanups(withProperty("lastKingLiberation"), withProperty("kingLiberated"));
    KoLCharacter.setKingLiberated(true);
    return cleanups;
  }

  /**
   * Sets the player's remaining adventures
   *
   * @param adventures Desired adventures remaining
   * @return Resets remaining adventures to previous value
   */
  public static Cleanups withAdventuresLeft(final int adventures) {
    var old = KoLCharacter.getAdventuresLeft();
    KoLCharacter.setAdventuresLeft(adventures);
    return new Cleanups(() -> KoLCharacter.setAdventuresLeft(old));
  }

  /**
   * Sets the player's current run
   *
   * @param adventures How many adventures so far this run
   * @return Resets remaining adventures to previous value
   */
  public static Cleanups withCurrentRun(final int adventures) {
    var old = KoLCharacter.getCurrentRun();
    KoLCharacter.setCurrentRun(adventures);
    return new Cleanups(() -> KoLCharacter.setCurrentRun(old));
  }

  /**
   * Sets the player's current run
   *
   * @return Resets remaining adventures to previous value
   */
  public static Cleanups withCurrentRun() {
    return withCurrentRun(KoLCharacter.getCurrentRun());
  }

  /**
   * Sets the player's ascensions
   *
   * @param ascensions Desired ascensions
   * @return Resets ascensions to previous value
   */
  public static Cleanups withAscensions(final int ascensions) {
    var old = KoLCharacter.getAscensions();
    KoLCharacter.setAscensions(ascensions);
    return new Cleanups(() -> KoLCharacter.setAscensions(old));
  }

  /**
   * Sets the player's hippy stone to broken
   *
   * @return Set's the player's hippy stone to unbroken
   */
  public static Cleanups withHippyStoneBroken() {
    KoLCharacter.setHippyStoneBroken(true);
    return new Cleanups(() -> KoLCharacter.setHippyStoneBroken(false));
  }

  /**
   * Sets the player's pvp attacks left
   *
   * @param fullness Desired fullness
   * @return Resets fullness to previous value
   */
  public static Cleanups withAttacksLeft(final int attacks) {
    var old = KoLCharacter.getAttacksLeft();
    KoLCharacter.setAttacksLeft(attacks);
    return new Cleanups(() -> KoLCharacter.setAttacksLeft(old));
  }

  /**
   * Sets the player's fullness
   *
   * @param fullness Desired fullness
   * @return Resets fullness to previous value
   */
  public static Cleanups withFullness(final int fullness) {
    var old = KoLCharacter.getFullness();
    KoLCharacter.setFullness(fullness);
    return new Cleanups(() -> KoLCharacter.setFullness(old));
  }

  /**
   * Sets the player's inebriety
   *
   * @param inebriety Desired inebriety
   * @return Resets inebriety to previous value
   */
  public static Cleanups withInebriety(final int inebriety) {
    var old = KoLCharacter.getInebriety();
    KoLCharacter.setInebriety(inebriety);
    return new Cleanups(() -> KoLCharacter.setInebriety(old));
  }

  /**
   * Sets the player's spleen use
   *
   * @param spleenUse Desired spleen use
   * @return Resets spleen use to previous value
   */
  public static Cleanups withSpleenUse(final int spleenUse) {
    var old = KoLCharacter.getSpleenUse();
    KoLCharacter.setSpleenUse(spleenUse);
    return new Cleanups(() -> KoLCharacter.setSpleenUse(old));
  }

  /**
   * Sets the player's class
   *
   * @param ascensionClass Desired class
   * @return Resets the class to the previous value
   */
  public static Cleanups withClass(final AscensionClass ascensionClass) {
    var old = KoLCharacter.getAscensionClass();
    KoLCharacter.setAscensionClass(ascensionClass);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLCharacter.setAscensionClass(old);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Sets the player's sign
   *
   * @param signName Name of desired sign
   * @return Resets the sign to the previous value
   */
  public static Cleanups withSign(final String signName) {
    return withSign(ZodiacSign.find(signName));
  }

  /**
   * Sets the player's sign
   *
   * @param sign Desired sign
   * @return Resets the sign to the previous value
   */
  public static Cleanups withSign(final ZodiacSign sign) {
    var old = KoLCharacter.getSign();
    KoLCharacter.setSign(sign);
    KoLCharacter.recalculateAdjustments();
    ConcoctionDatabase.refreshConcoctions();
    return new Cleanups(
        () -> {
          KoLCharacter.setSign(old);
          KoLCharacter.recalculateAdjustments();
          ConcoctionDatabase.refreshConcoctions();
        });
  }

  /**
   * Sets the player's path
   *
   * @param path Desired path
   * @return Resets the path to the previous value
   */
  public static Cleanups withPath(final Path path) {
    var old = KoLCharacter.getPath();
    KoLCharacter.setPath(path);
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(
        () -> {
          KoLCharacter.setPath(old);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Sets the player's location for modifier calculations
   *
   * @param location Desired location
   * @return Resets the location to the previous value
   */
  public static Cleanups withLocation(final String location) {
    var old = AdventureDatabase.getAdventure(Modifiers.currentLocation);
    Modifiers.setLocation(AdventureDatabase.getAdventure(location));
    return new Cleanups(() -> Modifiers.setLocation(old));
  }

  /**
   * Sets the Monster Control Device
   *
   * @param mcdLevel Desired MCD level
   * @return Resets the MCD to the previous value
   */
  public static Cleanups withMCD(int mcdLevel) {
    final int original = KoLCharacter.getMindControlLevel();
    KoLCharacter.setMindControlLevel(mcdLevel);

    return new Cleanups(() -> KoLCharacter.setMindControlLevel(original));
  }

  /**
   * Sets the player's limit mode
   *
   * @param limitMode Desired limit mode
   * @return Resets the limit mode to the previous value
   */
  public static Cleanups withLimitMode(final LimitMode limitMode) {
    var old = KoLCharacter.getLimitMode();
    KoLCharacter.setLimitMode(limitMode);
    return new Cleanups(() -> KoLCharacter.setLimitMode(old));
  }

  /**
   * Set's the player's fight state such that KoLmafia believes they are in an anapest mode
   *
   * @return Resets the location to the false
   */
  public static Cleanups withAnapest() {
    FightRequest.anapest = true;
    return new Cleanups(() -> FightRequest.anapest = false);
  }

  /**
   * Set next monster
   *
   * @param monster Monster to set
   * @return Restores to previous value
   */
  public static Cleanups withNextMonster(final MonsterData monster) {
    var previousMonster = MonsterStatusTracker.getLastMonster();
    MonsterStatusTracker.setNextMonster(monster);
    return new Cleanups(() -> MonsterStatusTracker.setNextMonster(previousMonster));
  }

  /**
   * Set next monster
   *
   * @param monsterName Monster to set
   * @return Restores to previous value
   */
  public static Cleanups withNextMonster(final String monsterName) {
    return withNextMonster(MonsterDatabase.findMonster(monsterName));
  }

  /**
   * Set the day used by HolidayDatabase
   *
   * @param year Year to set
   * @param month Month to set
   * @param day Day to set
   * @return Restores to using the real day
   */
  public static Cleanups withDay(final int year, final Month month, final int day) {
    return withDay(year, month, day, 12, 0);
  }

  /**
   * Set the day used by HolidayDatabase
   *
   * @param year Year to set
   * @param month Month to set
   * @param day Day to set
   * @param hour Hour to set
   * @param minute Minute to set
   * @return Restores to using the previous date time manager
   */
  public static Cleanups withDay(
      final int year, final Month month, final int day, final int hour, final int minute) {
    var dtm = Statics.DateTimeManager;
    var localDateTime = LocalDateTime.of(year, month, day, hour, minute);
    TestStatics.setDate(localDateTime);

    HolidayDatabase.reset();

    return new Cleanups(
        () -> {
          TestStatics.setDateTimeManager(dtm);
          HolidayDatabase.reset();
        });
  }

  /**
   * Sets the player's noobcore absorbs
   *
   * @param absorbs Number of absorbs to use
   * @return Restores to the old value
   */
  public static Cleanups withUsedAbsorbs(final int absorbs) {
    var old = KoLCharacter.getAbsorbs();
    KoLCharacter.setAbsorbs(absorbs);
    return new Cleanups(() -> KoLCharacter.setAbsorbs(old));
  }

  /**
   * Sets the player to be in hardcore
   *
   * @return restores hardcore
   */
  public static Cleanups withHardcore() {
    return withHardcore(true);
  }

  /**
   * Sets the player's hardcore status
   *
   * @param hardcore Hardcore or not
   * @return Restores previous value
   */
  public static Cleanups withHardcore(final boolean hardcore) {
    var wasHardcore = KoLCharacter.isHardcore();
    KoLCharacter.setHardcore(hardcore);
    return new Cleanups(() -> KoLCharacter.setHardcore(wasHardcore));
  }

  /**
   * Sets the player's campground as having an item installed in it
   *
   * @param itemId Item to add
   * @return Removes the item
   */
  public static Cleanups withCampgroundItem(final int itemId) {
    CampgroundRequest.setCampgroundItem(itemId, 1);
    return new Cleanups(() -> CampgroundRequest.removeCampgroundItem(ItemPool.get(itemId, 1)));
  }

  /**
   * Sets the player's campground as having an item installed in it
   *
   * @param itemId Item to add
   * @param count associated count
   * @return Removes the item
   */
  public static Cleanups withCampgroundItem(final int itemId, final int count) {
    CampgroundRequest.setCampgroundItem(itemId, count);
    return new Cleanups(() -> CampgroundRequest.removeCampgroundItem(ItemPool.get(itemId, 1)));
  }

  /**
   * Sets the player's campground as having an item installed in it
   *
   * @param item Item to add
   * @return Removes the item
   */
  public static Cleanups withCampgroundItem(final AdventureResult item) {
    CampgroundRequest.setCampgroundItem(item);
    return new Cleanups(() -> CampgroundRequest.removeCampgroundItem(item));
  }

  /**
   * Clears the Campground and clears it again when done. This prevents leakage if the test adds
   * items to the campground.
   *
   * @return Empties the Campground
   */
  public static Cleanups withEmptyCampground() {
    CampgroundRequest.reset();
    return new Cleanups(CampgroundRequest::reset);
  }

  /**
   * Sets the player's workshed
   *
   * @param itemId Item to set
   * @return Removes workshed item
   */
  public static Cleanups withWorkshedItem(final int itemId) {
    CampgroundRequest.setCurrentWorkshedItem(itemId);
    return new Cleanups(CampgroundRequest::resetCurrentWorkshedItem);
  }

  /**
   * Sets the player's workshed
   *
   * @param item Item to set
   * @return Removes workshed item
   */
  public static Cleanups withWorkshedItem(final AdventureResult item) {
    if (item == null) {
      CampgroundRequest.resetCurrentWorkshedItem();
    } else {
      CampgroundRequest.setCurrentWorkshedItem(item);
    }

    return new Cleanups(CampgroundRequest::resetCurrentWorkshedItem);
  }

  /**
   * Sets the user as having a range installed
   *
   * @return Removes the range
   */
  public static Cleanups withRange() {
    KoLCharacter.setRange(true);
    ConcoctionDatabase.refreshConcoctions();
    return new Cleanups(
        () -> {
          KoLCharacter.setRange(false);
          ConcoctionDatabase.refreshConcoctions();
        });
  }

  /**
   * Sets the user as having a cocktail kit installed
   *
   * @return Removes the cocktail kit
   */
  public static Cleanups withCocktailKit() {
    KoLCharacter.setCocktailKit(true);
    ConcoctionDatabase.refreshConcoctions();
    return new Cleanups(
        () -> {
          KoLCharacter.setCocktailKit(false);
          ConcoctionDatabase.refreshConcoctions();
        });
  }

  /**
   * Sets the user as having this dwlling installed
   *
   * @param dwellingId Id of dwelling to have installed
   * @return Restores the old dwelling state
   */
  public static Cleanups withDwelling(int dwellingId) {
    final int oldDwelling = CampgroundRequest.getCurrentDwelling().getItemId();
    CampgroundRequest.setCurrentDwelling(dwellingId);
    return new Cleanups(
        withCampgroundItem(dwellingId),
        new Cleanups(() -> CampgroundRequest.setCurrentDwelling(oldDwelling)));
  }

  /**
   * Sets the user as having this item installed in their Chateau Mantegna
   *
   * @param itemIds Id of items to have installed
   * @return Restores the old chateau state
   */
  public static Cleanups withChateau(int... itemIds) {
    final List<AdventureResult> oldChateau = new ArrayList<>(KoLConstants.chateau);

    for (var itemId : itemIds) {
      ChateauRequest.gainItem(ItemPool.get(itemId));
    }

    KoLCharacter.recalculateAdjustments();

    return new Cleanups(
        () -> {
          KoLConstants.chateau.clear();
          KoLConstants.chateau.addAll(oldChateau);
          KoLCharacter.recalculateAdjustments();
        });
  }

  /**
   * Does nothing, but ensures the given property is reverted as part of cleanup
   *
   * @param key Key of property
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key) {
    var current = Preferences.getString(key);
    return withProperty(key, current);
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final int value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getInteger(key);
    Preferences.setInteger(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setInteger(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final float value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getFloat(key);
    Preferences.setFloat(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setFloat(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final double value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getDouble(key);
    Preferences.setDouble(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setDouble(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final long value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getLong(key);
    Preferences.setLong(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setLong(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final String value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getString(key);
    Preferences.setString(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setString(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Sets a property for the user
   *
   * @param key Key of property
   * @param value Value to set
   * @return Restores the previous value of the property
   */
  public static Cleanups withProperty(final String key, final boolean value) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key, global);
    var oldValue = Preferences.getBoolean(key);
    Preferences.setBoolean(key, value);
    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setBoolean(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Ensures that property does not exist, restoring afterward if necessary
   *
   * @param key Key of property
   * @return Restores the previous value of the property if it existed
   */
  public static Cleanups withoutProperty(final String key) {
    var global = Preferences.isGlobalProperty(key);
    var exists = Preferences.propertyExists(key);
    var oldValue = Preferences.getString(key);

    if (exists) {
      Preferences.removeProperty(key, global);
    }

    return new Cleanups(
        () -> {
          if (exists) {
            Preferences.setString(key, oldValue);
          } else {
            Preferences.removeProperty(key, global);
          }
        });
  }

  /**
   * Does nothing, but ensures the given quest is reverted as part of cleanup
   *
   * @param quest Quest to set
   * @return Restores previous value
   */
  public static Cleanups withQuestProgress(final QuestDatabase.Quest quest) {
    var current = QuestDatabase.getQuest(quest);
    return new Cleanups(() -> QuestDatabase.setQuest(quest, current));
  }

  /**
   * Sets progress for a given quest
   *
   * @param quest Quest to set
   * @param value Value for quest property
   * @return Restores previous value
   */
  public static Cleanups withQuestProgress(final QuestDatabase.Quest quest, final String value) {
    var oldValue = QuestDatabase.getQuest(quest);
    QuestDatabase.setQuest(quest, value);
    return new Cleanups(() -> QuestDatabase.setQuest(quest, oldValue));
  }

  /**
   * Sets progress for a given quest
   *
   * @param quest Quest to set
   * @param step Step to set
   * @return Restores previous value
   */
  public static Cleanups withQuestProgress(final QuestDatabase.Quest quest, final int step) {
    return withQuestProgress(quest, "step" + step);
  }

  /**
   * Sets supplied HttpClient.Builder to be used by GenericRequest
   *
   * @param builder The builder to use
   * @return restores previous builder
   */
  public static Cleanups withHttpClientBuilder(HttpClient.Builder builder) {
    var old = HttpUtilities.getClientBuilder();
    HttpUtilities.setClientBuilder(() -> builder);
    GenericRequest.resetClient();
    GenericRequest.sessionId = "TEST"; // we fake the client, so "run" the requests

    return new Cleanups(
        () -> {
          GenericRequest.sessionId = null;
          HttpUtilities.setClientBuilder(() -> old);
          GenericRequest.resetClient();
        });
  }

  /**
   * Sets supplied passwordHash to be used by GenericRequest
   *
   * @param passwordHash The passwordHash to use
   * @return restores previous passwordHash
   */
  public static Cleanups withPasswordHash(String passwordHash) {
    var old = GenericRequest.passwordHash;
    GenericRequest.setPasswordHash(passwordHash);
    return new Cleanups(
        () -> {
          GenericRequest.setPasswordHash(old);
        });
  }

  /**
   * Sets next response to a GenericRequest Note that this uses its own FakeHttpClientBuilder so
   * getRequests() will not work on one set separately
   *
   * @param code Status code to fake
   * @param response Response text to fake
   * @return Cleans up so this response is not given again
   */
  public static Cleanups withNextResponse(final int code, final String response) {
    return withNextResponse(code, new HashMap<>(), response);
  }

  /**
   * Sets next response to a GenericRequest Note that this uses its own FakeHttpClientBuilder so
   * getRequests() will not work on one set separately
   *
   * @param code Status code to fake
   * @param headers Response headers to fake
   * @param response Response text to fake
   * @return Cleans up so this response is not given again
   */
  public static Cleanups withNextResponse(
      final int code, final Map<String, List<String>> headers, final String response) {
    if (code > 0) {
      return withNextResponse(new FakeHttpResponse<>(code, headers, response));
    }

    return withNextResponse();
  }

  /**
   * Sets next response to a GenericRequest Note that this uses its own FakeHttpClientBuilder so
   * getRequests() will not work on one set separately
   *
   * @param fakeHttpResponses Responses to fake
   * @return Cleans up so this response is not given again
   */
  @SafeVarargs
  public static Cleanups withNextResponse(final FakeHttpResponse<String>... fakeHttpResponses) {
    var old = HttpUtilities.getClientBuilder();
    var builder = new FakeHttpClientBuilder();
    HttpUtilities.setClientBuilder(() -> builder);
    GenericRequest.resetClient();
    GenericRequest.sessionId = "TEST"; // we fake the client, so "run" the requests

    for (FakeHttpResponse<String> fakeHttpResponse : fakeHttpResponses) {
      builder.client.addResponse(fakeHttpResponse);
    }

    return new Cleanups(
        () -> {
          GenericRequest.sessionId = null;
          HttpUtilities.setClientBuilder(() -> old);
          GenericRequest.resetClient();
        });
  }

  /**
   * Sets next response to a GenericRequest Note that this uses its own FakeHttpClientBuilder so
   * getRequests() will not work on one set separately
   *
   * @param responseMap Map of responses, keyed by request.
   * @return Cleans up so this response is not given afterwards.
   */
  public static Cleanups withResponseMap(final Map<String, FakeHttpResponse<String>> responseMap) {
    var old = HttpUtilities.getClientBuilder();
    var builder = new FakeHttpClientBuilder();
    HttpUtilities.setClientBuilder(() -> builder);
    GenericRequest.resetClient();
    GenericRequest.sessionId = "TEST"; // we fake the client, so "run" the requests

    for (var entry : responseMap.entrySet()) {
      builder.client.addResponse(entry.getKey(), entry.getValue());
    }

    return new Cleanups(
        () -> {
          GenericRequest.sessionId = null;
          HttpUtilities.setClientBuilder(() -> old);
          GenericRequest.resetClient();
        });
  }

  /**
   * Visits a choice with a given choice and response
   *
   * @param choice Choice to set
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withChoice(final int choice, final String responseText) {
    var oldChoice = ChoiceManager.lastChoice;
    var oldDecision = ChoiceManager.lastDecision;

    var req = new GenericRequest("choice.php?whichchoice=" + choice);
    req.responseText = responseText;

    ChoiceManager.preChoice(req);
    ChoiceControl.visitChoice(req);

    return new Cleanups(
        () -> {
          ChoiceManager.lastChoice = oldChoice;
          ChoiceManager.lastDecision = oldDecision;
        });
  }

  public static Cleanups withChoice(
      final BiConsumer<String, GenericRequest> cb,
      final int choice,
      final int decision,
      final String extra,
      final String responseText) {
    ChoiceManager.lastChoice = choice;
    ChoiceManager.lastDecision = decision;
    var url = "choice.php?choice=" + choice + "&option=" + decision;
    if (extra != null) url = url + "&" + extra;
    var req = new GenericRequest(url);
    req.responseText = responseText;
    cb.accept(url, req);

    return new Cleanups(
        () -> {
          ChoiceManager.lastChoice = 0;
          ChoiceManager.lastDecision = 0;
        });
  }

  /**
   * Runs postChoice0 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param extra Any extra parameters for the request
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice0(
      final int choice, final int decision, final String extra, final String responseText) {
    return withChoice(
        (url, req) -> ChoiceControl.postChoice0(choice, url, req),
        choice,
        decision,
        extra,
        responseText);
  }

  /**
   * Runs postChoice0 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice0(
      final int choice, final int decision, final String responseText) {
    return withPostChoice0(choice, decision, null, responseText);
  }

  /**
   * Runs postChoice0 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice0(final int choice, final int decision) {
    return withPostChoice0(choice, decision, "");
  }

  /**
   * Runs postChoice1 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param extra Any extra parameters for the request
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice1(
      final int choice, final int decision, final String extra, final String responseText) {
    return withChoice(ChoiceControl::postChoice1, choice, decision, extra, responseText);
  }

  /**
   * Runs postChoice1 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice1(
      final int choice, final int decision, final String responseText) {
    return withPostChoice1(choice, decision, null, responseText);
  }

  /**
   * Runs postChoice1 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice1(final int choice, final int decision) {
    return withPostChoice1(choice, decision, "");
  }

  /**
   * Runs postChoice2 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param extra Any extra parameters for the request
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice2(
      final int choice, final int decision, final String extra, final String responseText) {
    return withChoice(ChoiceControl::postChoice2, choice, decision, extra, responseText);
  }

  /**
   * Runs postChoice2 with a given choice and decision and response
   *
   * @param choice Choice to set
   * @param decision Decision to set
   * @param responseText Response to fake
   * @return Restores last choice and last decision
   */
  public static Cleanups withPostChoice2(
      final int choice, final int decision, final String responseText) {
    return withPostChoice2(choice, decision, null, responseText);
  }

  /**
   * Sets the last choice and last decision values
   *
   * @param choice Choice number
   * @param decision Decision number
   * @return Restores previous value
   */
  public static Cleanups withPostChoice2(final int choice, final int decision) {
    return withPostChoice2(choice, decision, "");
  }

  /**
   * Simulates a choice (postChoice1, processResults and then postChoice2)
   *
   * <p>{@code @todo} Still needs some more choice handling (postChoice0)
   *
   * @param choice Choice number
   * @param decision Decision number
   * @param responseText Response text to simulate
   * @return Restores state for choice handling
   */
  public static Cleanups withChoice(
      final int choice, final int decision, final String responseText) {
    return withChoice(choice, decision, null, responseText);
  }

  /**
   * Simulates a choice (postChoice1, processResults and then postChoice2)
   *
   * <p>{@code @todo} Still needs some more choice handling (postChoice0)
   *
   * @param choice Choice number
   * @param decision Decision number
   * @param extra Any extra parameters for the request
   * @param responseText Response text to simulate
   * @return Restores state for choice handling
   */
  public static Cleanups withChoice(
      final int choice, final int decision, final String extra, final String responseText) {
    var cleanups = new Cleanups();
    cleanups.add(withPostChoice0(choice, decision, extra, responseText));
    cleanups.add(withPostChoice1(choice, decision, extra, responseText));
    ResultProcessor.processResults(false, responseText);
    cleanups.add(withPostChoice2(choice, decision, extra, responseText));
    return cleanups;
  }

  /**
   * Sets the last location
   *
   * @param lastLocationName Last location name to set
   * @return Restores previous value
   */
  public static Cleanups withLastLocation(final String lastLocationName) {
    var location = AdventureDatabase.getAdventureByName(lastLocationName);
    return withLastLocation(location);
  }

  public static Cleanups withLastLocation(final KoLAdventure lastLocation) {
    var old = KoLAdventure.lastVisitedLocation;
    var oldVanilla = KoLAdventure.lastZoneName;

    var clearProperties =
        new Cleanups(
            withProperty("lastAdventure"),
            withProperty("hiddenApartmentProgress"),
            withProperty("hiddenHospitalProgress"),
            withProperty("hiddenOfficeProgress"),
            withProperty("hiddenBowlingAlleyProgress"));

    if (lastLocation == null) {
      KoLAdventure.setLastAdventure("None");
      KoLAdventure.lastZoneName = null;
    } else {
      KoLAdventure.setLastAdventure(lastLocation);
      KoLAdventure.lastZoneName = lastLocation.getAdventureName();
    }

    var cleanups =
        new Cleanups(
            () -> {
              KoLAdventure.setLastAdventure(old);
              KoLAdventure.lastZoneName = oldVanilla;
            });
    cleanups.add(clearProperties);
    return cleanups;
  }

  /**
   * Acts like the player is currently in a fight
   *
   * @return Restores previous value
   */
  public static Cleanups withFight() {
    return withFight(1);
  }

  /**
   * Acts like the player is currently on the given round of a fight
   *
   * @return Restores previous value
   */
  public static Cleanups withFight(final int round) {
    var old = FightRequest.currentRound;
    FightRequest.currentRound = round;
    return new Cleanups(
        () -> {
          FightRequest.resetInstance();
          FightRequest.preFight(false);
          FightRequest.clearInstanceData();
        });
  }

  /**
   * Acts like the player is currently in a multi-fight
   *
   * @return Restores previous value
   */
  public static Cleanups withMultiFight() {
    var old = FightRequest.inMultiFight;
    FightRequest.inMultiFight = true;
    return new Cleanups(() -> FightRequest.inMultiFight = old);
  }

  /**
   * Acts like the player is currently handling a choice
   *
   * @return Restores previous value
   */
  public static Cleanups withHandlingChoice() {
    var old = ChoiceManager.handlingChoice;
    ChoiceManager.handlingChoice = true;
    return new Cleanups(() -> ChoiceManager.handlingChoice = old);
  }

  /**
   * Sets the player's "currently handling a choice" flag
   *
   * @param handlingChoice Whether player should appear to be currently handling a choice
   * @return Restores previous value
   */
  public static Cleanups withHandlingChoice(final boolean handlingChoice) {
    var old = ChoiceManager.handlingChoice;
    ChoiceManager.handlingChoice = handlingChoice;
    return new Cleanups(() -> ChoiceManager.handlingChoice = old);
  }

  /**
   * Sets the player's "currently handling a choice" flag
   *
   * @param whichChoice which choice is currently being handled
   * @return Restores previous value
   */
  public static Cleanups withHandlingChoice(final int whichChoice) {
    ChoiceManager.handlingChoice = whichChoice != 0;
    ChoiceManager.lastChoice = whichChoice;
    return new Cleanups(
        () -> {
          ChoiceManager.handlingChoice = false;
          ChoiceManager.lastChoice = 0;
        });
  }

  /**
   * Sets the current "item monster" (the item source of the currently-being-fought monster)
   *
   * @param itemMonster Item source for monster
   * @return Restores to previous value
   */
  public static Cleanups withItemMonster(final String itemMonster) {
    var old = GenericRequest.itemMonster;
    GenericRequest.itemMonster = itemMonster;
    return new Cleanups(() -> GenericRequest.itemMonster = old);
  }

  /**
   * Sets the player's ronin status
   *
   * @param inRonin Whether the player is in Ronin
   * @return Restores to previous value
   */
  public static Cleanups withRonin(final boolean inRonin) {
    var old = KoLCharacter.inRonin();
    KoLCharacter.setRonin(inRonin);
    return new Cleanups(() -> KoLCharacter.setRonin(old));
  }

  /**
   * Sets the player's "can interact" status (i.e. whether they are under ronin-style restrictions)
   *
   * @param canInteract Whether the player can interact
   * @return Restores to previous value
   */
  public static Cleanups withInteractivity(final boolean canInteract) {
    var old = CharPaneRequest.canInteract();
    CharPaneRequest.setCanInteract(canInteract);
    return new Cleanups(() -> CharPaneRequest.setCanInteract(old));
  }

  /**
   * Sets the player's standard restriction status
   *
   * @param restricted Whether the player is standard restricted
   * @return Restores to previous value
   */
  public static Cleanups withRestricted(final boolean restricted) {
    var old = KoLCharacter.getRestricted();
    KoLCharacter.setRestricted(restricted);
    return new Cleanups(() -> KoLCharacter.setRestricted(old));
  }

  /**
   * Sets whether a particular item / skill is allowed in Standard
   *
   * @param type The type of key
   * @param key The restricted item / skill
   * @return Restores to previous value
   */
  public static Cleanups withNotAllowedInStandard(final RestrictedItemType type, final String key) {
    var lcKey = key.toLowerCase();
    var map = StandardRequest.getRestrictionMap();
    map.computeIfAbsent(type, k -> new HashSet<>()).add(lcKey);

    return new Cleanups(
        () -> {
          var val = map.get(type);
          if (val != null) val.remove(lcKey);
        });
  }

  /**
   * Sets the basement level
   *
   * @param level Level to set
   * @return Restores to the previous value
   */
  public static Cleanups withBasementLevel(final int level) {
    var old = BasementRequest.getBasementLevel();
    BasementRequest.setBasementLevel(level);
    return new Cleanups(() -> BasementRequest.setBasementLevel(old));
  }

  /**
   * Sets the basement level to zero This is useful if the tested function is going to set the level
   * and we just want to make sure it gets reset
   *
   * @return Restores the previous value
   */
  public static Cleanups withBasementLevel() {
    return withBasementLevel(0);
  }

  /**
   * Sets the continuation state (i.e. whether the CLI is in green "good", red "bad" mode etc)
   *
   * @param continuationState State to set
   * @return Restores previous continuation state
   */
  public static Cleanups withContinuationState(final MafiaState continuationState) {
    var old = StaticEntity.getContinuationState();
    StaticEntity.setContinuationState(continuationState);
    return new Cleanups(() -> StaticEntity.setContinuationState(old));
  }

  /**
   * Sets the continuation state to continue (green "good" mode)
   *
   * @return Restores previous state
   */
  public static Cleanups withContinuationState() {
    return withContinuationState(MafiaState.CONTINUE);
  }

  /**
   * Sets a counter
   *
   * @param turns how many turns
   * @param label what to call it
   * @param image what image to show in charpane
   * @return stops the counter
   */
  public static Cleanups withCounter(int turns, String label, String image) {
    var cleanups = new Cleanups(withProperty("relayCounters"));
    var counter = TurnCounter.startCounting(turns, label, image);
    cleanups.add(() -> TurnCounter.stopCounting(counter));
    return cleanups;
  }

  /**
   * Removes all counters
   *
   * @return Returns whatever counters were present before
   */
  public static Cleanups withoutCounters() {
    var cleanups = new Cleanups(withProperty("relayCounters", ""));
    TurnCounter.clearCounters();
    cleanups.add(TurnCounter::loadCounters);
    return cleanups;
  }

  /**
   * Sets the player's number of turns played
   *
   * @param turnsPlayed Turns to have played
   * @return Restores the old version
   */
  public static Cleanups withTurnsPlayed(final int turnsPlayed) {
    var old = KoLCharacter.getTurnsPlayed();
    KoLCharacter.setTurnsPlayed(turnsPlayed);
    return new Cleanups(() -> KoLCharacter.setTurnsPlayed(old));
  }

  /**
   * Sets the player's limit mode
   *
   * @param limitMode Desired limit mode
   * @return Resets limit mode to previous value
   */
  public static Cleanups withLimitMode(final String limitMode) {
    var old = KoLCharacter.getLimitMode();
    KoLCharacter.setLimitMode(limitMode);
    return new Cleanups(() -> KoLCharacter.setLimitMode(old));
  }

  /**
   * Saves preferences to a file.
   *
   * @return Stops saving preferences
   */
  public static Cleanups withSavePreferencesToFile() {
    Preferences.saveSettingsToFile = true;
    return new Cleanups(() -> Preferences.saveSettingsToFile = false);
  }

  /**
   * Sets the adventures spent in a particular location
   *
   * @param location The name of the location, as a string
   * @param adventuresSpent The number of adventures spent to set
   * @return Returns adventures spent to previous value
   */
  public static Cleanups withAdventuresSpent(final String location, final int adventuresSpent) {
    int old = AdventureSpentDatabase.getTurns(location);
    AdventureSpentDatabase.setTurns(location, adventuresSpent);
    return new Cleanups(() -> AdventureSpentDatabase.setTurns(location, old));
  }

  /**
   * Sets the adventures spent in a particular location
   *
   * @param location The name of the location, as a KoLAdventure
   * @param adventuresSpent The number of adventures spent to set
   * @return Returns adventures spent to previous value
   */
  public static Cleanups withAdventuresSpent(
      final KoLAdventure location, final int adventuresSpent) {
    int old = AdventureSpentDatabase.getTurns(location);
    AdventureSpentDatabase.setTurns(location, adventuresSpent);
    return new Cleanups(() -> AdventureSpentDatabase.setTurns(location, old));
  }

  /**
   * Sets the adventures spent in a particular location
   *
   * @param location The name of the location, as an integer snarfblat
   * @param adventuresSpent The number of adventures spent to set
   * @return Returns adventures spent to previous value
   */
  public static Cleanups withAdventuresSpent(final int location, final int adventuresSpent) {
    return withAdventuresSpent(AdventureDatabase.getAdventure(location), adventuresSpent);
  }

  /**
   * Sets the value of an adventure
   *
   * @param value The value in meat
   * @return Returns value to previous value
   */
  public static Cleanups withValueOfAdventure(final int value) {
    var cleanups = withProperty("valueOfAdventure", value);
    // changing the value of an adventure changes the cost of creating an item
    ConcoctionDatabase.refreshConcoctions();
    cleanups.add(ConcoctionDatabase::refreshConcoctions);
    return cleanups;
  }

  public static Cleanups withConcoctionRefresh() {
    ConcoctionDatabase.refreshConcoctions();
    return new Cleanups(new OrderedRunnable(ConcoctionDatabase::refreshConcoctions, 10));
  }

  public static Cleanups withAdjustmentsRecalculated() {
    KoLCharacter.recalculateAdjustments();
    return new Cleanups(new OrderedRunnable(KoLCharacter::recalculateAdjustments, 10));
  }

  public static Cleanups withNPCStoreReset() {
    NPCStoreDatabase.reset();

    return new Cleanups(NPCStoreDatabase::reset);
  }

  public static Cleanups withOverrideModifiers(ModifierType type, String key, String value) {
    ModifierDatabase.overrideModifier(type, key, value);
    return new Cleanups(() -> ModifierDatabase.overrideRemoveModifier(type, key));
  }

  public static Cleanups withOverrideModifiers(ModifierType type, int key, String value) {
    ModifierDatabase.overrideModifier(type, key, value);
    return new Cleanups(() -> ModifierDatabase.overrideRemoveModifier(type, key));
  }

  public static Cleanups withHermitReset() {
    HermitRequest.initialize();

    return new Cleanups(HermitRequest::initialize);
  }

  /**
   * Sets the current daycount
   *
   * @param currentDays The current daycount to use
   * @return Returns value to previous value
   */
  public static Cleanups withDaycount(final int currentDays) {
    var oldDays = KoLCharacter.getCurrentDays();
    KoLCharacter.setCurrentDays(currentDays);
    return new Cleanups(() -> KoLCharacter.setCurrentDays(oldDays));
  }

  /**
   * Sets the next adventure location
   *
   * @param adventure The location to consider as the next adventure
   * @return Returns value to previous value
   */
  public static Cleanups withNextAdventure(final String adventure) {
    var cleanups = new Cleanups(withProperty("nextAdventure"));
    KoLAdventure.setNextAdventure(adventure);
    return cleanups;
  }

  /**
   * Sets the TopMenuStyle
   *
   * @param style The TopMenuStyle from GenericRequest
   * @return Returns value to previous value
   */
  public static Cleanups withTopMenuStyle(final TopMenuStyle style) {
    var oldStyle = GenericRequest.topMenuStyle;
    GenericRequest.topMenuStyle = style;
    return new Cleanups(
        () -> {
          GenericRequest.topMenuStyle = oldStyle;
        });
  }

  /**
   * Sets the user id
   *
   * @param id The user id
   * @return Returns value to previous value
   */
  public static Cleanups withUserId(final int id) {
    var oldId = KoLCharacter.getUserId();
    KoLCharacter.setUserId(id);
    return new Cleanups(
        () -> {
          KoLCharacter.setUserId(oldId);
        });
  }

  /**
   * Sets the "banishedMonsters" property
   *
   * @param contents The user id
   * @return Returns value to previous value
   */
  public static Cleanups withBanishedMonsters(String contents) {
    return withProperty("banishedMonsters", contents);
  }

  /**
   * Sets the "banishedPhyla" property
   *
   * @param contents The user id
   * @return Returns value to previous value
   */
  public static Cleanups withBanishedPhyla(String contents) {
    return withProperty("banishedPhyla", contents);
  }

  /**
   * Sets the "trackedMonsters" property
   *
   * @param contents The user id
   * @return Returns value to previous value
   */
  public static Cleanups withTrackedMonsters(String contents) {
    return withProperty("trackedMonsters", contents);
  }

  /**
   * Sets the "trackedPhyla" property
   *
   * @param contents The user id
   * @return Returns value to previous value
   */
  public static Cleanups withTrackedPhyla(String contents) {
    return withProperty("trackedPhyla", contents);
  }

  public static Cleanups withDisabledCoinmaster(CoinmasterData data) {
    data.setDisabled(true);
    return new Cleanups(() -> data.setDisabled(false));
  }

  public static Cleanups withZonelessCoinmaster(CoinmasterData data) {
    String zone = data.getZone();
    data.inZone(null).getRootZone();
    return new Cleanups(() -> data.inZone(zone));
  }

  public static Cleanups withoutCoinmasterBuyItem(CoinmasterData data, AdventureResult item) {
    List<AdventureResult> buyItems = data.getBuyItems();
    if (!buyItems.contains(item)) {
      return new Cleanups();
    }

    List<AdventureResult> newBuyItems = CoinmastersDatabase.getNewList();
    newBuyItems.addAll(buyItems);
    newBuyItems.remove(item);
    data.withBuyItems(newBuyItems);

    CoinMasterPurchaseRequest request = CoinmastersDatabase.findPurchaseRequest(item);
    if (request != null) {
      CoinmastersDatabase.removePurchaseRequest(item);
    }

    var rows = data.getRows();
    Integer itemId = item.getItemId();
    Integer row = rows.get(itemId);
    ShopRowData shopRowData = (row != null) ? ShopRowDatabase.removeShopRowData(row) : null;
    if (row != null) {
      rows.remove(itemId);
    }

    return new Cleanups(
        () -> {
          // Restore original list
          data.withBuyItems(buyItems);
          if (row != null) {
            rows.put(itemId, row);
            ShopRowDatabase.putShopRowData(row, shopRowData);
          }
          if (request != null) {
            CoinmastersDatabase.addPurchaseRequest(item, request);
          }
        });
  }

  public static Cleanups withoutCoinmasterSellItem(CoinmasterData data, AdventureResult item) {
    List<AdventureResult> sellItems = data.getSellItems();
    if (!sellItems.contains(item)) {
      return new Cleanups();
    }

    List<AdventureResult> newSellItems = CoinmastersDatabase.getNewList();
    newSellItems.addAll(sellItems);
    newSellItems.remove(item);
    data.withSellItems(newSellItems);

    var rows = data.getRows();
    Integer itemId = item.getItemId();
    Integer row = rows.get(itemId);
    ShopRowData shopRowData = (row != null) ? ShopRowDatabase.removeShopRowData(row) : null;
    if (row != null) {
      rows.remove(itemId);
    }

    return new Cleanups(
        () -> {
          // Restore original list
          data.withSellItems(sellItems);
          if (row != null) {
            rows.put(itemId, row);
            ShopRowDatabase.putShopRowData(row, shopRowData);
          }
        });
  }

  /**
   * Copies the source file from root/provided_data to root/data giving it the destination name.
   * Deletes the copied file as part of cleanup. No error checking, specifically that the source
   * file is present or that the destination file was written successfully.
   *
   * @param sourceName the name of the source file in root/provided_data
   * @param destinationName the name of the destination file in root/data.
   * @return deletes the destination file
   */
  public static Cleanups withDataFile(String sourceName, String destinationName) {
    File sourceFile = new File(KoLConstants.ROOT_LOCATION + "/provided_data", sourceName);
    File destinationFile = new File(KoLConstants.DATA_LOCATION, destinationName);
    try {
      Files.copy(sourceFile.toPath(), destinationFile.toPath());
    } catch (FileAlreadyExistsException e) {
      // Do nothing.  No message needed.
    } catch (IOException e) {
      System.out.println(e + " while copying " + sourceName + " to " + destinationName + ".");
    }
    return new Cleanups(
        () -> {
          destinationFile.delete();
        });
  }

  /**
   * Copies the source file from root/provided_data to root/data giving it the same name. Deletes
   * the copied file as part of cleanup. No error checking, specifically that the source file is
   * present or that the destination file was written successfully.
   *
   * @param sourceName the name of the source file in root/provided_data and destination in data
   * @return deletes the destination file
   */
  public static Cleanups withDataFile(String sourceName) {
    return withDataFile(sourceName, sourceName);
  }

  /**
   * Tells FightRequest to use PokeFam parsing
   *
   * @return stops using PokeFam parsing
   */
  public static Cleanups withFightRequestPokefam() {
    FightRequest.pokefam = true;
    return new Cleanups(() -> FightRequest.pokefam = false);
  }

  /**
   * Sets the current chat channel
   *
   * @param channel Channel to use
   * @return set the channel to the previous value
   */
  public static Cleanups withChatChannel(final String channel) {
    var old = ChatManager.getCurrentChannel();
    ChatManager.processChannelEnable(new EnableMessage(channel, true));
    return new Cleanups(
        () -> {
          if (old == null) {
            ChatManager.dispose();
          } else {
            ChatManager.processChannelEnable(new EnableMessage(old, true));
          }
        });
  }

  /**
   * Add a goal to fulfill via adventuring
   *
   * @param goal Goal to add
   * @return clears added goal
   */
  public static Cleanups withGoal(final AdventureResult goal) {
    var goals = GoalManager.getGoals();
    GoalManager.addGoal(goal);
    return new Cleanups(
        () -> {
          GoalManager.addGoal(goal.getNegation());
        });
  }

  private static void updateMallResults(int itemId, List<PurchaseRequest> results) {
    MallPriceManager.saveMallSearch(itemId, results);
    MallPriceManager.updateMallPrice(ItemPool.get(itemId), results);
  }

  /**
   * Mall price for an item
   *
   * @param itemId Id of item
   * @param price Price to return
   * @return Totally reset price cache
   */
  public static Cleanups withMallPrice(final int itemId, final int price) {
    MallPriceDatabase.savePricesToFile = false;
    List<PurchaseRequest> results =
        List.of(new MallPurchaseRequest(itemId, 100, 1, "Test Shop", price, 100, true));
    updateMallResults(itemId, results);

    return new Cleanups(
        () -> {
          MallPriceManager.reset();
          MallPriceDatabase.savePricesToFile = true;
        });
  }

  /**
   * Load NPC price for an item into cache
   *
   * @param itemId Id of item
   * @return Totally reset price cache
   */
  public static Cleanups withNpcPrice(final int itemId) {
    List<PurchaseRequest> results =
        List.of(Objects.requireNonNull(NPCStoreDatabase.getPurchaseRequest(itemId)));
    updateMallResults(itemId, results);

    return new Cleanups(
        () -> {
          MallPriceManager.reset();
          MallPriceDatabase.savePricesToFile = true;
        });
  }

  /**
   * Set the GuildStore as open or closed.
   *
   * @param storeOpen - true if store is to be open, else false
   * @return - restores previous state of store
   */
  public static Cleanups withGuildStoreOpen(final boolean storeOpen) {
    boolean oldKnown = KoLCharacter.getGuildStoreOpen();
    KoLCharacter.setGuildStoreOpen(storeOpen);
    return new Cleanups(() -> KoLCharacter.setGuildStoreOpen(oldKnown));
  }
}

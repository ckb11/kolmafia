package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;
import net.sourceforge.kolmafia.session.InventoryManager;
import net.sourceforge.kolmafia.shop.ShopRequest;

public class VendingMachineRequest extends CoinMasterRequest {
  public static final String master = "Vending Machine";
  public static final String SHOPID = "damachine";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("(\\d+) fat loot token");
  public static final AdventureResult FAT_LOOT_TOKEN = ItemPool.get(ItemPool.FAT_LOOT_TOKEN, 1);

  public static final CoinmasterData VENDING_MACHINE =
      new CoinmasterData(master, "vendingmachine", VendingMachineRequest.class)
          .withToken("fat loot token")
          .withTokenTest("no fat loot tokens")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(FAT_LOOT_TOKEN)
          .withShopRowFields(master, SHOPID)
          .withCanBuyItem(VendingMachineRequest::canBuyItem)
          .withAccessible(VendingMachineRequest::accessible);

  private static Boolean canBuyItem(final Integer itemId) {
    AdventureResult item = ItemPool.get(itemId);
    return switch (itemId) {
      case ItemPool.SEWING_KIT -> InventoryManager.getCount(item) == 0;
      default -> item.getCount(VENDING_MACHINE.getBuyItems()) > 0;
    };
  }

  public VendingMachineRequest() {
    super(VENDING_MACHINE);
  }

  public VendingMachineRequest(final boolean buying, final AdventureResult[] attachments) {
    super(VENDING_MACHINE, buying, attachments);
  }

  public VendingMachineRequest(final boolean buying, final AdventureResult attachment) {
    super(VENDING_MACHINE, buying, attachment);
  }

  public VendingMachineRequest(final boolean buying, final int itemId, final int quantity) {
    super(VENDING_MACHINE, buying, itemId, quantity);
  }

  @Override
  public void processResults() {
    ShopRequest.parseResponse(this.getURLString(), this.responseText);
  }

  public static String accessible() {
    if (KoLCharacter.isKingdomOfExploathing()) {
      return "The vending machine exploded";
    }
    return null;
  }
}

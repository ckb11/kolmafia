package net.sourceforge.kolmafia.request.coinmaster.shop;

import java.util.regex.Pattern;
import net.sourceforge.kolmafia.AdventureResult;
import net.sourceforge.kolmafia.CoinmasterData;
import net.sourceforge.kolmafia.KoLCharacter;
import net.sourceforge.kolmafia.objectpool.ItemPool;
import net.sourceforge.kolmafia.request.coinmaster.CoinMasterRequest;
import net.sourceforge.kolmafia.shop.ShopRequest;

public class PlumberItemRequest extends CoinMasterRequest {
  public static final String master = "Mushroom District Item Shop";
  public static final String SHOPID = "marioitems";

  private static final Pattern TOKEN_PATTERN = Pattern.compile("([\\d,]+) coin");
  public static final AdventureResult COIN = ItemPool.get(ItemPool.COIN, 1);

  public static final CoinmasterData PLUMBER_ITEMS =
      new CoinmasterData(master, "marioitems", PlumberItemRequest.class)
          .withToken("coin")
          .withTokenTest("no coins")
          .withTokenPattern(TOKEN_PATTERN)
          .withItem(COIN)
          .withShopRowFields(master, SHOPID)
          .withAccessible(PlumberItemRequest::accessible);

  public PlumberItemRequest() {
    super(PLUMBER_ITEMS);
  }

  public PlumberItemRequest(final boolean buying, final AdventureResult[] attachments) {
    super(PLUMBER_ITEMS, buying, attachments);
  }

  @Override
  public void processResults() {
    ShopRequest.parseResponse(this.getURLString(), this.responseText);
  }

  public static String accessible() {
    if (!KoLCharacter.isPlumber()) {
      return "You are not a plumber.";
    }
    return null;
  }
}

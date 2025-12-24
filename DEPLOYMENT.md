# ARM-Guilds-Bridge Deployment Guide

## Overview

This guide explains how to deploy the ARM-Guilds integration system that enables guilds to purchase and manage shop regions through their guild vaults.

## Prerequisites

### Required Plugins
1. **LumaGuilds** v0.5.3+ (with shop permission support)
2. **Advanced Region Market** v3.5.4+
3. **WorldGuard** 7.0.9+
4. **Vault** (for economy integration)

### Server Requirements
- Paper/Spigot 1.21+
- Java 21+

## Installation Steps

### 1. Install Core Dependencies

```
plugins/
├── LumaGuilds-0.5.3.jar          # Guild system
├── AdvancedRegionMarket-*.jar    # Shop region management
├── WorldGuard-*.jar              # Region protection
└── Vault.jar                     # Economy API
```

**Download Links**:
- LumaGuilds: `D:\BadgersMC-Dev\bell-claims\build\libs\LumaGuilds-0.5.3.jar`
- ARM: https://github.com/alex9849/advanced-region-market/releases
- WorldGuard: https://dev.bukkit.org/projects/worldguard
- Vault: https://www.spigotmc.org/resources/vault.34315/

### 2. Install ARM-Guilds-Bridge

Copy the bridge plugin to your plugins folder:
```
plugins/ARM-Guilds-Bridge-1.0.0.jar
```

**Location**: `D:\BadgersMC-Dev\ARM-Guilds-Bridge\build\libs\ARM-Guilds-Bridge-1.0.0.jar`

### 3. Configure LumaGuilds

Ensure your `plugins/LumaGuilds/config.yml` has the vault system configured:

```yaml
vault:
  # VIRTUAL = economy plugin via Vault
  # PHYSICAL = RAW_GOLD items in chest
  # BOTH = try virtual first, fallback to physical
  bank_mode: PHYSICAL  # or VIRTUAL or BOTH
```

### 4. Configure ARM-Guilds-Bridge

Edit `plugins/ARM-Guilds-Bridge/config.yml`:

```yaml
storage:
  type: database
  shared-connection: true

shop-purchase:
  # Permission required to buy shop for guild
  required-permission: MANAGE_GUILD_SETTINGS  # Can also use ACCESS_SHOP_CHESTS, etc.

  # Notify all online guild members when shop purchased
  notify-guild: true

  # Minimum balance guild must have AFTER purchase
  minimum-balance-after: 1000.0

  # Maximum shops per guild (0 = unlimited)
  max-shops-per-guild: 0

payment:
  # Allow player personal funds as fallback (not recommended)
  allow-player-fallback: false

enemy-blocking:
  # Enable enemy guild blocking from shop regions
  enabled: true
  block-entry: true
  message: "§cCannot access - guilds at war!"
  block-truce: false  # Also block truce guilds

audit:
  log-transactions: true
  retention-days: 30

debug:
  enabled: false
```

### 5. Set Up Guild Permissions

In-game, use `/guild rank edit <rank>` to configure shop permissions:

**New Permissions Available**:
- `ACCESS_SHOP_CHESTS` - Open chests in guild-owned shop regions
- `EDIT_SHOP_STOCK` - Modify inventory in shop chests
- `MODIFY_SHOP_PRICES` - Change prices on shop signs

**Recommended Setup**:
- **Owner** - All permissions
- **Admin** - ACCESS_SHOP_CHESTS, EDIT_SHOP_STOCK, MODIFY_SHOP_PRICES
- **Member** - ACCESS_SHOP_CHESTS only (can buy from shops)
- **Guest** - No shop permissions

### 6. Configure Advanced Region Market

Set up ARM shop regions as normal. The bridge plugin will automatically intercept purchases when a guild member buys.

**Important ARM Settings**:
- Ensure regions are set as "for rent" or "for sale"
- Set appropriate prices per period
- Configure sign locations for shops

## How It Works

### Purchase Flow

1. Guild member clicks ARM shop sign
2. ARM fires `PreBuyEvent`
3. Bridge plugin intercepts:
   - Checks if player is in a guild
   - Verifies player has `MANAGE_GUILD_SETTINGS` permission
   - Checks guild shop limit
   - **Withdraws price from guild vault** (using `withdrawForShopPurchase()`)
   - Tells ARM to skip its payment system (`setNoMoneyTransfer()`)
   - **Sets guild UUID as landlord** - routes ALL future income to guild
   - Registers region in bridge database
4. Purchase completes
5. Guild members notified

### Income Flow

When players buy from shop signs:
1. ARM processes the sale
2. ARM deposits funds to landlord's economy account
3. Since landlord = guild UUID, funds go to guild's virtual balance

**Note**: This requires Vault economy integration. If using PHYSICAL mode, income routing needs additional implementation (Phase 6).

### Permission Checks

- `ACCESS_SHOP_CHESTS` - Required to open chests in guild shops
- `EDIT_SHOP_STOCK` - Required to modify chest contents
- `MODIFY_SHOP_PRICES` - Required to change sign prices

## Database

The bridge plugin creates `plugins/ARM-Guilds-Bridge/guild_shops.db` with:

**Tables**:
- `arm_guild_shops` - Guild-region mappings
- `arm_shop_transactions` - Transaction audit log

**Fields**:
- region_id, world_name
- guild_id (UUID)
- purchase_price
- purchased_at (timestamp)

## Testing

### Test Guild Shop Purchase

1. Create a guild: `/guild create TestGuild`
2. Add RAW_GOLD to guild vault (if using PHYSICAL mode)
3. Set up an ARM shop region
4. As guild owner, click the ARM shop sign
5. Verify:
   - ✓ Purchase succeeds
   - ✓ Funds withdrawn from guild vault
   - ✓ Region registered to guild
   - ✓ Guild members notified
   - ✓ `/guild vault` shows reduced balance

### Test Permission System

1. Create a rank: `/guild rank create Shopkeeper`
2. Give shop permissions
3. Assign member to rank
4. Test that they can:
   - ✓ Open chests in guild shop
   - ✓ Edit stock
   - ✓ Change prices

## Troubleshooting

### "Failed to withdraw from guild vault"
- Check guild has sufficient balance
- Verify `minimum-balance-after` in config
- Check vault is in correct mode (VIRTUAL vs PHYSICAL)
- Ensure RAW_GOLD items exist in vault (if PHYSICAL)

### "You don't have permission to purchase"
- Player needs `MANAGE_GUILD_SETTINGS` permission in guild
- Check rank configuration: `/guild rank edit <rank>`

### "Failed to register shop region"
- Check database connectivity
- View logs in `plugins/ARM-Guilds-Bridge/` for errors
- Ensure write permissions on plugin folder

### Shop income not going to guild
- **Current Status**: Income routing requires Vault economy integration
- **Workaround**: Manually deposit to guild vault
- **Future**: Phase 6 will implement direct deposit to PHYSICAL vaults

## What's Implemented

✅ **Phase 1**: LumaGuilds shop permissions
✅ **Phase 2**: Bridge plugin structure
✅ **Phase 3**: Purchase integration via PreBuyEvent

## What's Next

⏸️ **Phase 4**: Permission enforcement listeners
⏸️ **Phase 5**: Enemy blocking via WorldGuard flags
⏸️ **Phase 6**: Shop income deposit to PHYSICAL vaults

## Support

For issues or questions:
- Check server logs: `logs/latest.log`
- Enable debug mode in config
- Review transaction history in database

## Credits

Developed using EARS (Easy Approach to Requirements Syntax) methodology.
Integration between LumaGuilds and Advanced Region Market by alex9849.

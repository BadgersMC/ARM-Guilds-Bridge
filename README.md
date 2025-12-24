# ARM-Guilds-Bridge

Integration bridge between Advanced Region Market (ARM) and LumaGuilds for guild-owned shop regions.

## Overview

This plugin enables guilds to purchase and manage shop regions through the guild vault system, with permission-based access control and relation-based blocking.

## Features

- **Guild Shop Ownership**: Guild members purchase shop regions on behalf of guilds
- **Payment Routing**: All shop proceeds route to guild vault (virtual or physical currency)
- **Permission-Based Access**: Guild permissions control chest access, stock editing, and price modification
- **Relation Enforcement**: Enemy guilds blocked from accessing shops via WorldGuard flags
- **Audit Logging**: All transactions logged to audit system

## Dependencies

- **LumaGuilds**: Guild management and vault system
- **Advanced Region Market**: Shop region management (will be integrated in next phase)
- **WorldGuard**: Region protection and flag management
- **Vault**: Economy API support

## Project Structure

```
ARM-Guilds-Bridge/
├── src/main/java/net/lumalyte/armbridge/
│   ├── ARMGuildsBridge.java          # Main plugin class
│   ├── listeners/                     # Event listeners (Phase 3)
│   │   ├── RegionPurchaseListener     # PreBuyEvent hook
│   │   ├── ChestAccessListener        # Chest permission checks
│   │   ├── ShopEntryListener          # Enemy blocking
│   │   └── SignInteractionListener    # Price edit permission
│   ├── services/                      # Business logic
│   │   ├── GuildShopService           # Register/get guild-region mappings
│   │   ├── PaymentRoutingService      # Vault withdrawal/deposit
│   │   └── RelationFlagService        # WorldGuard flag updates
│   ├── storage/                       # Data persistence
│   │   ├── GuildRegionRepository      # Database interface
│   │   ├── GuildRegionRepositoryImpl  # SQLite implementation
│   │   ├── ShopRegionInfo             # Data class for shop regions
│   │   └── ShopTransaction            # Data class for transactions
│   └── util/                          # Utilities (Phase 3)
│       └── VaultCurrencyAdapter       # Physical/virtual conversion
└── src/main/resources/
    ├── plugin.yml                     # Plugin metadata
    └── config.yml                     # Configuration

```

## Implementation Status

### Phase 1: LumaGuilds Foundation ✅
- ✅ Added 3 RankPermission enums (ACCESS_SHOP_CHESTS, EDIT_SHOP_STOCK, MODIFY_SHOP_PRICES)
- ✅ Added withdrawForShopPurchase() to GuildVaultService interface
- ✅ Implemented withdrawForShopPurchase() in GuildVaultServiceBukkit
- ✅ Added permission toggles to rank editor UI

### Phase 2: Bridge Plugin Core ⏳
- ✅ Created plugin structure with Gradle build
- ✅ Implemented GuildRegionRepository (database schema + CRUD)
- ✅ Created service interfaces (GuildShopService, PaymentRoutingService, RelationFlagService)
- ⏸️ Implement service implementations
- ⏸️ Initialize services in main plugin class

### Phase 3: Purchase Integration
- ⏸️ Implement RegionPurchaseListener (PreBuyEvent hook)
- ⏸️ Add VaultCurrencyAdapter for physical/virtual conversion
- ⏸️ Test guild purchase flow with vault withdrawal
- ⏸️ Verify landlord routing to guild vault

### Phase 4: Permission Enforcement
- ⏸️ Implement ChestAccessListener (ACCESS_SHOP_CHESTS)
- ⏸️ Implement inventory modification checks (EDIT_SHOP_STOCK)
- ⏸️ Implement SignInteractionListener (MODIFY_SHOP_PRICES)

### Phase 5: Relation Blocking
- ⏸️ Implement ShopEntryListener (enemy movement blocking)
- ⏸️ Implement RelationFlagService (WorldGuard flag updates)
- ⏸️ Hook into LumaGuilds relation change events
- ⏸️ Test enemy→ally→enemy transitions

## Configuration

See `config.yml` for detailed configuration options.

Key settings:
- `storage.type`: Database storage type (database or yaml)
- `shop-purchase.required-permission`: Permission needed to buy for guild
- `enemy-blocking.enabled`: Enable enemy guild blocking
- `audit.log-transactions`: Log all shop transactions

## Building

```bash
./gradlew shadowJar
```

Output JAR: `build/libs/ARM-Guilds-Bridge-1.0.0.jar`

## Installation

1. Build LumaGuilds first and ensure it's installed
2. Build ARM-Guilds-Bridge
3. Place JAR in plugins folder
4. Configure config.yml
5. Restart server

## Technical Details

### Currency System

Server uses RAW_GOLD currency:
- RAW_GOLD_BLOCK = 9 RAW_GOLD
- No smaller denominations

Vault system supports:
- VIRTUAL mode: Economy API (Vault)
- PHYSICAL mode: Raw gold items in chest
- BOTH mode: Try virtual first, fallback to physical

### Landlord System

ARM's landlord system routes all shop payments to landlord UUID. By setting the guild UUID as landlord, all proceeds automatically go to the guild.

### WorldGuard Integration

Custom flag (BLOCKED_GUILDS_FLAG) stores blocked guild UUIDs. Updated when relations change (ally/enemy/truce).

## License

Proprietary - LumaLyte

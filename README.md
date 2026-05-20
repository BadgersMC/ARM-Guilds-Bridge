# ARM-Guilds-Bridge

Integration bridge between **Advanced Region Market (ARM)**, **ItemShops**, and **LumaGuilds** — enabling guild-owned shop regions with vault-backed payments, relation-based access control, and full audit logging.

## Features

- **Guild Shop Purchases** — guild members buy ARM regions on behalf of their guild via vault withdrawal
- **Purchase Mode Prompt** — players in guilds choose personal vs guild purchase with clickable chat UI
- **Payment Routing** — shop income deposits to guild vault (virtual or physical currency)
- **Payment Rollback** — automatic refund if region registration fails after withdrawal
- **Enemy Blocking** — 4 modes: `BAN` (full block), `WINDOW_SHOP` (view only), `ALLOW` (full access), `UPCHARGE` (price markup)
- **Permission Enforcement** — guild rank permissions control chest access, stock editing, and price modification
- **WorldGuard Integration** — custom `BLOCKED_GUILDS_FLAG` updated on relation changes
- **Guild Disband Cleanup** — automatically removes all shop regions and clears WG flags when a guild disbands
- **Audit Logging** — all transactions (purchase, removal, income, disband cleanup) logged to SQLite
- **Custom Events** — fires `GuildRegionPurchasedEvent` for external listeners (e.g. advancement plugins)

## Dependencies

| Plugin | Required | Purpose |
|--------|----------|---------|
| **LumaGuilds** | Yes | Guild management, vault system, rank permissions, relation events |
| **Advanced Region Market** | Yes | Shop region management (PreBuyEvent hook via reflection) |
| **WorldGuard** | Yes | Region protection and custom flag management |
| **Vault** | Yes | Economy API |
| **ItemShops** | Optional | Individual chest+sign shop tracking |

## Project Structure

```
ARM-Guilds-Bridge/
├── src/main/java/net/lumalyte/armbridge/
│   ├── ARMGuildsBridge.java              # Main plugin — lifecycle, DI, listener registration
│   ├── commands/
│   │   └── GuildShopCommand.java         # /guildshop command (list, mode, info)
│   ├── events/
│   │   └── GuildRegionPurchasedEvent.java # Custom event for external listeners
│   ├── listeners/
│   │   ├── RegionPurchaseListener.java   # ARM PreBuyEvent hook (reflection-based)
│   │   ├── ChestAccessListener.java      # ACCESS_SHOP_CHESTS permission check
│   │   ├── InventoryModificationListener.java # EDIT_SHOP_STOCK permission check
│   │   ├── SignInteractionListener.java  # MODIFY_SHOP_PRICES permission check
│   │   ├── ShopEntryListener.java        # Enemy movement blocking (BAN mode)
│   │   ├── ShopSignInteractionListener.java # WINDOW_SHOP mode enforcement
│   │   ├── ShopTransactionListener.java  # UPCHARGE mode price modification
│   │   ├── ShopIncomeListener.java       # PHYSICAL mode income routing
│   │   ├── RelationChangeListener.java   # WG flag updates on relation changes
│   │   └── GuildDisbandListener.java     # Shop cleanup on guild disband
│   ├── models/
│   │   └── EnemyAccessMode.java          # BAN, WINDOW_SHOP, ALLOW, UPCHARGE enum
│   ├── services/
│   │   ├── GuildShopService.java         # Shop region management interface
│   │   ├── GuildShopServiceImpl.java     # SQLite-backed implementation
│   │   ├── PaymentRoutingService.java    # Vault withdrawal/deposit interface
│   │   ├── PaymentRoutingServiceImpl.java # LumaGuilds vault integration
│   │   ├── RelationFlagService.java      # WorldGuard flag management interface
│   │   ├── RelationFlagServiceImpl.java  # WG custom flag implementation
│   │   ├── ItemShopGuildService.java     # Individual ItemShop tracking interface
│   │   ├── ItemShopGuildServiceImpl.java # SQLite-backed ItemShop tracking
│   │   ├── PurchaseMode.java             # Personal vs guild purchase prompt
│   │   └── PurchaseChoiceService.java    # Purchase choice state management
│   └── storage/
│       ├── GuildRegionRepository.java    # Database interface
│       ├── GuildRegionRepositoryImpl.java # SQLite implementation
│       ├── ShopRegionInfo.java           # Region data record
│       └── ShopTransaction.java          # Transaction audit record
└── src/main/resources/
    ├── plugin.yml
    └── config.yml
```

## Configuration

Key settings in `config.yml`:

```yaml
storage:
  type: database                    # Storage backend (database only for now)

shop-purchase:
  required-permission: MANAGE_GUILD_SETTINGS  # Guild rank permission needed to buy
  max-shops-per-guild: 0            # 0 = unlimited
  minimum-balance-after: 1000.0     # Warning threshold after withdrawal
  notify-guild: true                # Notify online members on purchase

enemy-blocking:
  enabled: true
  default-mode: BAN                 # Default mode for new shop regions
  default-upcharge-percentage: 50.0 # Default upcharge % for UPCHARGE mode
```

## Enemy Access Modes

| Mode | Behavior |
|------|----------|
| `BAN` | Enemy guild members completely blocked from entering shop region |
| `WINDOW_SHOP` | Enemies can view shops but cannot purchase |
| `ALLOW` | Full access for all players |
| `UPCHARGE` | Enemies pay an additional percentage on all purchases |

## Custom Events

### `GuildRegionPurchasedEvent`

Fired after a guild successfully purchases an ARM region. Used by `CustomAdvancements` to grant `GUILD_REGION_PURCHASED` advancement progress.

| Field | Type | Description |
|-------|------|-------------|
| `regionId` | `String` | ARM region identifier |
| `worldName` | `String` | World containing the region |
| `guildId` | `UUID` | Purchasing guild |
| `buyerId` | `UUID` | Player who initiated the purchase |
| `purchasePrice` | `double` | Amount withdrawn from guild vault |

## Building

```bash
./gradlew clean shadowJar
```

Output: `build/libs/ARM-Guilds-Bridge-1.0.0.jar`

**Build dependencies** (must exist as sibling project JARs):
- `../bell-claims/build/libs/LumaGuilds-2.0.0.jar`
- `../ItemShops/build/libs/ItemShops-1.1.1.jar`

## Installation

1. Install LumaGuilds, ARM, WorldGuard, and Vault on the server
2. Build: `./gradlew clean shadowJar`
3. Place `ARM-Guilds-Bridge-1.0.0.jar` in `plugins/`
4. Start server — config.yml generates on first run
5. Configure settings and restart

## Technical Details

### ARM Integration (Reflection)

ARM's `PreBuyEvent` is hooked via reflection and dynamic event registration to avoid classloader conflicts. All reflection objects are cached at startup for zero-overhead event handling.

### Currency System

Server uses RAW_GOLD currency:
- `RAW_GOLD_BLOCK` = 9 `RAW_GOLD`
- Vault system supports VIRTUAL (economy API), PHYSICAL (raw gold in chest), or BOTH modes

### Landlord Routing

ARM's landlord system routes shop payments to a landlord UUID. Setting the guild UUID as landlord automatically directs all proceeds to the guild.

## License

Proprietary — LumaLyte

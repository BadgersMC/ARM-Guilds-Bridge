package net.lumalyte.armbridge.services;

import net.lumalyte.lg.application.services.WithdrawalInfo;

import java.util.UUID;

/**
 * Service for routing shop payments through guild vaults
 */
public interface PaymentRoutingService {

    /**
     * Withdraw funds from guild vault for shop purchase
     *
     * @param guildId Guild UUID
     * @param amount Amount to withdraw
     * @param reason Transaction description
     * @return WithdrawalResult with success/failure status
     */
    WithdrawalResult withdrawFromGuild(UUID guildId, double amount, String reason);

    /**
     * Deposit funds to guild vault from shop income
     *
     * @param guildId Guild UUID
     * @param amount Amount to deposit
     * @param reason Transaction description
     * @return true if successful
     */
    boolean depositToGuild(UUID guildId, double amount, String reason);

    /**
     * Result wrapper for withdrawal operations
     */
    class WithdrawalResult {
        private final boolean success;
        private final String error;
        private final WithdrawalInfo info;

        private WithdrawalResult(boolean success, String error, WithdrawalInfo info) {
            this.success = success;
            this.error = error;
            this.info = info;
        }

        public static WithdrawalResult success(WithdrawalInfo info) {
            return new WithdrawalResult(true, null, info);
        }

        public static WithdrawalResult failure(String error) {
            return new WithdrawalResult(false, error, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }

        public WithdrawalInfo getInfo() {
            return info;
        }
    }
}

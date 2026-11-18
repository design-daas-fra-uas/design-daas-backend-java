package de.frauas.design.backend.shared.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Defers work until the current transaction has committed.
 *
 * <p>Used for slow or irreversible side effects such as e-mails: they never keep database
 * locks held longer than necessary, and they are not sent if the transaction rolls back.</p>
 */
public final class AfterCommit {

    private AfterCommit() {}

    /**
     * Runs {@code action} once the current transaction commits. Falls back to running
     * immediately if no transaction is active (e.g. during unit tests).
     *
     * @param action the work to defer until after commit
     */
    public static void run(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

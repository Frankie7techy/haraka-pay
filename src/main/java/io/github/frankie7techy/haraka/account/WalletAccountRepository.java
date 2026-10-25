package io.github.frankie7techy.haraka.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {

    /**
     * Locks the given accounts in a deterministic order (ascending id) with
     * {@code SELECT ... FOR UPDATE}. Two concurrent transactions touching the same pair
     * always acquire locks in the same global order, which structurally prevents the
     * classic A->B / B->A transfer deadlock.
     */
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from WalletAccount a
            where a.id in :ids
            order by a.id
            """)
    List<WalletAccount> findAllForUpdate(@Param("ids") Collection<Long> ids);

    List<WalletAccount> findByUserIdOrderByAccountNumber(Long userId);

    Optional<WalletAccount> findFirstByUserId(Long userId);

    Optional<WalletAccount> findByAccountNumber(String accountNumber);
}

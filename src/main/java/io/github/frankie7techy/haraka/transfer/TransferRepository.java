package io.github.frankie7techy.haraka.transfer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findById(Long id);

    List<Transfer> findBySourceAccountId(Long sourceAccountId);

    List<Transfer> findByTargetAccountId(Long targetAccountId);
}

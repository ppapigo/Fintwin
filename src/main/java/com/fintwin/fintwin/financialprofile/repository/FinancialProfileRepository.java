package com.fintwin.fintwin.financialprofile.repository;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface FinancialProfileRepository extends JpaRepository<FinancialProfile, Long> {
    Optional<FinancialProfile> findFirstByUserIdOrderByVersionDesc(Long userId);
    Optional<FinancialProfile> findByIdAndUserId(Long id, Long userId);
    List<FinancialProfile> findAllByUserIdOrderByVersionDesc(Long userId);
    boolean existsByUserId(Long userId);
}

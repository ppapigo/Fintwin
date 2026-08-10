package com.fintwin.fintwin.financialprofile.service;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileUpdateRequest;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.global.error.ConflictException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FinancialProfileService {
    private final FinancialProfileRepository financialProfileRepository;
    private final UserRepository userRepository;

    public FinancialProfileService(FinancialProfileRepository financialProfileRepository, UserRepository userRepository) {
        this.financialProfileRepository = financialProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public FinancialProfileResponse create(Long userId, FinancialProfileCreateRequest request) {
        User user = lockUser(userId);
        if (financialProfileRepository.existsByUserId(userId)) {
            throw new ConflictException("Financial profile already exists");
        }
        return saveSnapshot(FinancialProfile.createInitial(user, request));
    }

    @Transactional
    public FinancialProfileResponse updateCurrent(Long userId, FinancialProfileUpdateRequest request) {
        lockUser(userId);
        FinancialProfile current = findCurrent(userId);
        return saveSnapshot(FinancialProfile.createNext(current, request));
    }

    public FinancialProfileResponse getCurrent(Long userId) {
        return FinancialProfileResponse.from(findCurrent(userId));
    }

    public List<FinancialProfileResponse> getHistory(Long userId) {
        return financialProfileRepository.findAllByUserIdOrderByVersionDesc(userId).stream()
                .map(FinancialProfileResponse::from)
                .toList();
    }

    public FinancialProfileResponse getSnapshot(Long userId, Long profileId) {
        return financialProfileRepository.findByIdAndUserId(profileId, userId)
                .map(FinancialProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Financial profile not found"));
    }

    private User lockUser(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private FinancialProfile findCurrent(Long userId) {
        return financialProfileRepository.findFirstByUserIdOrderByVersionDesc(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Financial profile not found"));
    }

    private FinancialProfileResponse saveSnapshot(FinancialProfile profile) {
        try {
            return FinancialProfileResponse.from(financialProfileRepository.saveAndFlush(profile));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Financial profile version conflict");
        }
    }
}

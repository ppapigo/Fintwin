package com.fintwin.fintwin.financialprofile.service;

import com.fintwin.fintwin.financialprofile.domain.FinancialProfile;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileCreateRequest;
import com.fintwin.fintwin.financialprofile.dto.FinancialProfileResponse;
import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.global.error.ConflictException;
import com.fintwin.fintwin.global.error.ResourceNotFoundException;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (financialProfileRepository.existsByUserId(userId)) {
            throw new ConflictException("Financial profile already exists");
        }
        return FinancialProfileResponse.from(financialProfileRepository.save(FinancialProfile.create(user, request)));
    }

    public FinancialProfileResponse getByUserId(Long userId) {
        return financialProfileRepository.findByUserId(userId)
                .map(FinancialProfileResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Financial profile not found"));
    }
}

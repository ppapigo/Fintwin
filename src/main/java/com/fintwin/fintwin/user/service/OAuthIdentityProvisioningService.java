package com.fintwin.fintwin.user.service;

import com.fintwin.fintwin.user.domain.OAuthIdentity;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import com.fintwin.fintwin.user.domain.User;
import com.fintwin.fintwin.user.repository.OAuthIdentityRepository;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;

@Service
public final class OAuthIdentityProvisioningService {
    private final OAuthIdentityRepository identityRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    public OAuthIdentityProvisioningService(OAuthIdentityRepository identityRepository,
                                            UserRepository userRepository,
                                            PlatformTransactionManager transactionManager) {
        this.identityRepository = identityRepository;
        this.userRepository = userRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public Long resolveOrCreate(OAuthProvider provider, String providerSubject) {
        Objects.requireNonNull(provider);
        Objects.requireNonNull(providerSubject);
        try {
            return requireResult(transactionTemplate.execute(status -> resolveOrCreateInTransaction(
                    provider, providerSubject)));
        } catch (DataIntegrityViolationException collision) {
            Long existingUserId = transactionTemplate.execute(status -> identityRepository
                    .findByProviderAndProviderSubject(provider, providerSubject)
                    .map(identity -> {
                        identity.recordLogin();
                        return identity.getUser().getId();
                    })
                    .orElse(null));
            if (existingUserId != null) {
                return existingUserId;
            }
            throw collision;
        }
    }

    private Long resolveOrCreateInTransaction(OAuthProvider provider, String providerSubject) {
        return identityRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(identity -> {
                    identity.recordLogin();
                    return identity.getUser().getId();
                })
                .orElseGet(() -> createIdentity(provider, providerSubject));
    }

    private Long createIdentity(OAuthProvider provider, String providerSubject) {
        User user = userRepository.saveAndFlush(User.create());
        identityRepository.saveAndFlush(OAuthIdentity.create(user, provider, providerSubject));
        return user.getId();
    }

    private Long requireResult(Long result) {
        return Objects.requireNonNull(result, "OAuth identity transaction returned no user");
    }
}

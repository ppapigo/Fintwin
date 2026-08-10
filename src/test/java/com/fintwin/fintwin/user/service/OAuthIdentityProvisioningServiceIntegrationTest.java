package com.fintwin.fintwin.user.service;

import com.fintwin.fintwin.financialprofile.repository.FinancialProfileRepository;
import com.fintwin.fintwin.user.domain.OAuthProvider;
import com.fintwin.fintwin.user.repository.OAuthIdentityRepository;
import com.fintwin.fintwin.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OAuthIdentityProvisioningServiceIntegrationTest {
    @Autowired
    private OAuthIdentityProvisioningService service;
    @Autowired
    private OAuthIdentityRepository identityRepository;
    @Autowired
    private FinancialProfileRepository profileRepository;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        identityRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void cleanAfter() {
        identityRepository.deleteAll();
        profileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void firstLoginCreatesUserAndReloginReturnsExistingUserWithoutProfile() {
        Long first = service.resolveOrCreate(OAuthProvider.GOOGLE, "google-subject-1");
        Long second = service.resolveOrCreate(OAuthProvider.GOOGLE, "google-subject-1");

        assertThat(second).isEqualTo(first);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(identityRepository.count()).isEqualTo(1);
        assertThat(profileRepository.count()).isZero();
        assertThat(identityRepository.findByProviderAndProviderSubject(
                OAuthProvider.GOOGLE, "google-subject-1")).get()
                .satisfies(identity -> {
                    assertThat(identity.getCreatedAt()).isNotNull();
                    assertThat(identity.getLastLoginAt()).isNotNull();
                    assertThat(identity.toString()).doesNotContain("google-subject-1");
                });
    }

    @Test
    void distinctAccountsAndProvidersAreNeverMergedBySubjectOrEmail() {
        Long googleOne = service.resolveOrCreate(OAuthProvider.GOOGLE, "subject-1");
        Long googleTwo = service.resolveOrCreate(OAuthProvider.GOOGLE, "subject-2");
        Long kakaoSameSubject = service.resolveOrCreate(OAuthProvider.KAKAO, "subject-1");

        assertThat(googleOne).isNotEqualTo(googleTwo).isNotEqualTo(kakaoSameSubject);
        assertThat(googleTwo).isNotEqualTo(kakaoSameSubject);
        assertThat(userRepository.count()).isEqualTo(3);
        assertThat(identityRepository.count()).isEqualTo(3);
    }

    @Test
    void sameProviderSubjectsRemainCaseSensitive() {
        Long upperCase = service.resolveOrCreate(OAuthProvider.GOOGLE, "Case-Sensitive-Subject");
        Long lowerCase = service.resolveOrCreate(OAuthProvider.GOOGLE, "case-sensitive-subject");

        assertThat(upperCase).isNotEqualTo(lowerCase);
        assertThat(userRepository.count()).isEqualTo(2);
        assertThat(identityRepository.count()).isEqualTo(2);
    }

    @Test
    void concurrentFirstLoginCreatesExactlyOneIdentityAndOneUser() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> loginAfterBarrier(ready, start));
            var second = executor.submit(() -> loginAfterBarrier(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            Long firstUserId = first.get(10, TimeUnit.SECONDS);
            Long secondUserId = second.get(10, TimeUnit.SECONDS);

            assertThat(secondUserId).isEqualTo(firstUserId);
            assertThat(identityRepository.count()).isEqualTo(1);
            assertThat(userRepository.count()).isEqualTo(1);
        }
    }

    private Long loginAfterBarrier(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent login test did not start");
        }
        return service.resolveOrCreate(OAuthProvider.GOOGLE, "concurrent-subject");
    }
}

package com.cookgenie.domain.user.repository;

import com.cookgenie.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByProviderAndProviderId(String provider, String providerId);
}

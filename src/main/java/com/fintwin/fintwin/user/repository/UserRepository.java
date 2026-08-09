package com.fintwin.fintwin.user.repository;

import com.fintwin.fintwin.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}

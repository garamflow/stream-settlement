package com.github.garamflow.streamsettlement.repository.user;

import com.github.garamflow.streamsettlement.entity.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}

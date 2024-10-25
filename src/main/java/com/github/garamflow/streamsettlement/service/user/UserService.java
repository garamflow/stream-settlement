package com.github.garamflow.streamsettlement.service.user;

import com.github.garamflow.streamsettlement.entity.user.User;

import java.util.Optional;

public interface UserService {

    Optional<User> findByEmail(String email);
}

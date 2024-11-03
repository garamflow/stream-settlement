package com.github.garamflow.streamsettlement.service.user;

import com.github.garamflow.streamsettlement.entity.member.Member;

import java.util.Optional;

public interface MemberService {

    Optional<Member> findByEmail(String email);
}

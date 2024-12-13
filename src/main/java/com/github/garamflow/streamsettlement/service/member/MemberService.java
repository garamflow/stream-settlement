package com.github.garamflow.streamsettlement.service.member;

import com.github.garamflow.streamsettlement.entity.member.Member;

import java.util.Optional;

public interface MemberService {

    Optional<Member> findByEmail(String email);
}

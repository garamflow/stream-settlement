package com.github.garamflow.streamsettlement.repository.user;

import com.github.garamflow.streamsettlement.entity.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);
}

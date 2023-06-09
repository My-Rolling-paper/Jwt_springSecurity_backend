package restful_sign_project.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PutMapping;
import restful_sign_project.JWT.JwtTokenProvider;
import restful_sign_project.dto.Member_Dto;
import restful_sign_project.entity.Member;

import restful_sign_project.repository.Member_Repository;

import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = false)
@RequiredArgsConstructor
public class Member_Service implements UserDetailsService {
    private final Member_Repository memberRepository;
    private final RedisService redisService;
    private final RedisTemplate redisTemplate;


    @Transactional
    public Member join(Member_Dto memberDto) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        String bcry_password = passwordEncoder.encode(memberDto.getPassWord());
//        memberDto.setPassWord(passwordEncoder.encode(memberDto.getPassWord()));
        Member member = Member.builder()
                .name(memberDto.getName())
                .email(memberDto.getEmail())
                .passWord(bcry_password)
                .roles(Collections.singletonList("ROLE_USER"))
                .build();
        return memberRepository.save(member);
    }

    public Optional<Member> findMemberByEmail(String email) {
        return memberRepository.findMemberByEmail(email);
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return memberRepository.findMemberByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));
    }

}

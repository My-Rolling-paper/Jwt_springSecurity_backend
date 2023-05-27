package restful_sign_project.controller;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import restful_sign_project.JWT.JwtTokenProvider;
import restful_sign_project.JWT.refresh.RefreshTokenRedisRepository;
import restful_sign_project.controller.Response.*;
import restful_sign_project.controller.status.ResponseMessage;
import restful_sign_project.controller.status.StatusCode;
import restful_sign_project.dto.Member_Dto;
import restful_sign_project.entity.Member;
import restful_sign_project.repository.Member_Repository;
import restful_sign_project.service.EmailService;
import restful_sign_project.service.Member_Service;
import restful_sign_project.service.PageService;
import restful_sign_project.service.RedisService;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController("/api")
@Slf4j
@Transactional
@EnableGlobalMethodSecurity(prePostEnabled = true)
@CrossOrigin(origins = "https://restful-jwt-project.herokuapp.com")

public class Member_Controller {
    private final BCryptPasswordEncoder encoder;
    private final Member_Service memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private final RefreshTokenRedisRepository refreshTokenRedisRepository;
    private final PageService pageService;
    private final Member_Repository memberRepository;
    private final EmailService emailService;
    private RedisTemplate redisTemplate;
    @Value("${jwt.token.secret}")
    private String key;

    private final Long expireTimeMs = 30000l;
    private final Long RefreshExpireTimeMs = 1000 * 60 * 60 * 60L;

    public Member_Controller(
            BCryptPasswordEncoder encoder,
            Member_Service memberService,
            JwtTokenProvider jwtTokenProvider,
            RedisService redisService,
            RefreshTokenRedisRepository refreshTokenRedisRepository,
            PageService pageService,
            Member_Repository memberRepository, EmailService emailService) {
        this.encoder = encoder;
        this.memberService = memberService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisService = redisService;
        this.refreshTokenRedisRepository = refreshTokenRedisRepository;
        this.pageService = pageService;
        this.memberRepository = memberRepository;
        this.emailService = emailService;
    }
    //회원가입

    /**
     * JSON형식으로 입력을 받으며 STRING : STRING 형식으로 입력을 받기 때문에 Map함수를 사용함.
     */
    @PostMapping("/signup")
    public ResponseEntity<SignInResponse> signup(@RequestBody Map<String, String> memberDto) {
        SignInResponse response = new SignInResponse();
        String name = (String) memberDto.get("name");
        String email = (String) memberDto.get("email");
        String password = (String) memberDto.get("password");
        Member_Dto member_dto = new Member_Dto(name, email, password);
        Optional<Member> memberFind = memberService.findMemberByEmail(email);
        if (memberFind.isEmpty()) { //Optional로 받았기 때문에 member가 없는/있는 상황을 고려해야함.
            Member member = memberService.join(member_dto);
            log.info(member.getRoles().get(0));
            response = SignInResponse.builder()
                    .code(StatusCode.OK)
                    .message(ResponseMessage.SIGNIN_SUCCESS)
                    .data(member)
                    .build();
            return ResponseEntity.ok(response); // 성공하면 OK안에 response를 담아서 return 함.
        }
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST); //실패하면 BAD_REQUEST와 함께 response를 보냄
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody Map<String, String> user) { //로그인도 회원가입과 마찬가지로 map함수를 사용해서 받음
        LoginResponse loginResponse = new LoginResponse();
        Optional<Member> member1 = memberService.findMemberByEmail(user.get("email")); //memberService를 이용해서 email로 Member를 찾음
        if (!member1.isPresent()) { //email로 찾았는데 member가 없는 경우
            loginResponse = LoginResponse.builder()
                    .code(StatusCode.UNAUTHORIZED)
                    .message(ResponseMessage.EMAIL_NOT_FOUND)
                    .build();
            return new ResponseEntity<>(loginResponse, HttpStatus.BAD_REQUEST);
        }
        Member member = member1.get(); //member가 있는 경우
        log.info(member.getPassword());
        log.info(user.get("password"));
        if (!encoder.matches(user.get("password"), member.getPassword())) { //
            loginResponse = LoginResponse.builder()
                    .code(StatusCode.FORBIDDEN)
                    .message(ResponseMessage.PASSWORD_ERROR)
                    .build();
            return new ResponseEntity<>(loginResponse, HttpStatus.BAD_REQUEST);
        }
        long currentTimeMillis = System.currentTimeMillis();
        Long expireTimesEND = expireTimeMs + currentTimeMillis; // Spring에서 현재시간에서 expireTimeMs가 더해진 시간을 MS단위로 보낸다
        log.info(expireTimesEND.toString());

        String token = jwtTokenProvider.createToken(member.getEmail(), member.getRoles(), expireTimeMs); //AccessToken : tokenProvider을 통해서 인자로 이메일,역할,시간을 보낸다.
        String refreshToken = jwtTokenProvider.createToken(member.getEmail(), member.getRoles(), RefreshExpireTimeMs); //RefreshToken : tokenProvider을 통해서 인자로 이메일,역할,시간을 보낸다.
        redisService.setValues(member.getEmail(),refreshToken);
        log.info(token);
        log.info(refreshToken);
        //HTTPONLY 쿠키에 RefreshToken 생성후 전달
        ResponseCookie responseCookie =
                ResponseCookie.from("refreshToken", refreshToken)
//                        .domain("restful-jwt-project.herokuapp.com")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("None")
                        .path("/")
                        .maxAge(3600000)
                        .build();

        loginResponse = LoginResponse.builder()
                .code(StatusCode.OK)
                .message(ResponseMessage.LOGIN_SUCCESS)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .header("accessToken", token)
                .header("expireTime", String.valueOf(expireTimesEND))
                .body(loginResponse);
    }

    @GetMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(@RequestBody Map<String, String> token) {
        String accessToken = token.get("accessToken");
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new IllegalStateException("유효하지 않은 토큰입니다.");
        }
        String userEmail = jwtTokenProvider.getUserPk(accessToken);
        if (redisTemplate.opsForValue().get(userEmail) != null) {
            log.info("redisTemplate에 refresh토큰이 존재한다.");
            redisTemplate.delete(userEmail); // refresh Token 삭제
        }
        Long AccessTokenExpiretime = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue().set(accessToken,"logout",AccessTokenExpiretime,TimeUnit.MILLISECONDS);
        LogoutResponse logoutResponse = LogoutResponse.builder()
                .code(StatusCode.OK)
                .message(ResponseMessage.LOGOUT_SUCCESS)
                .build();
        return ResponseEntity.ok(logoutResponse);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@CookieValue(value = "refreshToken", required = false) String refreshToken) {
        if (refreshToken != null) {
            // Access Token 갱신
            TokenResponse token = jwtTokenProvider.refreshToken(refreshToken);

            long currentTimeMillis = System.currentTimeMillis();
            Long expireTimesEND = expireTimeMs + currentTimeMillis; // Spring에서 현재시간에서 expireTimeMs가 더해진 시간을 MS단위로 보낸다
            log.info(expireTimesEND.toString());

            // 새로운 Access Token 값과 함께 응답 객체 생성
            RefreshTokenResponse response = RefreshTokenResponse.builder()
                    .code(StatusCode.OK)
                    .message(ResponseMessage.REFRESH_TOKEN_SUCCESS)
                    .build();

            // HTTP Only 쿠키에 RefreshToken 생성 후 전달
            ResponseCookie responseCookie = ResponseCookie.from("refreshToken", token.getRefreshToken())
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("None")
                    .path("/")
                    .maxAge(3600000)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                    .header("accessToken", token.getAccessToken())
                    .header("expireTime", String.valueOf(expireTimesEND))
                    .body(response);
        } else {
            // refreshToken이 존재하지 않을 경우에 대한 처리
            RefreshTokenResponse response = RefreshTokenResponse.builder()
                    .code(StatusCode.BAD_REQUEST)
                    .message(ResponseMessage.REFRESH_TOKEN_FAIL)
                    .build();
            return ResponseEntity.badRequest().body(response);
        }
    }


    @PostMapping("/passwordChange/{id}")
    public ResponseEntity<?> passWordChange(@PathVariable Long id, @RequestBody Map<String, String> password) {
        String currentPassword = password.get("currentPassword");
        String newPassWord = password.get("newPassword");
        PasswordChangeResponse passwordChangeResponse = new PasswordChangeResponse();
        // ID를 기반으로 데이터베이스에서 해당 멤버를 조회합니다.
        Optional<Member> op_member = memberRepository.findMemberById(id);
        Member member = op_member.get();
        if (!currentPassword.equals(newPassWord)) {
            if (encoder.matches(currentPassword, member.getPassword())) {
                log.info("비밀번호 똑같아요!!");
                BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
                String bcry_password = passwordEncoder.encode(newPassWord);
                member.setPassWord(bcry_password);

                memberRepository.save(member);
                passwordChangeResponse = PasswordChangeResponse.builder()
                        .code(StatusCode.OK)
                        .message(ResponseMessage.PASSWORD_CHANGE_OK)
                        .build();
                return ResponseEntity.ok(passwordChangeResponse);
            } else {
                log.info(member.getName());
                return new ResponseEntity<>(passwordChangeResponse, HttpStatus.BAD_REQUEST);
            }
        } else {
            passwordChangeResponse.setMessage("비밀번호를 다르게 입력하세요");
            return new ResponseEntity<>(passwordChangeResponse, HttpStatus.BAD_REQUEST);
        }
    }


}
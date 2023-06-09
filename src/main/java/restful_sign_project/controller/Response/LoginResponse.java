package restful_sign_project.controller.Response;

import lombok.Builder;
import lombok.Data;
import restful_sign_project.controller.status.ResponseMessage;
import restful_sign_project.controller.status.StatusCode;

/**
 * Login을 할 때 반환되는 Response입니다.
 */
@Data
public class LoginResponse {
    private int code;
    private String message;
    private Object token;
    private Long expireTimeMs;

    public LoginResponse() {
        this.code = code = StatusCode.BAD_REQUEST;
        this.message = ResponseMessage.LOGIN_FAILED;
        this.token = null;
        this.expireTimeMs = null;
    }

    @Builder
    public LoginResponse(int code, String message, Object token, Long expireTimeMs) {
        this.code = code;
        this.message = message;
        this.token = token;
        this.expireTimeMs = expireTimeMs;
    }
}

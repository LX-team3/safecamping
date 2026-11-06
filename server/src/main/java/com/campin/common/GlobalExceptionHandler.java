package com.campin.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;


// 컨트롤러에서 던져진 예외를 한곳에서 ApiResponse 실패 응답으로 바꾼다 (컨트롤러에서 try-catch 하지 않음)
@RestControllerAdvice  // 스프링: 모든 @RestController 의 예외를 여기서 잡는다
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 서비스에서 규칙 위반으로 던진 예외 → 400
    @ExceptionHandler(InvalidRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> invalidRequest(InvalidRequestException e) {
        return ApiResponse.fail(e.getMessage());
    }

    // @Valid 검사 실패 → 400. 여러 개가 틀려도 첫 번째 문구만 보여 준다
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> notValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("입력값을 다시 확인해 주세요.");
        return ApiResponse.fail(message);
    }

    // JSON 형식이 틀렸거나 필요한 주소 파라미터가 빠짐 → 400
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> badFormat(Exception e) {
        return ApiResponse.fail("요청 형식이 맞지 않습니다.");
    }

    // 로그인 중 DB 오류 등 → 500. 아래 401 처리보다 먼저 잡아야 "비밀번호 틀림"으로 가려지지 않는다
    @ExceptionHandler(InternalAuthenticationServiceException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> loginError(InternalAuthenticationServiceException e) {
        log.error("로그인 처리 중 오류", e);
        return ApiResponse.fail("잠시 후 다시 시도해 주세요.");
    }

    // 로그인 실패 → 401. 아이디가 없는지 비밀번호가 틀렸는지는 알려 주지 않는다
    @ExceptionHandler(AuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiResponse<Void> loginFailed(AuthenticationException e) {
        return ApiResponse.fail("아이디 또는 비밀번호가 맞지 않습니다.");
    }

    // 서비스에서 찾는 데이터가 없다고 던진 예외 → 404
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> dataNotFound(NotFoundException e) {
        return ApiResponse.fail(e.getMessage());
    }

    // 없는 주소 → 404
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> notFound(NoResourceFoundException e) {
        return ApiResponse.fail("없는 주소입니다.");
    }

    // 그 밖의 오류 → 500. 내부 내용은 로그에만 남기고 사용자에게는 공통 문구
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> unknown(Exception e) {
        log.error("처리하지 못한 오류", e);
        return ApiResponse.fail("잠시 후 다시 시도해 주세요.");
    }
}

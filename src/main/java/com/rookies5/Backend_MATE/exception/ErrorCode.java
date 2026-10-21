package com.rookies5.Backend_MATE.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // 인증/인가 관련 (40X)
    AUTH_INVALID_CREDENTIALS("AUTH_001", "이메일 또는 비밀번호가 올바르지 않습니다", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_EXPIRED("AUTH_002", "토큰이 만료되었습니다", HttpStatus.UNAUTHORIZED),
    AUTH_TOKEN_INVALID("AUTH_003", "유효하지 않은 토큰입니다", HttpStatus.UNAUTHORIZED),
    AUTH_ACCESS_DENIED("AUTH_004", "접근 권한이 없습니다", HttpStatus.FORBIDDEN),
    AUTH_ACCOUNT_LOCKED("AUTH_006", "로그인 시도가 너무 많아 계정이 일시적으로 잠겼습니다. 잠시 후 다시 시도해주세요", HttpStatus.LOCKED),
    AUTH_RESET_TOKEN_INVALID("AUTH_005", "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다", HttpStatus.BAD_REQUEST),

    // 회원 관련 (40X, 422)
    USER_NOT_FOUND("USER_001", "사용자를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    USER_EMAIL_DUPLICATE("USER_002", "이미 사용 중인 이메일입니다", HttpStatus.CONFLICT),
    USER_NICKNAME_DUPLICATE("USER_003", "이미 사용 중인 닉네임입니다", HttpStatus.CONFLICT),
    USER_ACTIVE_PROJECT_EXISTS("USER_004", "진행 중인 프로젝트가 있어 탈퇴할 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY),
    USER_PHONE_DUPLICATE("USER_005", "이미 사용 중인 전화번호입니다", HttpStatus.CONFLICT),
    USER_NOT_MATCHED("USER_006", "입력된 이메일과 전화번호가 일치하는 사용자가 없습니다", HttpStatus.NOT_FOUND),
    USER_NICKNAME_FORMAT_INVALID("USER_007", "닉네임은 2~10자의 영문, 숫자, 한글만 가능합니다", HttpStatus.BAD_REQUEST),
    INVALID_EMAIL_FORMAT("USER_008", "올바른 이메일 형식이 아닙니다", HttpStatus.BAD_REQUEST),

    // 파일/이미지 관련 (400, 415)
    FILE_SIZE_EXCEEDED("FILE_001", "파일 크기는 최대 5MB까지만 허용됩니다", HttpStatus.BAD_REQUEST),
    FILE_UNSUPPORTED_EXTENSION("FILE_002", "JPG, JPEG, PNG 형식의 이미지만 업로드 가능합니다", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    FILE_UPLOAD_ERROR("FILE_003", "파일 업로드 중 서버 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),

    // 프로젝트(모집글) 관련 (40X, 422)
    PROJECT_NOT_FOUND("PROJECT_001", "해당 프로젝트를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    PROJECT_HAS_MEMBERS("PROJECT_002", "진행 중인 팀원이 있는 프로젝트는 삭제할 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY),
    PROJECT_CLOSED("PROJECT_003", "모집이 마감된 프로젝트입니다", HttpStatus.UNPROCESSABLE_ENTITY),
    PROJECT_RECRUITMENT_FULL("PROJECT_004", "모집 정원이 가득 찼습니다", HttpStatus.UNPROCESSABLE_ENTITY),
    PROJECT_RECRUIT_COUNT_INVALID("PROJECT_005", "모집 인원은 현재 합류한 인원보다 적을 수 없습니다", HttpStatus.BAD_REQUEST),
    PROJECT_END_DATE_INVALID("PROJECT_006", "마감 일자는 오늘 이후여야 합니다", HttpStatus.BAD_REQUEST),

    // 지원서/매칭 관련 (40X, 422)
    APPLY_NOT_FOUND("APPLY_001", "지원서를 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    APPLY_DUPLICATE("APPLY_002", "이미 지원했거나 가입 거절된 프로젝트입니다", HttpStatus.CONFLICT),
    APPLY_CANNOT_CANCEL("APPLY_003", "이미 처리된 지원서는 취소할 수 없습니다", HttpStatus.UNPROCESSABLE_ENTITY),
    OWNER_CANNOT_APPLY("APPLY_004", "방장은 본인의 프로젝트에 지원할 수 없습니다", HttpStatus.BAD_REQUEST),

    // 멤버십 및 게시판 관련 (40X)
    MEMBER_NOT_FOUND("MEMBER_001", "해당 프로젝트의 팀원이 아닙니다", HttpStatus.NOT_FOUND),
    BOARD_NOT_FOUND("BOARD_001", "게시글을 찾을 수 없습니다", HttpStatus.NOT_FOUND),
    COMMENT_NOT_FOUND("COMMENT_001", "해당 댓글을 찾을 수 없습니다", HttpStatus.NOT_FOUND),

    // 검증 관련 (400)
    VALIDATION_ERROR("VALID_001", "입력값이 올바르지 않습니다", HttpStatus.BAD_REQUEST),
    REQUIRED_FIELD_MISSING("VALID_002", "필수 항목이 누락되었습니다", HttpStatus.BAD_REQUEST),
    INVALID_PHONE_FORMAT("VALID_003", "전화번호 형식이 올바르지 않습니다 (숫자 11자)", HttpStatus.BAD_REQUEST),


    // 서버 오류 (500)
    INTERNAL_SERVER_ERROR("SERVER_001", "서버 내부 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_ERROR("SERVER_002", "데이터베이스 오류가 발생했습니다", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
package com.trading.journal.dto;

/**
 * 인증 모드 공개 설정.
 *
 * <p>프론트엔드가 로그인 화면을 보여줄지({@code authEnabled=true}) 바로 대시보드로 진입할지 결정하는 데 쓴다.
 *
 * @param authEnabled 로그인/JWT 인증 활성화 여부 ({@code app.auth.enabled})
 */
public record AuthConfigDto(boolean authEnabled) {}

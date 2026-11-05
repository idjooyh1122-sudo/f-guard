package com.fguard.config;

import org.springframework.stereotype.Component;

/**
 * 현재 사용자 식별. 해커톤 버전은 로그인 없이 기본 사용자(id=1)를 사용한다.
 * 로그인 기능 추가 시 이 클래스만 교체하면 된다.
 */
@Component
public class CurrentUser {
    public static final long DEFAULT_USER_ID = 1L;

    public long id() {
        return DEFAULT_USER_ID;
    }
}

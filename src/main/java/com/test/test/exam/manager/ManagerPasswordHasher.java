package com.test.test.exam.manager;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * 운영 DB 에 매니저를 <b>직접 INSERT</b> 할 때 쓸 비밀번호 해시를 찍어 준다.
 *
 * <pre>
 *   ./gradlew managerHash -Ppassword=내가정한비밀번호
 * </pre>
 *
 * <p><b>왜 필요한가</b>: {@code password_hash} 컬럼에 평문을 넣으면 로그인이 안 된다.
 * 로그인은 BCrypt 해시와 비교하기 때문이다. 그렇다고 아무 온라인 해시 생성기에
 * 운영 비밀번호를 붙여넣게 둘 수는 없다 — 그 순간 남의 서버에 넘어간 비밀번호가 된다.
 *
 * <p>애플리케이션 코드가 아니라 <b>운영 도구</b>다. 서버 기동과는 무관하게 이 클래스만 실행된다.
 */
public final class ManagerPasswordHasher {

    private ManagerPasswordHasher() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.out.println("사용법: ./gradlew managerHash -Ppassword=비밀번호");
            return;
        }
        String password = args[0];
        if (password.length() < ManagerAccountInitializer.MIN_PASSWORD_LENGTH) {
            System.out.printf("⚠️  %d자입니다. 운영은 %d자 이상을 요구합니다(짧으면 기동이 중단됩니다).%n",
                    password.length(), ManagerAccountInitializer.MIN_PASSWORD_LENGTH);
        }
        System.out.println();
        System.out.println(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password));
        System.out.println();
        System.out.println("위 값을 member.password_hash 에 넣으세요. {bcrypt} 접두사까지 통째로 넣어야 합니다.");
    }
}

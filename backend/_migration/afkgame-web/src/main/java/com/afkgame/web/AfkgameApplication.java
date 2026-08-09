package com.afkgame.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AFK GAME バックエンドのエントリーポイント。
 *
 * <p>ドメイン層（{@code com.afkgame.domain}）はアプリケーション層と別パッケージのため、
 * コンポーネントスキャンと Mapper スキャンの起点を明示する。設定値のクラスは
 * {@code afkgame-env}（{@code com.afkgame.env.config}）にあるため、あわせて走査する。
 * 構成の正は docs/tech/basic/tech_structure.md §2・§4。
 *
 * <p>認証は JWT のみで、ユーザー名／パスワードによる認証（フォーム・Basic）を使わないため、
 * Spring Security の既定ユーザー自動構成は無効化する（起動ログに使われない生成パスワードが
 * 出るのを防ぐ）。認証方式の正は docs/tech/detail/tech_auth.md §1。
 */
@SpringBootApplication(
        scanBasePackages = "com.afkgame",
        exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan("com.afkgame")
@MapperScan("com.afkgame.domain.repository")
public class AfkgameApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfkgameApplication.class, args);
    }
}

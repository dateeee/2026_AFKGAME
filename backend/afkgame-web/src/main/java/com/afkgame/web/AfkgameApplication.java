package com.afkgame.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AFK GAME バックエンドのエントリーポイント。
 *
 * <p>ドメイン層（{@code com.afkgame.domain}）はアプリケーション層と別パッケージのため、
 * コンポーネントスキャンと Mapper スキャンの起点を明示する。
 * 構成の正は docs/tech/basic/tech_structure.md §2・§4。
 */
@SpringBootApplication(scanBasePackages = "com.afkgame")
@MapperScan("com.afkgame.domain.repository")
public class AfkgameApplication {

    public static void main(String[] args) {
        SpringApplication.run(AfkgameApplication.class, args);
    }
}

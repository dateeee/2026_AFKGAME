package com.afkgame.env.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 現在時刻の供給元を定義する。
 *
 * <p>現在時刻はサービス内部で取得せず外から受ける（コーディング規約 §2）。DBに保存する時刻は
 * すべて UTC のため、既定の {@link Clock} も UTC で揃える（docs/tech/basic/tech_db.md）。
 *
 * <p>テストは実時間に依存させないため、{@code Clock.fixed(...)} を直接コンストラクタへ渡す。
 */
@Configuration
public class TimeConfig {

    /**
     * アプリケーション全体で使う現在時刻の供給元。
     *
     * @return UTC のシステムクロック
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

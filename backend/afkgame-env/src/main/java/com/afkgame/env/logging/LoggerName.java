package com.afkgame.env.logging;

/**
 * ログの出力元を表すロガー名。
 *
 * <p>仕様: docs/tech/basic/tech_logging.md「ロガー名体系」。
 * 出力先とレベルを機能単位で切り替えるため、ロガー名はクラスの配置から独立させる
 * （coding_standards_backend/common.md §7 #1。{@code getLogger(Xxx.class)} を使わない）。
 *
 * <p>体系の正は仕様書側にある。本 enum は**実際に出力している領域だけ**を持ち、
 * 新しい領域のログを書くときに追加する。
 */
public enum LoggerName {

    /** 認証処理（ゲスト作成・登録・ログイン・ログアウト・トークン検証）。 */
    AUTH("afkgame.auth"),

    /** リクエストログ・例外ハンドラなどの横断処理。 */
    MIDDLEWARE("afkgame.middleware"),

    /** ヘルスチェック（運用監視向け）。 */
    HEALTH("afkgame.health"),

    /** 戦闘tick処理・オフライン計算。 */
    BATTLE("afkgame.battle"),

    /** 通信の START / END（受信・送信）。通信ログファイルへ出る唯一のロガー。 */
    COMM("afkgame.comm"),

    /** AOP による境界ログ（Service・Repository の START / END）。 */
    LAYER("afkgame.layer");

    private final String loggerName;

    LoggerName(String loggerName) {
        this.loggerName = loggerName;
    }

    /**
     * SLF4J へ渡すロガー名を返す。
     *
     * @return {@code afkgame.<領域>} 形式のロガー名
     */
    public String loggerName() {
        return loggerName;
    }
}

package com.afkgame.domain.masterdata;

/**
 * 敵のマスターデータを保持するレジストリ。
 *
 * <p>列の正は docs/data/towers/000_テンプレート.md §2。
 *
 * <p><b>本クラスは製造①-i で用意した表層であり、レジストリ本体は未実装。</b>
 * YAML（{@code masterdata/enemies.yml}）の読み込みと {@code @Component} 登録は、階層データを
 * 作るセグメント②（tower。docs/backlog/java_migration.md STEP 3-B）で {@link Items} と同じ形に
 * そろえる。YAML が無いまま {@code @Component} を付けると、起動時ローダが読み込みに失敗して
 * アプリケーションコンテキストが起動しなくなるため、登録は本体と同じ回に行う。
 */
public class Enemies {

    /**
     * 敵IDでマスターデータを取得する。
     *
     * @param enemyId 敵ID
     * @return 該当する敵データ
     */
    public EnemyData get(String enemyId) {
        throw new UnsupportedOperationException("セグメント②（tower マスターデータ）で実装する");
    }
}

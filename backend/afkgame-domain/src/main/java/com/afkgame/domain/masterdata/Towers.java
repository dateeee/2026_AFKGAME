package com.afkgame.domain.masterdata;

import java.util.List;

/**
 * 塔のマスターデータを保持するレジストリ。
 *
 * <p>数値の正は docs/data/towers/TOWERS_OVERVIEW.md「塔一覧」。
 *
 * <p><b>本クラスはテストリスト作成②-a で用意した表層であり、レジストリ本体は未実装。</b>
 * YAML（{@code masterdata/towers.yml}）の読み込みと {@code @Component} 登録は、階層データを
 * 作る製造②（tower。docs/backlog/java_migration.md STEP 3-B）で {@link Items} と同じ形に
 * そろえる。YAML が無いまま {@code @Component} を付けると、起動時ローダが読み込みに失敗して
 * アプリケーションコンテキストが起動しなくなるため、登録は本体と同じ回に行う
 * （{@link Enemies} と同じ理由）。
 */
public class Towers {

    /**
     * 塔IDでマスターデータを取得する。
     *
     * @param towerId 塔ID
     * @return 該当する塔データ。定義が無ければ null
     */
    public TowerData get(String towerId) {
        throw new UnsupportedOperationException("製造②（tower マスターデータ）で実装する");
    }

    /**
     * 全塔をマスターデータの定義順で返す。
     *
     * <p>順序は TOWERS_OVERVIEW.md の番号順（一覧APIがこの順で応答する。
     * docs/tech/detail/tech_tower/list.md §5 手順2）。
     *
     * @return 塔データの一覧
     */
    public List<TowerData> all() {
        throw new UnsupportedOperationException("製造②（tower マスターデータ）で実装する");
    }
}

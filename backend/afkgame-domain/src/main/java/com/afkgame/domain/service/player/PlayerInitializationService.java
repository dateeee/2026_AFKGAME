package com.afkgame.domain.service.player;

import com.afkgame.domain.model.Player;

/**
 * プレイヤーの初期状態を組み立てるドメインサービス。
 *
 * <p>仕様: docs/tech/detail/tech_auth/init.md §8.2「処理フロー」手順2〜6・§8.3「分岐一覧」。
 * 既定値の正は docs/tech/basic/tech_db/player.md §1（players）・§2（player_settings）・§4（characters）。
 *
 * <p>共有 Service。{@link AuthService} のゲスト作成と本登録から利用する（入口が異なるだけで
 * 手順2以降は共通のため切り出している）。トランザクション境界は呼び出し側が持つ（§8.2 手順8）。
 *
 * <p>実装は {@link PlayerInitializationServiceImpl}。
 */
public interface PlayerInitializationService {

    /**
     * ユーザーにプレイ可能な初期状態を作る（tech_auth/init.md §8.2 手順2〜6）。
     *
     * <p>1ユーザー1プレイヤーは {@code uq_players_user_id} が保証する。既にプレイヤーがある場合は
     * 一意制約違反がそのまま送出され、呼び出し側のトランザクションが全体をロールバックする（§8.3 #2・#12）。
     * 公開APIからは到達しない状態のため、業務例外へは写さない。
     *
     * <p>呼び出し側の境界に必ず含める（{@code Propagation.MANDATORY}）。境界が無い状態で呼ぶと
     * 例外になり、付け忘れに気づける。
     *
     * @param userId 初期化する対象のユーザーID
     * @return 作成したプレイヤー
     * @throws org.springframework.transaction.IllegalTransactionStateException
     *         トランザクションが無い状態で呼ばれた場合
     */
    Player initialize(String userId);
}

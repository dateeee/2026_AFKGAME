package com.afkgame.web.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.terasoluna.gfw.common.exception.BusinessException;
import org.terasoluna.gfw.common.message.ResultMessage;
import org.terasoluna.gfw.common.message.ResultMessages;

/**
 * {@link ErrorCatalog} の単体テスト。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「AUTH_ コード一覧」、
 * 規約は docs/process/coding_standards_backend/exception.md §4 #4・#5。
 *
 * <p><b>コードとステータスの網羅的な一致は {@code scripts/check_error_codes.py} が担う</b>
 * （規約 §4 #5「手で同期させない」）。本クラスは表の引き方の分岐だけを持つ。
 *
 * <p>分岐観点: 登録済みのコード / 未登録のコード、および業務例外からのコード取り出しの
 * 正常 / 空の {@code ResultMessages} / コードを持たない {@code ResultMessage}。
 * 骨格構築の横断基盤であり詳細設計の分岐一覧を持たないため、分岐マーカーは付けない。
 */
@Tag("unit")
class ErrorCatalogTest {

    @Nested
    @DisplayName("コードからの引き当て")
    class TestFind {

        @Test
        void test_登録済みのコードは仕様のステータスと文言を返す() {
            ErrorCatalog.Entry entry = ErrorCatalog.find("AUTH_PLAYER_NOT_FOUND");

            assertThat(entry.status()).isEqualTo(404);
            assertThat(entry.message()).isEqualTo("指定されたプレイヤーが見つかりません");
        }

        /** 未登録は 422 と定型文へ倒す（規約 §4 #4「未登録は 422」）。 */
        @Test
        void test_未登録のコードは422と定型文になる() {
            ErrorCatalog.Entry entry = ErrorCatalog.find("BATTLE_NOT_IN_TOWER");

            assertThat(entry.status()).isEqualTo(422);
            assertThat(entry.message()).isEqualTo("リクエストを処理できませんでした");
        }
    }

    @Nested
    @DisplayName("業務例外からのコード取り出し")
    class TestCodeOf {

        @Test
        void test_ResultMessagesの先頭のコードを返す() {
            BusinessException e = new BusinessException(ResultMessages.error().add("AUTH_EMAIL_TAKEN"));

            assertThat(ErrorCatalog.codeOf(e)).isEqualTo("AUTH_EMAIL_TAKEN");
        }

        /** コードを載せないのは規約 §3 #1 違反。空のコードを返さず内部エラーへ倒す。 */
        @Test
        void test_ResultMessagesが空なら内部エラーのコードになる() {
            BusinessException e = new BusinessException(ResultMessages.error());

            assertThat(ErrorCatalog.codeOf(e)).isEqualTo("INTERNAL_UNEXPECTED_ERROR");
        }

        @Test
        void test_コードを持たないメッセージなら内部エラーのコードになる() {
            BusinessException e = new BusinessException(
                    ResultMessages.error().add(ResultMessage.fromText("文言だけのメッセージ")));

            assertThat(ErrorCatalog.codeOf(e)).isEqualTo("INTERNAL_UNEXPECTED_ERROR");
        }
    }
}

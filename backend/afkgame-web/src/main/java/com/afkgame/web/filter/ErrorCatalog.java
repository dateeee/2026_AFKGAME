package com.afkgame.web.filter;

import java.util.List;
import java.util.Map;

import org.terasoluna.gfw.common.exception.ResultMessagesNotificationException;
import org.terasoluna.gfw.common.message.ResultMessage;

/**
 * エラーコードから HTTP ステータスと応答メッセージを引く対応表。
 *
 * <p>仕様: docs/tech/basic/tech_error_handling.md「エラーコード体系」「AUTH_ コード一覧」。
 * コードとステータスの対応は同ファイルが正で、**一致は {@code scripts/check_error_codes.py} が
 * 機械照合する**（手で同期させない。規約 exception.md §4 #5）。
 *
 * <p>ドメイン層の業務例外はエラーコードだけを持ち、ステータスと文言は本表が決める
 * （規約 exception.md §4 #4・common.md §2「HTTP ステータスはドメイン層で扱わない」）。
 * <b>文言の正は本表</b>であり、仕様書は持たない（Service は文言を解決しない・exception.md §3 #5）。
 * クライアントは文言ではなくコードで分岐するため、文言は表示用の定型文にとどめる。
 *
 * <p>認証・認可の失敗は理由を出し分けない（exception.md §4 #2）。
 * {@code AUTH_INVALID_TOKEN} と {@code AUTH_USER_NOT_FOUND} が同じ文言なのはそのためで、
 * 切り分けはログの {@code reason} だけが持つ。
 */
public final class ErrorCatalog {

    /** 対応表に無いコードの既定ステータス（規約 exception.md §4 #4）。 */
    private static final int DEFAULT_STATUS = 422;

    /** 業務コードを持たない応答と、対応表に無いコードへ充てる定型文。 */
    static final String GENERIC_MESSAGE = "リクエストを処理できませんでした";

    /** サーバー内部エラー。分類2・分類3の応答をここへそろえる（規約 exception.md §1）。 */
    static final String INTERNAL_ERROR = "INTERNAL_UNEXPECTED_ERROR";

    /** 認証情報が無効であることだけを伝える文言。理由（署名不正・ユーザー不在）は区別させない。 */
    private static final String INVALID_AUTHENTICATION = "認証情報が無効です";

    /** Google 連携の不成立。未設定と未実装をクライアントへ区別させない。 */
    private static final String GOOGLE_UNAVAILABLE = "Googleログインは利用できません";

    private static final Map<String, Entry> ENTRIES = Map.ofEntries(
            Map.entry("AUTH_HEADER_MISSING", new Entry(401, "認証が必要です")),
            Map.entry("AUTH_INVALID_FORMAT", new Entry(401, "認証情報の形式が正しくありません")),
            Map.entry("AUTH_TOKEN_EXPIRED", new Entry(401, "アクセストークンの有効期限が切れています")),
            Map.entry("AUTH_INVALID_TOKEN", new Entry(401, INVALID_AUTHENTICATION)),
            Map.entry("AUTH_USER_NOT_FOUND", new Entry(401, INVALID_AUTHENTICATION)),
            Map.entry("AUTH_PLAYER_NOT_FOUND", new Entry(404, "指定されたプレイヤーが見つかりません")),
            Map.entry("AUTH_INVALID_CREDENTIALS", new Entry(401, "メールアドレスまたはパスワードが正しくありません")),
            Map.entry("AUTH_REFRESH_INVALID", new Entry(401, "再度ログインしてください")),
            Map.entry("AUTH_EMAIL_TAKEN", new Entry(409, "このメールアドレスは既に使用されています")),
            Map.entry("AUTH_ALREADY_REGISTERED", new Entry(400, "このアカウントは既に本登録されています")),
            Map.entry("AUTH_LINK_PAYLOAD_INVALID", new Entry(400, "移行方法をどちらか一方だけ指定してください")),
            Map.entry("AUTH_VERIFICATION_INVALID", new Entry(400, "確認用リンクが無効か期限切れです")),
            Map.entry("AUTH_RESET_TOKEN_INVALID", new Entry(400, "再設定用リンクが無効か期限切れです")),
            Map.entry("AUTH_GOOGLE_NOT_CONFIGURED", new Entry(501, GOOGLE_UNAVAILABLE)),
            Map.entry("AUTH_GOOGLE_NOT_IMPLEMENTED", new Entry(501, GOOGLE_UNAVAILABLE)),
            Map.entry(INTERNAL_ERROR, new Entry(500, "サーバー内部エラーが発生しました")));

    private ErrorCatalog() {
    }

    /**
     * 1コード分の応答。
     *
     * @param status  HTTP ステータスコード
     * @param message クライアントへ返す定型文
     */
    public record Entry(int status, String message) {
    }

    /**
     * コードに対応する応答を引く。
     *
     * <p>未登録のコードは 422 と定型文へ倒す。新しいコードは仕様へ追加してから本表へ足す運用で、
     * 抜けは {@code check_error_codes.py} が落とす。
     *
     * @param code エラーコード
     * @return 対応するステータスと文言（未登録なら既定）
     */
    public static Entry find(String code) {
        Entry entry = ENTRIES.get(code);
        if (entry == null) {
            return new Entry(DEFAULT_STATUS, GENERIC_MESSAGE);
        }
        return entry;
    }

    /**
     * 業務例外が載せているエラーコードを取り出す。
     *
     * <p>規約 exception.md §3 #1 は {@code ResultMessages.error().add(code)} でコードを載せることを
     * 要求する。コードが無い（{@code ResultMessage.fromText} で作った・空の {@code ResultMessages}）のは
     * 実装の誤りなので、空のコードをクライアントへ返さず {@code INTERNAL_UNEXPECTED_ERROR}（500）へ倒す。
     *
     * @param e 業務例外
     * @return エラーコード。載っていない場合は {@code INTERNAL_UNEXPECTED_ERROR}
     */
    public static String codeOf(ResultMessagesNotificationException e) {
        List<ResultMessage> messages = e.getResultMessages().getList();
        if (messages.isEmpty() || messages.get(0).getCode() == null) {
            return INTERNAL_ERROR;
        }
        return messages.get(0).getCode();
    }
}

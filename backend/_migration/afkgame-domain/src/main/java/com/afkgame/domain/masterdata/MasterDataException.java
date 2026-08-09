package com.afkgame.domain.masterdata;

/**
 * マスターデータの読み込み・スキーマ検証に失敗したことを表す例外。
 *
 * <p>マスターデータは起動時に一度だけ読み込むため、本例外は Bean 生成中に送出され、
 * アプリケーションの起動を中止させる（docs/backlog/java_migration.md §5「マスターデータ」）。
 * 実行中のリクエストが受け取る業務エラーではないため {@link com.afkgame.domain.exception.AppException}
 * とは別系統にし、エラーコード・HTTPステータスを持たせない。
 */
public class MasterDataException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MasterDataException(String message) {
        super(message);
    }

    public MasterDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

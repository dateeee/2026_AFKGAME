package com.afkgame.env.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 生のトークン・パスワードを返すメソッドへ付け、AOP境界ログ（{@code result}）から値を伏せる。
 *
 * <p>仕様: docs/process/coding_standards_backend/logging/application.md §3.1 規約3
 * 「生のトークン・パスワードを返すメソッドは、戻り値を出力対象から外す注釈を付けて {@code ****} にする」。
 * 引数と異なり戻り値はパラメータ名で判定できないため、本注釈で明示する。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface MaskReturnValue {
}

/**
 * APIエラー型
 *
 * バックエンドは全例外を `{"error": {"code", "message"}}` に統一している
 * （backend/app/exceptions.py、コード体系は docs/tech/tech_logging.md）。
 * サーバーが用意した日本語メッセージとエラーコードを、そのまま画面へ届けるための型。
 */

/** 通信自体が失敗したとき（サーバー応答なし）に使う疑似ステータス */
export const NETWORK_ERROR_STATUS = 0

export class ApiError extends Error {
  readonly code: string
  readonly status: number
  /** 元の例外（ネットワークエラー時の fetch の失敗理由など） */
  readonly cause?: unknown

  constructor(message: string, code: string, status: number, cause?: unknown) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.cause = cause
  }

  /** 通信断（サーバーに届いていない）か。業務エラーとの出し分けに使う */
  get isNetworkError(): boolean {
    return this.status === NETWORK_ERROR_STATUS
  }
}

/** 通信断を表す ApiError を作る */
export function networkError(cause?: unknown): ApiError {
  return new ApiError(
    'サーバーに接続できません',
    'NETWORK_ERROR',
    NETWORK_ERROR_STATUS,
    cause,
  )
}

/**
 * エラー応答の本文から ApiError を組み立てる。
 * 統一形式でない応答（プロキシの502等）でも落ちないようフォールバックする。
 */
export async function toApiError(response: Response): Promise<ApiError> {
  const body = await response.json().catch(() => null)
  const error = body?.error
  return new ApiError(
    error?.message || `サーバーエラー (${response.status})`,
    error?.code || `HTTP_${response.status}`,
    response.status,
  )
}

/** 画面表示用のメッセージを取り出す（想定外の例外もここで文字列化する） */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof Error) return error.message
  return '予期しないエラーが発生しました'
}

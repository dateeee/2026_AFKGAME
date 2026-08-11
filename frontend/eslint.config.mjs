// ESLint flat config（規約の正は docs/process/coding_standards_frontend/basis.md §3）
// ルールは準拠元の推奨セットをそのまま使い、個別ルールの上書きをしない
// （上書きが要る判断は basis.md §2 #5 の手順で規約側に差分を書いてから）。
import pluginVue from 'eslint-plugin-vue'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import prettierConfig from '@vue/eslint-config-prettier'

export default defineConfigWithVueTs(
  { ignores: ['dist/**', 'node_modules/**', 'playwright-report/**', 'test-results/**'] },
  pluginVue.configs['flat/recommended'],
  vueTsConfigs.recommended,
  prettierConfig,
)

<script setup lang="ts">
import AppHeader from './AppHeader.vue'
import AppNav from './AppNav.vue'

/**
 * アプリの外枠。
 * スマホ縦持ちの土台をここに閉じ込め、各画面が意識しなくてよいようにする。
 *  - 高さは dvh 基準（アドレスバーの伸縮でナビが流れない）
 *  - スクロールするのは main だけ（grid の 1fr + min-height:0）
 *  - セーフエリア（ノッチ・ホームインジケータ）の余白確保
 */
withDefaults(defineProps<{
  /** 本文の最大幅。1カラム画面は content、2カラム前提の画面は wide */
  width?: 'content' | 'wide'
}>(), { width: 'content' })
</script>

<template>
  <div class="app-shell">
    <AppHeader />

    <main class="app-main">
      <div class="app-content" :class="`app-content-${width}`">
        <slot />
      </div>
    </main>

    <AppNav />
  </div>
</template>

<style scoped>
.app-shell {
  display: grid;
  /* minmax(0, 1fr) がないと main が内容の高さで膨らみ、ナビが画面外へ流れる */
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: 100vh;
  height: 100dvh;
}

.app-main {
  overflow-y: auto;
  /* main の端でスクロールが body へ伝播しないようにする */
  overscroll-behavior: contain;
  /* -webkit-overflow-scrolling: touch は使わない
     （非推奨。合成レイヤーが分離して描画が崩れる。現行ブラウザでは不要） */
  padding: 1rem;
  padding-left: max(1rem, env(safe-area-inset-left, 0px));
  padding-right: max(1rem, env(safe-area-inset-right, 0px));
}

.app-content {
  margin-inline: auto;
  /* ナビとの間に指1本分の余白。最下部の要素がナビに接しないようにする */
  padding-bottom: 0.5rem;
}

.app-content-content {
  max-width: var(--container-content);
}

.app-content-wide {
  max-width: var(--container-wide);
}

@media (min-width: 768px) {
  .app-shell {
    grid-template-columns: var(--size-rail-w) minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
    grid-template-areas:
      'nav header'
      'nav main';
  }

  /* 子コンポーネントのルート要素には親のスコープが付くため、そのまま指定できる */
  .app-header {
    grid-area: header;
  }

  .app-main {
    grid-area: main;
    padding: 1.5rem;
  }

  .app-nav {
    grid-area: nav;
  }
}
</style>

<script setup lang="ts">
/**
 * 全画面共通のカード。
 * 見出しは「細い古金の目印 + 小さめラベル」に統一し、
 * 面の階層（surface-1 / surface-2）で情報の優先順位を出す。
 * カードごとに色付きの上辺ボーダーを付けると全パネルが等価に見えるため行わない。
 */
withDefaults(defineProps<{
  title?: string
  /** セクションとして扱うか（見出しを持つ塊は section にする） */
  as?: 'section' | 'div' | 'article'
  /** 主役のカード。わずかに明るくして視線を集める */
  emphasis?: boolean
  /** 内側の余白を詰める */
  dense?: boolean
}>(), { as: 'section' })
</script>

<template>
  <component :is="as" class="panel" :class="{ 'panel-emphasis': emphasis, 'panel-dense': dense }">
    <header v-if="title || $slots.title || $slots.actions" class="panel-head">
      <h2 class="panel-title">
        <slot name="title">{{ title }}</slot>
      </h2>
      <div v-if="$slots.actions" class="panel-actions">
        <slot name="actions" />
      </div>
    </header>
    <slot />
  </component>
</template>

<style scoped>
.panel {
  background-color: var(--color-surface-1);
  border: 1px solid var(--color-line-soft);
  border-radius: var(--radius-lg);
  padding: 1rem;
  /* 上端の 1px ハイライトで彫り込み感を出す */
  box-shadow: var(--shadow-card), inset 0 1px 0 rgba(242, 239, 228, 0.045);
}

.panel-emphasis {
  background-color: var(--color-surface-2);
  border-color: var(--color-line);
}

.panel-dense {
  padding: 0.75rem;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-bottom: 0.875rem;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-family: var(--font-body);
  font-size: var(--text-label);
  font-weight: 600;
  letter-spacing: 0.12em;
  color: var(--color-content-muted);
}

/* 見出しの左に短い古金の目印を置く。全幅の罫線より情報量を主張しない */
.panel-title::before {
  content: '';
  width: 2px;
  height: 0.875rem;
  border-radius: 1px;
  background-color: var(--color-accent);
  flex: none;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
</style>

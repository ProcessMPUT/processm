<template>
  <div class="kpi-container">
    {{ data.value }}
  </div>
</template>

<style scoped>
.kpi-container {
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--v-primary-darken2);
  font-size: 80px;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";

@Component
export default class Kpi extends Vue {
  @Prop({ default: {} })
  readonly data!: any;
}
</script>

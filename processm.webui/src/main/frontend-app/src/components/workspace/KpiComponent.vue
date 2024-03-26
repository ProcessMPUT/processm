<template>
  <div class="kpi-container">
    {{ data.data.value }}
  </div>
</template>

<style scoped>
.kpi-container {
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
  color: var(--v-primary-darken2);
  font-size: 4em;
  overflow: hidden;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";
import { KpiComponentData } from "@/models/WorkspaceComponent";

@Component
export default class KpiComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: KpiComponentData };
}
</script>

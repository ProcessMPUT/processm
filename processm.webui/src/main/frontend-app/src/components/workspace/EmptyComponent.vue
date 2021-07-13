<template>
  <div class="container">
    <v-select
      v-model="selectedComponentType"
      :items="availableComponents"
      :label="$t('workspace.component.select')"
      dense
    >
      <template slot="item" slot-scope="componentType">
        <v-icon>${{ componentType.item }}Component</v-icon
        >{{ $t(`workspace.component.${kebabize(componentType.item)}`) }}
      </template>
      <template slot="selection" slot-scope="componentType">
        <v-icon>${{ componentType.item }}Component</v-icon
        >{{ $t(`workspace.component.${kebabize(componentType.item)}`) }}
      </template>
    </v-select>
    <v-btn
      :disabled="selectedComponentType == null"
      @click="$emit('type-selected', selectedComponentType)"
      >{{ $t("common.configure") }}</v-btn
    >
  </div>
</template>

<style scoped>
.container {
  display: flex;
  flex-direction: column;
  align-items: center;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component } from "vue-property-decorator";
import { kebabize } from "@/utils/StringCaseConverter";
import { ComponentType } from "@/models/WorkspaceComponent";

@Component
export default class EmptyComponent extends Vue {
  readonly availableComponents = Object.values(ComponentType);
  selectedComponentType: ComponentType | null = null;
  kebabize = kebabize;
}
</script>

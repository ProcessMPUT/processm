<template>
  <div>
    <slot />
    <div v-if="!isAdvancedEmpty">
      <v-btn
        outlined
        text
        @click="displayAdvanced = !displayAdvanced"
        :plain="displayAdvanced"
      >
        {{ $t("data-connector-dialog.advanced") }}
      </v-btn>
      <slot v-if="displayAdvanced" name="advanced"></slot>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from "vue";
import { Component } from "vue-property-decorator";

@Component
export default class SqlConnectionConfiguration extends Vue {
  displayAdvanced = false;

  get isAdvancedEmpty() {
    return !this.$slots["advanced"];
  }
}
</script>

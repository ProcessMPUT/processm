<template>
  <v-combobox
    v-model="valueInternal"
    :loading="loading"
    :items="items"
    :search-input.sync="searchInternal"
    cache-items
    @change="$emit('update:value', valueInternal)"
  ></v-combobox>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";

@Component
export default class ComboBoxWithSearch extends Vue {
  /**
   * The selected value.
   */
  @Prop()
  value = "";
  /**
   * The callback function to search for items.
   */
  @Prop()
  search!: (value: string) => Array<string>;

  valueInternal = this.value;
  searchInternal = "";
  loading = false;

  /**
   * The items in the combo box.
   */
  items: Array<string> = [];

  @Watch("searchInternal")
  async searchUpdate(value: string) {
    this.loading = true;
    try {
      this.items = await this.search(value);
    } finally {
      this.loading = false;
    }
  }
}
</script>

<style scoped></style>

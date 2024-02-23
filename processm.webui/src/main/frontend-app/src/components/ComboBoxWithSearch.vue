<template>
  <v-combobox
    v-model="valueInternal"
    :label="label"
    :loading="loading"
    :items="items"
    :search-input.sync="searchInternal"
    cache-items
    :rules="rules"
    @input="$emit('update:value', valueInternal)"
    :name="name"
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
  @Prop({ default: "" })
  value!: string;
  /**
   * The callback function to search for items.
   */
  @Prop()
  search!: (value: string) => Array<string>;
  @Prop()
  label?: string;
  @Prop()
  rules?: [];
  @Prop()
  name?: string;

  valueInternal = this.value;
  searchInternal = "";
  loading = false;

  /**
   * The items in the combo box.
   */
  items: Array<string> = [];

  @Watch("value")
  updateValue(value: string) {
    this.valueInternal = value;
  }

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

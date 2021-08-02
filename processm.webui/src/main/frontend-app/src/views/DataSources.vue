<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <data-sources-list />
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped></style>

<script lang="ts">
import Vue from "vue";
import { Component } from "vue-property-decorator";
import DataSourcesList from "@/views/DataSourcesList.vue";

@Component({
  components: { DataSourcesList }
})
export default class DataSources extends Vue {}
</script>

<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <data-stores-list />
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped></style>

<script lang="ts">
import Vue from "vue";
import { Component } from "vue-property-decorator";
import DataStoresList from "@/views/DataStoresList.vue";

@Component({
  components: { DataStoresList }
})
export default class DataStores extends Vue {}
</script>

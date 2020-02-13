<template>
  <v-container>
    <v-row>
      <v-col cols="12">
        <grid-layout
          :layout.sync="layout"
          :row-height="30"
          :is-draggable="!locked"
          :is-resizable="!locked"
          :is-mirrored="false"
          :vertical-compact="true"
          :margin="[10, 10]"
          :use-css-transforms="true"
        >
          <grid-item
            v-for="item in layout"
            :x="item.x"
            :y="item.y"
            :w="item.w"
            :h="item.h"
            :i="item.i"
            :key="item.i"
            class="elevation-1"
            >{{ name }} {{ item.i }}</grid-item
          >
        </grid-layout>
        <v-switch v-model="locked" label="Locked" />
      </v-col>
    </v-row>
  </v-container>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Prop } from "vue-property-decorator";
import { GridLayout, GridItem } from "vue-grid-layout";

@Component({
  components: { GridLayout, GridItem }
})
export default class Workspace extends Vue {
  @Prop(String) readonly name: string = "";
  locked = true;

  layout = [
    { x: 0, y: 0, w: 2, h: 2, i: "0" },
    { x: 2, y: 0, w: 2, h: 4, i: "1" },
    { x: 4, y: 0, w: 2, h: 5, i: "2" },
    { x: 6, y: 0, w: 2, h: 3, i: "3" },
    { x: 8, y: 0, w: 2, h: 3, i: "4" },
    { x: 10, y: 0, w: 2, h: 3, i: "5" },
    { x: 0, y: 5, w: 2, h: 5, i: "6" },
    { x: 2, y: 5, w: 2, h: 5, i: "7" },
    { x: 4, y: 5, w: 2, h: 5, i: "8" },
    { x: 6, y: 3, w: 2, h: 4, i: "9" },
    { x: 8, y: 4, w: 2, h: 4, i: "10" },
    { x: 10, y: 4, w: 2, h: 4, i: "11" },
    { x: 0, y: 10, w: 2, h: 5, i: "12" },
    { x: 2, y: 10, w: 2, h: 5, i: "13" }
  ];

  toggleLocked() {
    this.locked = !this.locked;
  }
}
</script>

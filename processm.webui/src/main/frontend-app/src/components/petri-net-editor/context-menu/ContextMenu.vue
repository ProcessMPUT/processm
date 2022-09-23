<template>
  <v-menu
    id="context-menu"
    ref="contextMenu"
    v-model="visible"
    :position-x="x"
    :position-y="y"
  >
    <v-list>
      <v-list-item
        v-for="item in visibleItems"
        :key="item.name"
        light
        v-on:click="() => performButtonAction(item)"
      >
        <v-list-item-title>{{ item.name }}</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-menu>
</template>

<script lang="ts">
import Vue from "vue";
import Component from "vue-class-component";
import { Emit, PropSync } from "vue-property-decorator";
import { ContextMenuItem } from "@/components/petri-net-editor/context-menu/ContextMenuItem";

@Component({ components: {} })
export default class ContextMenu extends Vue {
  @PropSync("items", { default: () => [] })
  _items!: ContextMenuItem[];
  $refs!: {
    contextMenu: Vue;
  };
  private visible: boolean = false;

  x: number = 0;
  y: number = 0;

  private get visibleItems(): ContextMenuItem[] {
    return this._items.filter((item) => item.isVisible);
  }

  @Emit()
  expand(payload: Element) {
    return payload;
  }

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    const scope = this.getScope();
    const contextMenu = this.getContextMenu();

    scope.addEventListener("contextmenu", this.handleContextMenuEvent);

    scope.addEventListener("click", (e: MouseEvent) => {
      // ? close the menu if the user clicks outside it
      if (
        (e.target as Element).parentElement?.parentElement != contextMenu &&
        (e.target as Element).parentElement?.parentElement?.parentElement !=
          contextMenu
      ) {
        this.visible = false;
      }
    });
  }

  getScope(): HTMLElement {
    const scope = this.$el.parentElement;
    if (!(scope instanceof HTMLElement)) {
      throw new TypeError("scope is not a HTMLElement");
    }

    return scope;
  }

  handleContextMenuEvent(event: MouseEvent) {
    event.preventDefault();

    if (event.target instanceof Element) {
      this.$emit("expand", event.target);
    }

    this.x = event.clientX;
    this.y = event.clientY;

    this.visible = true;
  }

  performButtonAction(item: ContextMenuItem) {
    const contextMenu = this.getContextMenu();
    contextMenu.classList.remove("visible");
    item.action();
  }

  private getContextMenu(): HTMLElement {
    return this.$refs.contextMenu.$el as HTMLElement;
  }
}
</script>


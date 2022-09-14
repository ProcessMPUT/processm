<template>
  <v-btn-toggle
    light
    group
    class="elevation-6"
    id="context-menu" ref="contextMenu"
  >
    <v-btn
      light
      v-for="(item, index) in _items"
      v-if="item.isVisible"
      :key="item.name"
      v-on:click="() => performButtonAction(index)"
    >
      {{ item.name }}
    </v-btn>
  </v-btn-toggle>
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
        (e.target as Element).parentElement?.parentElement != contextMenu
        && (e.target as Element).parentElement?.parentElement?.parentElement != contextMenu
      ) {
        contextMenu.classList.remove("visible");
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

    const contextMenu = this.getContextMenu();

    contextMenu.style.top = `${event.offsetY}px`;
    contextMenu.style.left = `${event.offsetX}px`;

    setTimeout(() => {
      contextMenu.classList.add("visible");
    });
  }

  performButtonAction(itemIndex: number) {
    const contextMenu = this.getContextMenu();
    contextMenu.classList.remove("visible");
    this._items[itemIndex].action();
  }

  private getContextMenu(): HTMLElement {
    return (this.$refs.contextMenu as Vue).$el as HTMLElement;
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
#context-menu {
  position: absolute;
  background: white;

  transform: scale(0);
  transform-origin: top left;
  transition: transform 50ms ease-in-out;
}

/*noinspection CssUnusedSymbol*/
#context-menu.visible {
  transform: scale(1);
  transition: transform 200ms ease-in-out;
}
</style>

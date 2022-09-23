<template>
  <v-dialog v-model="visible" max-width="300px" @input="closeDialog">
    <v-card>
      <v-card-title>Edit</v-card-title>
      <v-card-text>
        <v-form>
          <v-text-field v-model="text" label="Name" type="text" />
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="secondary" text @click="closeDialog"> Cancel</v-btn>
        <v-btn color="primary" text @click="this.saveDialogChanges">
          Save
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Component from "vue-class-component";
import Vue from "vue";
import { Emit, Prop } from "vue-property-decorator";
import { SvgTransition } from "@/components/petri-net-editor/svg/SvgTransition";

@Component({ components: {} })
export default class EditTransitionDialog extends Vue {
  private text: string = "";

  private visible: boolean = true;

  @Prop()
  private transition!: SvgTransition;

  @Emit()
  close() {
    /* just empty */
  }

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    this.text = this.transition.text;
  }

  closeDialog() {
    this.visible = false;
    this.$emit("close");
  }

  saveDialogChanges() {
    this.transition.text = this.text;

    this.closeDialog();
  }
}
</script>

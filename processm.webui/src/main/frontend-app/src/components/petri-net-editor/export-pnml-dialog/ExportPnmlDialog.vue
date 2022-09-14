<template>
  <v-dialog
    v-model="visible"
    max-width="300px"
    @input="closeDialog"
  >
    <v-card>
      <v-card-title>Export PNML</v-card-title>
      <v-card-text>
        <v-form>
          <v-text-field
            label="Filename"
            min="4"
            v-model="filename"
          />
        </v-form>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="secondary" text @click="closeDialog">
          Cancel
        </v-btn>
        <v-btn color="primary" text @click="this.export">
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
import { saveAs } from "file-saver";
import { PetriNetState } from "@/components/petri-net-editor/model/PetriNetState";
import { PnmlSerializer } from "@/components/petri-net-editor/pnml/PnmlSerializer";

@Component({ components: {} })
export default class ExportPnmlDialog extends Vue {
  private filename: string = "petriNet.pnml";

  private visible: boolean = true;

  @Prop()
  private state!: PetriNetState;

  @Emit()
  close() {
  }

  closeDialog() {
    this.visible = false;
    this.$emit("close");
  }

  export() {
    const pnml = PnmlSerializer.serialize(this.state, this.filename);
    saveAs(new File([pnml], this.filename, { type: "text/xml;charset=utf-8" }));

    this.closeDialog();
  }
}
</script>

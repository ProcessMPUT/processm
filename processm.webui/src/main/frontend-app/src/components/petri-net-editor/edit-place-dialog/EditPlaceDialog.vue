<template>
  <v-dialog
    v-model="visible"
    max-width="300px"
    @input="closeDialog"
  >
    <v-card>
      <v-card-title>Edit</v-card-title>
      <v-card-text>
        <v-form>
          <v-text-field
            label="Name"
            type="text"
            v-model="text"
          />
          <v-text-field
            label="Token count"
            type="number"
            min="0"
            v-model="tokenCount"
          />
          <v-checkbox
            label="Initial"
            v-model="isPlaceFinal"
            @click="isPlaceInitial && isPlaceFinal ? isPlaceFinal = false : null"
          />
          <v-checkbox
            label="Final"
            v-model="isPlaceFinal"
            @click="isPlaceInitial && isPlaceFinal ? isPlaceInitial = false : null"
          />
        </v-form>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn color="secondary" text @click="this.closeDialog">
          Cancel
        </v-btn>
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
import { SvgPlace } from "@/components/petri-net-editor/svg/SvgPlace";
import { PlaceType } from "@/components/petri-net-editor/model/Place";

@Component({ components: {} })
export default class EditPlaceDialog extends Vue {
  private text: string = "";
  private tokenCount: number = 0;
  private isPlaceInitial: boolean = false;
  private isPlaceFinal: boolean = false;

  private visible: boolean = true;

  @Prop()
  private place!: SvgPlace;

  @Emit()
  close() {
  }

  // noinspection JSUnusedGlobalSymbols
  mounted() {
    this.text = this.place.text;
    this.tokenCount = this.place.tokenCount;
    this.isPlaceInitial = this.place.type == PlaceType.INITIAL;
    this.isPlaceFinal = this.place.type == PlaceType.FINAL;
  }

  closeDialog() {
    this.visible = false;
    this.$emit("close");
  }

  saveDialogChanges() {
    this.place.text = this.text;
    this.place.tokenCount = this.tokenCount;

    if (this.isPlaceInitial) {
      this.place.type = PlaceType.INITIAL;
    } else if (this.isPlaceFinal) {
      this.place.type = PlaceType.FINAL;
    } else {
      this.place.type = PlaceType.NORMAL;
    }

    this.closeDialog();
  }
}
</script>

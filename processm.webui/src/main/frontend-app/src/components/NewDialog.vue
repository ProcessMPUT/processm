<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="400" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("new-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form v-model="isNewNameValid" ref="renameForm">
            <v-row>
              <v-col>
                <v-text-field
                    name="text-new-name"
                    v-model="newName"
                    :label="$t('new-dialog.new-name-placeholder')"
                    :rules="[
                    (v) => !!v || $t('new-dialog.validation.name-empty')
                  ]"
                    required
                />
              </v-col>
            </v-row>
          </v-form>
        </v-container>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="primary darken-1" text @click.stop="cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn
            color="primary darken-1"
            text
            :disabled="!isNewNameValid"
            @click.stop="save"
            name="btn-new-dialog-submit"
        >
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import {Component, Prop} from "vue-property-decorator";

@Component
export default class RenameDialog extends Vue {
  @Prop({default: false})
  readonly value!: boolean;

  isNewNameValid = true;
  newName = "";

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  save() {
    this.$emit("submitted", this.newName);
    this.resetForm();
  }

  private resetForm() {
    (this.$refs.renameForm as HTMLFormElement).reset();
  }
}
</script>

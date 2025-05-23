<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="400" @keydown.esc="cancel">
    <v-card>
      <v-card-title class="headline">
        {{ $t("rename-dialog.dialog-title") }} {{ oldName }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form v-model="isNewNameValid" ref="renameForm">
            <v-row>
              <v-col>
                <v-text-field
                  v-model="newName"
                  name="rename-dialog-new-name"
                  :label="$t('rename-dialog.new-name-placeholder')"
                  :rules="[
                    (v) => !!v || $t('rename-dialog.validation.name-empty')
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

        <v-btn color="primary darken-1" text @click.stop="cancel" name="btn-rename-dialog-cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn
          color="primary darken-1"
          text
          :disabled="!isNewNameValid"
          @click.stop="rename"
          name="btn-rename-dialog-submit"
        >
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop, Watch } from "vue-property-decorator";

@Component
export default class RenameDialog extends Vue {
  @Prop({ default: "" })
  readonly oldName!: string;
  @Prop({ default: false })
  readonly value!: boolean;

  isNewNameValid = true;
  newName = "";

  @Watch("value")
  componentVisibilityChanged(isVisible: boolean) {
    if (isVisible) this.newName = this.oldName;
  }

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  rename() {
    this.$emit("submitted", this.newName);
    this.resetForm();
  }

  private resetForm() {
    (this.$refs.renameForm as HTMLFormElement).reset();
  }
}
</script>

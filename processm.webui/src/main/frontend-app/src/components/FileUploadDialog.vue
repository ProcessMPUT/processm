<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="400">
    <v-card>
      <v-card-title class="headline">
        {{ $t("file-upload-dialog.title") }}
      </v-card-title>
      <v-card-text>
        <v-container>
          <v-form ref="fileUploadForm">
            <v-row>
              <v-col>
                <v-file-input
                  append-icon="file_upload"
                  show-size
                  v-bind="$attrs"
                  :rules="[
                    (v) =>
                      !v ||
                      v.size < sizeLimit ||
                      ` ${$t('file-upload-dialog.validation.size-limit')}
                        ${sizeLimit / 1024 / 1024} MB!`
                  ]"
                  :label="$t('file-upload-dialog.label')"
                  @change="selectFile"
                  accept=".xml,.xml.gz,.xes,.xes.gz,.log,.log.gz"
                ></v-file-input>
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
          text
          color="primary"
          @click.stop="submitFile"
          :disabled="selectedFile == null"
        >
          {{ $t("file-upload-dialog.upload") }}
          <v-progress-circular
            v-show="isUploading"
            indeterminate
            :width="3"
            :size="20"
            color="secondary"
            class="ml-2"
          ></v-progress-circular>
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";

@Component
export default class FileUploadDialog extends Vue {
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly sizeLimit?: number;

  selectedFile: File | null = null;
  isUploading = false;

  cancel() {
    this.$emit("cancelled");
    this.resetForm();
  }

  submitFile() {
    try {
      this.isUploading = true;
      this.$emit("submitted", this.selectedFile);
      this.resetForm();
    } finally {
      this.isUploading = false;
    }
  }

  selectFile(file: File) {
    this.selectedFile = file;
  }

  private resetForm() {
    (this.$refs.fileUploadForm as HTMLFormElement).reset();
  }
}
</script>

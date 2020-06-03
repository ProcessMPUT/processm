<template>
  <v-dialog fullscreen persistent v-model="value">
    <v-card flat tile>
      <v-card-actions>
        <v-btn color="primary darken-1" icon @click.stop="$emit('close')">
          <v-icon>arrow_back</v-icon>
        </v-btn>
        <v-spacer></v-spacer>
        <v-btn color="primary" icon @click.stop="saveChanges">
          <v-icon>save</v-icon>
        </v-btn>
      </v-card-actions>
      <v-card-text>
        <workspace-component
          v-if="isMounted"
          :component-details="componentDetails"
          :interactive="true"
          @view="$emit('view', componentDetails.id)"
          @edit="$emit('edit', componentDetails.id)"
        />
        <v-list subheader>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>Name</v-list-item-title>
              <v-text-field
                v-model="componentDetails.name"
                :rules="[v => !!v || 'Name cannot be empty']"
                required
              />
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>Type</v-list-item-title>
              <v-select
                v-model="componentDetails.type"
                :items="[
                  { text: 'Casual net', value: 'casualNet' },
                  { text: 'KPI', value: 'kpi' }
                ]"
              ></v-select>
            </v-list-item-content>
          </v-list-item>
          <v-list-item>
            <v-list-item-content>
              <v-list-item-title>Name</v-list-item-title>
              <v-text-field
                v-model="componentDetails.data.query"
                :rules="[v => !!v || 'Query cannot be empty']"
                required
              />
            </v-list-item-content>
          </v-list-item>
        </v-list>
      </v-card-text>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.v-card,
.v-card > div:last-child {
  height: 50%;
}
</style>

<script lang="ts">
import Vue from "vue";
import WorkspaceComponent from "./WorkspaceComponent.vue";
import { Component, Prop } from "vue-property-decorator";

@Component({
  components: { WorkspaceComponent }
})
export default class EditComponentView extends Vue {
  @Prop({ default: {} })
  readonly componentDetails!: any;
  @Prop({ default: false })
  readonly value!: boolean;
  isMounted = false;

  mounted() {
    this.isMounted = true;
  }

  saveChanges() {
    console.log("save");
  }
}
</script>

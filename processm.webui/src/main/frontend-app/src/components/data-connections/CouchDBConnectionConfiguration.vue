<template>
  <sql-connection-configuration>
    <template v-slot>
      <v-text-field
        v-model="value['server']"
        :label="$t('data-connector-dialog.server')"
        required
        :rules="hostnameRules"
      ></v-text-field>
      <v-text-field
        v-model="value['port']"
        type="number"
        :label="$t('data-connector-dialog.port')"
        required
        :rules="portRules"
      ></v-text-field>
      <v-text-field
        v-model="value['username']"
        :label="$t('data-connector-dialog.username')"
        required
        :rules="notEmptyRules"
      ></v-text-field>
      <v-text-field
        v-model="value['password']"
        :label="$t('data-connector-dialog.password')"
        required
        type="password"
        :rules="notEmptyRules"
      ></v-text-field>
      <v-text-field
        v-model="value['database']"
        :label="$t('data-connector-dialog.database')"
        :rules="notEmptyRules"
        required
      ></v-text-field>
      <v-checkbox
        v-model="value['https']"
        :label="$t('data-connector-dialog.https')"
        required>
      </v-checkbox>
    </template>
  </sql-connection-configuration>
</template>

<script lang="ts">
import Vue from "vue";
import { Component, Prop } from "vue-property-decorator";
import SqlConnectionConfiguration from "@/components/data-connections/SqlConnectionConfiguration.vue";
import {
  notEmptyRule,
  hostnameOrIpV4Rule,
  portNumberRule
} from "@/utils/FormValidationRules";
import {ConnectionProperties} from "@/openapi";

@Component({
  components: { SqlConnectionConfiguration }
})
export default class CouchDBConnectionConfiguration extends Vue {
  @Prop({
    default: () => {
      return {};
    }
  })
  readonly value!: ConnectionProperties;

  hostnameRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "data-connector-dialog.validation.non-empty-field"
        ).toString()
      ),
    (v: string) =>
      hostnameOrIpV4Rule(
        v,
        this.$t(
          "data-connector-dialog.validation.hostname-field"
        ).toString()
      )
  ];

  portRules = [
    (v: string) =>
      portNumberRule(
        v,
        this.$t("data-connector-dialog.validation.port-field").toString()
      )
  ];

  notEmptyRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "data-connector-dialog.validation.non-empty-field"
        ).toString()
      )
  ];
}
</script>

<template>
  <sql-connection-configuration>
    <template v-slot>
      <v-text-field
        v-model="value['server']"
        :label="$t('add-data-connector-dialog.server')"
        required
        :rules="hostnameRules"
      ></v-text-field>
      <v-text-field
        v-model="value['port']"
        type="number"
        :label="$t('add-data-connector-dialog.port')"
        required
        :rules="portRules"
      ></v-text-field>
      <v-text-field
        v-model="value['username']"
        :label="$t('add-data-connector-dialog.username')"
        required
        :rules="notEmptyRules"
      ></v-text-field>
      <v-text-field
        v-model="value['password']"
        :label="$t('add-data-connector-dialog.password')"
        required
        type="password"
        :rules="notEmptyRules"
      ></v-text-field>
      <v-text-field
        v-model="value['database']"
        :label="$t('add-data-connector-dialog.database')"
        required
      ></v-text-field>
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

@Component({
  components: { SqlConnectionConfiguration }
})
export default class SqlServerConnectionConfiguration extends Vue {
  @Prop({
    default: () => {
      return {};
    }
  })
  readonly value!: Record<string, string>;

  hostnameRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.non-empty-field"
        ).toString()
      ),
    (v: string) =>
      hostnameOrIpV4Rule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.hostname-field"
        ).toString()
      )
  ];

  portRules = [
    (v: string) =>
      portNumberRule(
        v,
        this.$t("add-data-connector-dialog.validation.port-field").toString()
      )
  ];

  notEmptyRules = [
    (v: string) =>
      notEmptyRule(
        v,
        this.$t(
          "add-data-connector-dialog.validation.non-empty-field"
        ).toString()
      )
  ];
}
</script>

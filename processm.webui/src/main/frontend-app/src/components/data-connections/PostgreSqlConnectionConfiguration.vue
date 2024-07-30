<template>
  <sql-connection-configuration>
    <template v-slot>
      <v-text-field
        v-model="value['server']"
        :label="$t('data-connector-dialog.server')"
        required
        :rules="hostnameRules"
        name="postgresql-server"
      ></v-text-field>
      <v-text-field
        v-model="value['port']"
        type="number"
        :label="$t('data-connector-dialog.port')"
        required
        :rules="portRules"
        name="postgresql-port"
      ></v-text-field>
      <v-text-field
        v-model="value['username']"
        :label="$t('data-connector-dialog.username')"
        required
        :rules="notEmptyRules"
        name="postgresql-username"
      ></v-text-field>
      <v-text-field
        v-model="value['password']"
        :label="$t('data-connector-dialog.password')"
        required
        type="password"
        :rules="notEmptyRules"
        name="postgresql-password"
      ></v-text-field>
      <v-text-field
        v-model="value['database']"
        :label="$t('data-connector-dialog.database')"
        required
        name="postgresql-database"
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
export default class PostgreSqlConnectionConfiguration extends Vue {
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

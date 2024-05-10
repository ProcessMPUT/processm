<template>
  <v-dialog v-model="value" @click:outside="cancel" max-width="600">
    <v-card>
      <v-card-title class="headline">
        {{ $t("add-data-connector-dialog.dialog-title") }}
      </v-card-title>
      <v-card-text>
        <v-expansion-panels accordion mandatory v-model="configMode">
          <v-expansion-panel>
            <v-expansion-panel-header>{{ $t("add-data-connector-dialog.use-connection-string") }} </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-form ref="connectionStringForm" lazy-validation>
                <v-text-field
                  v-model="connectionName"
                  :label="$t('add-data-connector-dialog.connector-name')"
                  required
                  :rules="connectionNameRules"
                  name="connection-string-connection-name"
                ></v-text-field>
                <v-text-field
                  v-model="connectionString['connection-string']"
                  outlined
                  hide-details="auto"
                  :label="$t('add-data-connector-dialog.connection-string')"
                  :rules="connectionStringRules"
                  :hint="$t('add-data-connector-dialog.connection-string-hint')"
                  placeholder="jdbc:driver://host:port/database?user=login&password=password"
                  name="connection-string"
                ></v-text-field>
              </v-form>
            </v-expansion-panel-content>
          </v-expansion-panel>
          <v-expansion-panel>
            <v-expansion-panel-header name="header-specify-connection-properties">{{ $t("add-data-connector-dialog.specify-connection-properties") }} </v-expansion-panel-header>
            <v-expansion-panel-content>
              <v-form ref="connectionPropertiesForm" lazy-validation>
                <v-text-field
                  v-model="connectionName"
                  :label="$t('add-data-connector-dialog.connector-name')"
                  required
                  :rules="connectionNameRules"
                  name="connection-name"
                ></v-text-field>

                <v-select
                  v-model="connectionProperties['connection-type']"
                  :items="availableConnectionTypes"
                  :label="$t('add-data-connector-dialog.connection-type')"
                  required
                  name="available-connection-types"
                ></v-select>

                <component :is="connectionTypeComponent" v-if="connectionProperties['connection-type']" v-model="connectionProperties"></component>
              </v-form>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>

        <v-btn color="secondary" text @click.stop="cancel" name="btn-add-data-connector-cancel">
          {{ $t("common.cancel") }}
        </v-btn>

        <v-btn :color="testConnectionButtonColor" :loading="isTestingConnection" :outlined="connectionTestResult != null" text @click.stop="testConnection">
          {{ $t("add-data-connector-dialog.test-connection") }}
        </v-btn>

        <v-btn :loading="isSubmitting" color="primary" text @click.stop="createDataConnector" name="btn-create-data-connector">
          {{ $t("common.save") }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<style scoped>
.v-expansion-panel-content {
  margin: 5px;
}
</style>

<script lang="ts">
import { ConnectionType } from "@/models/DataStore";
import Vue from "vue";
import { Component, Inject, Prop } from "vue-property-decorator";
import PostgreSqlConnectionConfiguration from "@/components/data-connections/PostgreSqlConnectionConfiguration.vue";
import SqlServerConnectionConfiguration from "@/components/data-connections/SqlServerConnectionConfiguration.vue";
import MySqlConnectionConfiguration from "@/components/data-connections/MySqlConnectionConfiguration.vue";
import OracleDatabaseConnectionConfiguration from "@/components/data-connections/OracleDatabaseConnectionConfiguration.vue";
import Db2ConnectionConfiguration from "@/components/data-connections/Db2ConnectionConfiguration.vue";
import DataStoreService from "@/services/DataStoreService";
import { connectionStringFormatRule, notEmptyRule } from "@/utils/FormValidationRules";
import App from "@/App.vue";

enum ConfigurationMode {
  ConnectionString = 0,
  ConnectionProperties = 1
}

@Component({
  components: {
    PostgreSqlConnectionConfiguration,
    SqlServerConnectionConfiguration,
    MySqlConnectionConfiguration,
    OracleDatabaseConnectionConfiguration,
    Db2ConnectionConfiguration
  }
})
export default class AddDataConnectorDialog extends Vue {
  @Inject() app!: App;
  @Inject() dataStoreService!: DataStoreService;
  @Prop({ default: false })
  readonly value!: boolean;
  @Prop()
  readonly dataStoreId?: string;

  connectionNameRules = [(v: string) => notEmptyRule(v, this.$t("add-data-connector-dialog.validation.non-empty-field").toString())];

  connectionName = "";
  connectionProperties: Record<string, string> = {};
  connectionString: Record<string, string> = {};
  isTestingConnection = false;
  isSubmitting = false;
  connectionTestResult: boolean | null = null;
  connectionStringRules = [
    (v: string) => notEmptyRule(v, this.$t("add-data-connector-dialog.validation.non-empty-field").toString()),
    (v: string) => connectionStringFormatRule(v, this.$t("add-data-connector-dialog.validation.connection-string-format").toString())
  ];

  constructor() {
    super();
    this.connectionProperties["connection-type"] = Object.keys(ConnectionType)[0];
  }

  get availableConnectionTypes() {
    return Object.entries(ConnectionType).map(([type, name]) => {
      return { value: type, text: name };
    });
  }

  get connectionTypeComponent() {
    return `${this.connectionProperties["connection-type"]}ConnectionConfiguration`;
  }

  get testConnectionButtonColor() {
    if (this.connectionTestResult == null) return "secondary";

    return this.connectionTestResult ? "green" : "red";
  }

  configMode = ConfigurationMode.ConnectionString;

  cancel() {
    this.$emit("cancelled");
    this.resetForms();
  }

  async createDataConnector() {
    if (this.dataStoreId == null) return this.app.error(this.$t("data-stores.data-store-not-found").toString());
    if (!this.validateForm()) throw new Error("The provided data is invalid");

    try {
      this.isSubmitting = true;
      const dataConnector = await this.dataStoreService.createDataConnector(
        this.dataStoreId,
        this.connectionName,
        this.configMode == ConfigurationMode.ConnectionString ? this.connectionString : this.connectionProperties
      );
      this.app.success(`${this.$t("common.saving.success")}`);
      this.$emit("submitted", dataConnector);
      this.resetForms();
    } catch (error) {
      this.app.error(error);
    } finally {
      this.isSubmitting = false;
    }
  }

  async testConnection() {
    try {
      this.isTestingConnection = true;

      if (this.dataStoreId == null) throw new Error("DataStoreId is not defined");
      if (!this.validateForm()) throw new Error("The provided data is invalid");

      await this.dataStoreService.testDataConnector(
        this.dataStoreId,
        this.configMode == ConfigurationMode.ConnectionString ? this.connectionString : this.connectionProperties
      );
      this.connectionTestResult = true;
      this.app.success(`${this.$t("add-data-connector-dialog.testing.success")}`);
    } catch (e) {
      this.connectionTestResult = null;
      this.app.error(`${this.$t("add-data-connector-dialog.testing.failure")}: ${e.message}`);
    } finally {
      this.isTestingConnection = false;
    }
  }

  private validateForm() {
    return this.configMode == ConfigurationMode.ConnectionString ? this.validateConnectionStringForm() : this.validateConnectionPropertiesForm();
  }

  private validateConnectionStringForm() {
    return (this.$refs.connectionStringForm as HTMLFormElement).validate();
  }

  private validateConnectionPropertiesForm() {
    return (this.$refs.connectionPropertiesForm as HTMLFormElement).validate();
  }

  private resetForms() {
    this.connectionName = "";
    this.connectionProperties = {};
    this.connectionString = {};
    this.connectionTestResult = null;
    (this.$refs.connectionStringForm as HTMLFormElement)?.reset();
    (this.$refs.connectionPropertiesForm as HTMLFormElement)?.reset();
  }
}
</script>

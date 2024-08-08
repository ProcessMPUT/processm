<template>
  <v-container>
    <v-row class="pa-2" justify="center">
      <div>
        <v-img :alt="app.config.brand" max-width="400" src="@/assets/logo-big.png"></v-img>
        <p>{{ $t("about.version", app.config) }}</p>
        <p>
          &copy;2020-{{ Math.max(new Date().getFullYear(), 2024) }} Poznan University of Technology, Poland.<br />
          <a href="#/licensing">Software license</a><br />
          <a href="https://processm.cs.put.poznan.pl">Project home page</a>
        </p>
      </div>
    </v-row>
    <v-divider />
    <v-row justify="center">
      <p class="display-1 my-10">{{ $t("about.acknowledgments") }}</p>
    </v-row>
    <v-divider />
    <v-row class="my-10" justify="center">
      <a href="https://www.ncbr.gov.pl/" rel="nofollow" target="_blank">
        <v-img :alt="$t('about.ncbr')" max-height="120" max-width="166" src="@/assets/NCBR_logo_ENG.png" />
      </a>

      <v-spacer />

      <a href="https://www.put.poznan.pl/" rel="nofollow" target="_blank">
        <v-img :alt="$t('about.put')" max-height="120" max-width="120" src="@/assets/put-logo.png" />
      </a>

      <v-spacer />

      <v-img :alt="$t('about.lider')" max-height="40" max-width="144" src="@/assets/LIDER.jpeg" />
    </v-row>

    <v-row justify="center">
      <h1 class="display-1">{{ $t("about.lider") }}</h1>
    </v-row>

    <v-divider class="my-10" />

    <v-row justify="center">
      <p class="display-1">{{ $t("about.project-title") }}</p>
    </v-row>

    <v-row class="my-5">
      <v-container>
        <v-row>
          <v-col class="text-right">{{ $t("about.beneficiary") }}</v-col>
          <v-col>Poznan University of Technology</v-col>
        </v-row>
        <v-row>
          <v-col class="text-right">{{ $t("about.project-value") }}</v-col>
          <v-col>1 497 592.50 PLN</v-col>
        </v-row>
        <v-row>
          <v-col class="text-right">{{ $t("about.funding-value") }}</v-col>
          <v-col>1 497 592.50 PLN</v-col>
        </v-row>
        <v-row>
          <v-col class="text-right">{{ $t("about.duration") }}</v-col>
          <v-col>2020-01-01 - 2024-09-30</v-col>
        </v-row>
        <v-row>
          <v-col class="text-right">{{ $t("about.grant") }}</v-col>
          <v-col>LIDER/14/0086/L-10/18/NCBR/2019</v-col>
        </v-row>
      </v-container>
    </v-row>

    <v-divider class="my-10" />

    <v-row justify="center">
      {{ $t("about.note") }}
    </v-row>

    <v-row></v-row>
  </v-container>
</template>

<style scoped></style>
<script lang="ts">
import { Component, Inject } from "vue-property-decorator";
import Vue from "vue";
import App from "@/App.vue";

let inject = Inject;
@Component
export default class About extends Vue {
  @Inject() app!: App;
}
</script>

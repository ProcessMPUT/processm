<template>
  <v-container>
    <v-row align="center">
      <a href="https://www.put.poznan.pl/" rel="nofollow" target="_blank">
        <v-img alt="Poznan University of Technology" max-height="120" max-width="120" src="@/assets/put-logo.png" />
      </a>

      <v-spacer />

      <v-img alt="LIDER Programme" max-height="40" max-width="144" src="@/assets/LIDER.jpeg" />

      <v-spacer />

      <a href="https://www.ncbr.gov.pl/" rel="nofollow" target="_blank">
        <v-img alt="The National Centre for Research and Development" max-height="120" max-width="166" src="@/assets/NCBR_logo_ENG.png" />
      </a>
    </v-row>

    <v-row justify="center">
      <p class="display-1">ProcessM: Real-Time Intelligent Process Mining Software</p>
    </v-row>

    <v-row class="my-5">
      <p class="text-justify">
        A business process, understood as a set of activities in dependency relation leading to the achievement of a business goal, is an essential part of
        every enterprise. Effective management of business processes in medium and large enterprises is impossible without the support of dedicated software.
        The aim of the project is to develop ProcessM, a fully-functional software product for modeling, monitoring, analysis and optimization of business
        processes. ProcessM is not an Enterprise Resource Planning (ERP) software, but an artificial intelligence tool supporting business process management.
        ProcessM seamlessly integrates with ERP tools and any other data sources thanks to the use of machine learning methods that support extraction,
        transformation and loading (ETL) of data. ProcessM models the business process in online mode based on the incoming in real-time events from the ERP
        system, verifies the conformance of the process with the model, classifies detected deviations from the model into errors and concept drift, reports
        deviations together with a root cause analysis and proposes changes to the process to optimize its performance. ProcessM is a web-service that works in
        a continuous mode and does the above tasks unattended. Detected deviations in the operation of the process are immediately reported to the user. The
        web-service of ProcessM is compatible with Windows, Linux and MacOS, and the client of ProcessM works in a web-browser running on virtually any
        operating system.
      </p>
    </v-row>

    <v-divider />
    <v-row align="center">
      <v-col align="center">
        <p>
          Duration: 1 January&nbsp;2020 – 30&nbsp;September&nbsp;2024<br />
          &copy;2020-{{ Math.max(new Date().getFullYear(), 2024) }} Poznan University of Technology, Poland.<br />
          <a href="https://processm.cs.put.poznan.pl">Project home page</a>
          <v-spacer></v-spacer>
          <a href="#/licensing">Software license</a>
        </p>
      </v-col>
      <v-col align="center">
        The project is financed by The National Centre for Research and Development under the LIDER Programme, grant
        <br />№ LIDER/14/0086/L-10/18/NCBR/2019.
      </v-col>
    </v-row>

    <v-row> </v-row>
  </v-container>
</template>

<style scoped></style>
<script lang="ts" setup></script>

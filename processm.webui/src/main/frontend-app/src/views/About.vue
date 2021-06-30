<template>
  <v-container>
    <v-row>
      <div id="put-image" class="float-left">
        <a href="https://www.put.poznan.pl/" rel="nofollow" target="_blank">
          <v-img
            src="@/assets/put-logo.png"
            alt="Poznan University of Technology"
          />
        </a>
      </div>

      <v-spacer />

      <div id="ncbir-image" class="float-right">
        <a href="https://www.ncbr.gov.pl/" rel="nofollow" target="_blank">
          <v-img
            src="https://www.ncbr.gov.pl/fileadmin/user_upload/import/files/ncbr_logo_z_czerwonym_napisem_eng_q.jpg"
            alt="The National Centre for Research and Development"
          />
        </a>
      </div>
    </v-row>

    <v-row justify="center">
      <p class="display-1">
        ProcessM: Real-Time Intelligent Process Mining Software
      </p>
    </v-row>

    <v-row class="my-5">
      <p class="text-justify px-12">
        A business process, understood as a set of activities in dependency
        relation leading to the achievement of a business goal, is an essential
        part of every enterprise. Effective management of business processes in
        medium and large enterprises is impossible without the support of
        dedicated software. The aim of the project is to develop ProcessM, a
        fully-functional software product for modeling, monitoring, analysis and
        optimization of business processes. ProcessM is not an Enterprise
        Resource Planning (ERP) software, but an artificial intelligence tool
        supporting business process management. ProcessM seamlessly integrates
        with ERP tools and any other data sources thanks to the use of machine
        learning methods that support extraction, transformation and loading
        (ETL) of data. ProcessM models the business process in online mode based
        on the incoming in real-time events from the ERP system, verifies the
        conformance of the process with the model, classifies detected
        deviations from the model into errors and concept drift, reports
        deviations together with a root cause analysis and proposes changes to
        the process to optimize its performance. ProcessM is a web-service that
        works in a continuous mode and does the above tasks unattended. Detected
        deviations in the operation of the process are immediately reported to
        the user. The web-service of ProcessM is compatible with Windows, Linux
        and MacOS, and the client of ProcessM works in a web-browser running on
        virtually any operating system.
      </p>
    </v-row>

    <v-divider />
    <v-row align="center">
      <v-col align="center">
        <p>Duration: 1&nbsp;January&nbsp;2020 – 31&nbsp;December&nbsp;2022</p>
      </v-col>
      <v-col align="center">
        The project is financed by The National Centre for Research and
        Development under the LIDER Programme, grant <br />№
        LIDER/14/0086/L-10/18/NCBR/2019.
      </v-col>
    </v-row>
  </v-container>
</template>

<style scoped>
#put-image {
  width: 120px;
  padding: 10px;
}

#ncbir-image {
  width: 350px;
  padding: 15px;
}

@media screen and (max-width: 600px) {
  #ncbir-image {
    width: 240px;
    margin-top: 20px;
  }
}
</style>

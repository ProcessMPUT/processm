<template>
  <div ref="container" class="vue-bpmn-diagram-container"></div>
</template>

<style src="bpmn-js/dist/assets/diagram-js.css"></style>
<style src="bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css"></style>
<style scoped>
.vue-bpmn-diagram-container {
  position: relative;
  height: 100%;
}
</style>

<script lang="ts">
import Vue from "vue";
import { Component, Inject, Prop, Watch } from "vue-property-decorator";
import App from "@/App.vue";
import Viewer from "bpmn-js";
import Modeler from "bpmn-js/lib/Modeler";
import i18npl from "bpmn-js-i18n/translations/pl";
import { ComponentMode } from "@/components/workspace/WorkspaceComponent.vue";

const translations = new Map();
translations.set("pl", i18npl);

const customTranslateModule = {
  locale: "en",
  translate: [
    "value",
    (template: string, replacements: { [key: string]: string }) => {
      replacements = replacements || {};

      const lang = customTranslateModule.locale;
      const trans = translations.get(lang) as
        | {
            [key: string]: string | undefined;
          }
        | undefined;
      // Translate
      if (trans !== undefined) template = trans[template] || template;

      // Replace
      return template.replace(/{([^}]+)}/g, (_, key) => {
        return replacements[key] || "{" + key + "}";
      });
    }
  ]
};

@Component
export default class BPMNComponent extends Vue {
  @Prop({ default: {} })
  readonly data!: { data: { xml: string } | undefined };
  @Prop({ default: null })
  readonly componentMode?: ComponentMode;
  @Prop({ default: false })
  readonly updateData = false;
  @Inject()
  private app!: App;
  private bpmn?: Viewer | Modeler;

  mounted() {
    const container = this.$refs.container;

    const xml = this.data.data?.xml;
    console.log(xml);
    if (xml == undefined) return;
    const options = {
      diagramXML: xml,
      propertiesPanel: {},
      additionalModules: [
        (() => {
          customTranslateModule.locale = this.$i18n.locale;
          return customTranslateModule;
        })()
      ],
      moddleExtensions: []
    };

    const _options = Object.assign({ container: container as object }, options);
    let ctor;
    if (this.componentMode != ComponentMode.Edit) ctor = Viewer;
    else ctor = Modeler;
    this.bpmn = new ctor(_options);
    this.bpmn.importXML(_options.diagramXML);

    this.bpmn.on("import.done", (event: any) => {
      const error = event.error;
      const warnings = event.warnings;

      if (error) {
        this.app.error(error);
      } else {
        this.app.warn(warnings);
      }

      this.bpmn?.get("canvas").zoom("fit-viewport", "auto");
    });
  }

  @Watch("updateData")
  async saveXML() {
    if (this.updateData) {
      const data: any = await this.bpmn?.saveXML({ formatted: true });
      this.data.data = { xml: data.xml };
    }
  }

  beforeDestroy() {
    this.bpmn?.destroy();
  }
}
</script>

<template>
  <v-btn light
         color="primary"
         class="ma-2"
         type="file"
         @click="selectExperimentFiles">
    Run experiment
    <input ref="importExperimentInput"
           hidden
           type="file"
           accept=".pnml"
           @change="runExperiment"
           multiple
    >
  </v-btn>
</template>

<script lang="ts">
import Component from "vue-class-component";
import Vue from "vue";
import { PropSync } from "vue-property-decorator";
import JSZip from "jszip";
import { saveAs } from "file-saver";
import { Layouter } from "@/components/petri-net-editor/layouter/interfaces/Layouter";
import { PetriNetSvgManager } from "@/components/petri-net-editor/svg/PetriNetSvgManager";
import { PnmlSerializer } from "@/components/petri-net-editor/pnml/PnmlSerializer";

@Component({ components: {} })
export default class RunExperimentButton extends Vue {
  @PropSync("layouter")
  private _layouter!: Layouter;

  @PropSync("petriNetManager")
  private _petriNetManager!: PetriNetSvgManager;

  selectExperimentFiles(): void {
    (this.$refs.importExperimentInput as HTMLElement).click();
  }

  private async runExperiment(event: Event) {
    const eventTarget = event.currentTarget! as HTMLInputElement;
    const files = eventTarget.files!;

    const zip = new JSZip();
    for (let i = 0; i < files.length; i++) {
      const file = files.item(i)!;

      const fileContent = await new Promise(resolve => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.readAsText(file);
      }) as string;

      const filename = file.name.replace(".pnml", "");

      this._petriNetManager.state = PnmlSerializer.deserialize(fileContent);
      this._layouter.clearOverlay();
      this._petriNetManager.state = this._layouter.run(this._petriNetManager.state);

      const metrics = this.calculateMetrics();
      zip.file(`${filename}_metrics.json`, JSON.stringify(metrics));
      const svgWithBlocks = document.getElementById("petri-net-editor-svg")!.outerHTML;
      zip.file(`${filename}_with_blocks.svg`, svgWithBlocks);

      this._layouter.clearOverlay();
      const svgWithoutBlocks = document.getElementById("petri-net-editor-svg")!.outerHTML;
      zip.file(`${filename}_without_blocks.svg`, svgWithoutBlocks);
    }

    const zipContent = await zip.generateAsync({ type: "blob" });
    saveAs(new File([zipContent], "result.zip", { type: "application/zip" }));

    eventTarget.value = "";
  }

  private calculateMetrics() {
    return {
      numberOfIntersectingArcs: this._petriNetManager.getNumberOfIntersectingArcs(),
      hierarchyDepth: this._petriNetManager.getHierarchyDepth(),
      numberOfLayers: this._petriNetManager.getNumberOfLayers(),
      branchingFactor: this._petriNetManager.getBranchingFactor()
    };
  }

}
</script>

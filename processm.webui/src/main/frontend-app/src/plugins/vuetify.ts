import Vue from "vue";
import Vuetify from "vuetify/lib";
import XesEventIcon from "@/components/icons/XesEventIcon.vue";
import XesTraceIcon from "@/components/icons/XesTraceIcon.vue";
import XesLogIcon from "@/components/icons/XesLogIcon.vue";
import KpiIcon from "@/components/icons/KpiIcon.vue";
import CausalNetIcon from "@/components/icons/CausalNetIcon.vue";
import BPMNIcon from "@/components/icons/BPMNIcon.vue";
import PetriNetIcon from "@/components/icons/PetriNetIcon.vue";
import GraphIcon from "@/components/icons/GraphIcon.vue";

Vue.use(Vuetify);

export default new Vuetify({
  theme: {
    options: {
      customProperties: true
    }
  },
  icons: {
    iconfont: "md",
    values: {
      xesEvent: {
        component: XesEventIcon
      },
      xesTrace: {
        component: XesTraceIcon
      },
      xesLog: {
        component: XesLogIcon
      },
      kpiComponent: {
        component: KpiIcon
      },
      alignerKpiComponent: {
        component: KpiIcon
      },
      causalNetComponent: {
        component: CausalNetIcon
      },
      petriNetComponent: {
        component: PetriNetIcon
      },
      bpmnComponent: {
        component: BPMNIcon
      },
      treeLogViewComponent: {
        component: XesLogIcon
      },
      flatLogViewComponent: {
        component: XesLogIcon
      },
      directlyFollowsGraphComponent: {
        component: GraphIcon
      }
    }
  }
});

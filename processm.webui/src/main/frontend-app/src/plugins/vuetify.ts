import Vue from "vue";
import Vuetify from "vuetify/lib";
import XesEventIcon from "@/components/icons/XesEventIcon.vue";
import XesTraceIcon from "@/components/icons/XesTraceIcon.vue";
import XesLogIcon from "@/components/icons/XesLogIcon.vue";
import KpiIcon from "@/components/icons/KpiIcon.vue";
import CausalNetIcon from "@/components/icons/CausalNetIcon.vue";

Vue.use(Vuetify);

export default new Vuetify({
  theme: {
    options: {
      customProperties: true
    }
  },
  icons: {
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
      causalNetComponent: {
        component: CausalNetIcon
      }
    }
  }
});

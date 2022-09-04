import Vue, { VNode } from 'vue';
import Dev from './serve.vue';
import PetriNetEditor from '@/entry.esm';
import vuetify from '@/plugins/vuetify';

Vue.use(PetriNetEditor);
Vue.config.productionTip = false;

new Vue({
    vuetify,
    render: (h): VNode => h(Dev)
}).$mount('#app');

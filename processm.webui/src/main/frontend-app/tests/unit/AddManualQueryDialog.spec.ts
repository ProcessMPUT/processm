import "@testing-library/jest-dom";
import Vue from "vue";
import {render, fireEvent, RenderResult} from "@testing-library/vue";
import Vuetify from "vuetify";
import AddManualQueryDialog from "@/components/data-connections/AddManualQueryDialog.vue";
import {describe, beforeEach, it} from '@jest/globals';
import DataConnector from "@/models/DataStore";
import {createLocalVue} from "@vue/test-utils";


// We need to use a global Vue instance, otherwise Vuetify will complain about
// read-only attributes.
// This could also be done in a custom Jest-test-setup file to execute for all tests.
// More info: https://github.com/vuetifyjs/vuetify/issues/4068
//            https://vuetifyjs.com/en/getting-started/unit-testing
Vue.use(Vuetify);


describe("AddManualQueryDialog.vue", () => {

    const localVue = createLocalVue();
    let vuetify: Vuetify;
    let dialog: RenderResult
    let mockApp: { success: jest.Mock<any, any>; error: jest.Mock<any, any>; };


    beforeEach(() => {
        vuetify = new Vuetify();

        //creating an axu div to serve as a wrapper for the component - apparently Dialog needs it
        // https://forum.vuejs.org/t/vuetify-data-app-true-and-problems-rendering-v-dialog-in-unit-tests/27495/2
        const app = document.createElement('div');
        app.setAttribute('data-app', 'true');
        document.body.append(app);

        Vue.prototype.$t = (key: String): String => key;  //shitty mocking i18n, but I have no idea how to handle this better

        let dc1 : DataConnector = {id: "dc1id", name: "dc1"}
        let dc2 : DataConnector = {id: "dc2id", name: "dc2"}

         mockApp = {
            success: jest.fn(),
            error: jest.fn()
        }

        dialog = render(AddManualQueryDialog, {
            localVue,
            vuetify,
            container: app,
            inject: {
                app: {
                    default:  mockApp
                },
                dataStoreService: {
                    default: function () {
                        return undefined;
                    }
                }
            },
            propsData: {
                value: true,
                dataConnectors: [dc1, dc2]
            }
        });
    });

    it("Submit empty dialog", () => {
        //This should use fireEvent.click, but that is an async function and I can't be bothered right now
        expect(dialog.getByTestId("submit").click).toThrowError();
    });

    it("Cancel", async () => {
        //TODO dunno czemu w testach nic nie przestaje byc visible tutaj
        expect(dialog.container.firstChild).toBeVisible()
        await fireEvent.click(dialog.getByTestId("cancel"))
        expect(dialog.container.firstChild).not.toBeVisible()
    });

    it("Submit completed dialog", async () => {
        await fireEvent.update(dialog.getByTestId("etlConfigurationName"), "onion");
        await fireEvent.update(dialog.getByTestId("dataConnector"), "dc1");
        await fireEvent.update(dialog.getByTestId("query"), "curry");
        await fireEvent.update(dialog.getByTestId("refresh"), "120");
        await fireEvent.update(dialog.getByTestId("traceIdColumn"), "carrot");
        await fireEvent.update(dialog.getByTestId("eventIdColumn"), "potato");
        expect(dialog.getByTestId("etlConfigurationName")).toHaveValue("onion");
        await fireEvent.click(dialog.getByTestId("submit"));
        expect(mockApp.success).toBeCalled();
        expect(mockApp.error).not.toBeCalled();
        expect(dialog.getByTestId("etlConfigurationName")).toHaveValue("");
    });
});
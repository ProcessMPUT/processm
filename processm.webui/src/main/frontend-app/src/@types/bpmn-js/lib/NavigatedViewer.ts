declare module "bpmn-js/lib/NavigatedViewer" {
  export default class NavigatedViewer {
    constructor(param?: {
      container: object;
      diagramXML: string;
      propertiesPanel: object;
      additionalModules: Array<object>;
      moddleExtensions: Array<object>;
    });

    importXML(xml: string): undefined;

    saveXML(options: any): Promise<object>;

    on(name: string, callback: Function): undefined;

    get(name: string): any;

    destroy(): undefined;
  }
}

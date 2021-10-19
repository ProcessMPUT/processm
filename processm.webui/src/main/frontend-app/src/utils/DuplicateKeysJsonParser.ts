import clarinet from "clarinet";

class Stack<T> {
  private readonly internalQueue = new Array<T>();

  get isEmpty(): boolean {
    return this.internalQueue.length == 0;
  }

  pop(): T {
    const element = this.internalQueue.pop();

    if (element == null) throw new Error("The stack is empty");

    return element;
  }

  push(element: T) {
    this.internalQueue.push(element);
  }

  peek(): T {
    return this.internalQueue[this.internalQueue.length - 1];
  }
}

export default class DuplicateKeysJsonParser {
  private readonly parser: clarinet.CParser;
  private readonly keysWithArrayValue = new Set([
    "date",
    "boolean",
    "float",
    "int",
    "string",
    "id",
    "classifier",
    "extension"
  ]);
  private rootElement: [] | {} | null = null;
  private unassignedKeysStack = new Stack<string>();
  private processedElementsStack = new Stack<
    | Array<{} | string | boolean | null>
    | { [_: string]: [] | {} | string | boolean | null }
  >();
  private firstError: Error | null = null;

  constructor() {
    this.parser = clarinet.parser();
    this.parser.onerror = (e) => {
      this.firstError = e;
      console.warn(e);
    };

    const parseKey = (key: string) => {
      this.unassignedKeysStack.push(key);
    };

    this.parser.onvalue = (value) => {
      const parentElement = this.processedElementsStack.peek();
      if (Array.isArray(parentElement)) {
        parentElement.push(value);
      } else {
        const lastKey = this.unassignedKeysStack.pop();
        parentElement[lastKey] = value;
      }
    };

    this.parser.onopenobject = (key) => {
      const newObject = {};
      if (!this.processedElementsStack.isEmpty) {
        const parentElement = this.processedElementsStack.peek();

        if (Array.isArray(parentElement)) {
          parentElement.push(newObject);
        } else {
          const lastKey = this.unassignedKeysStack.pop();
          if (this.keysWithArrayValue.has(lastKey)) {
            const parentProperty = (parentElement[lastKey] as Array<{}>) ?? [];

            parentProperty.push(newObject);
            parentElement[lastKey] = parentProperty;
          } else {
            parentElement[lastKey] = newObject;
          }
        }
      } else {
        this.rootElement = newObject;
      }

      this.processedElementsStack.push(newObject);

      if (key != null) {
        parseKey(key);
      }
    };

    this.parser.onkey = parseKey;

    this.parser.oncloseobject = () => {
      this.processedElementsStack.pop();
    };

    this.parser.onopenarray = () => {
      const newObject = new Array<{}>();

      if (this.processedElementsStack.isEmpty) {
        this.rootElement = newObject;
      } else {
        const parentElement = this.processedElementsStack.peek();

        if (Array.isArray(parentElement)) {
          parentElement.push(newObject);
        } else {
          const lastKey = this.unassignedKeysStack.pop();
          if (this.keysWithArrayValue.has(lastKey)) {
            if (parentElement[lastKey] == null) {
              parentElement[lastKey] = newObject;
            }
          } else {
            parentElement[lastKey] = newObject;
          }
        }
      }
      this.processedElementsStack.push(newObject);
    };

    this.parser.onclosearray = () => {
      this.processedElementsStack.pop();
    };
  }

  public parseFromJson(jsonString: string) {
    this.parser.write(jsonString);
    this.parser.close();

    try {
      if (this.firstError != null) throw this.firstError;

      return this.rootElement;
    } finally {
      this.rootElement = null;
      this.firstError = null;
      this.unassignedKeysStack = new Stack();
      this.processedElementsStack = new Stack();
    }
  }
}

import { PetriNetElement } from '@/lib-components/petri-net-editor/model/PetriNetElement';
import { v4 as uuidv4 } from 'uuid';

export interface TransitionOptions {
    id?: string;
    x: number;
    y: number;
    text: string;
    isSilent?: boolean;
}

export class Transition extends PetriNetElement {
    x: number;
    y: number;
    static readonly HEIGHT: number = 50;
    static readonly WIDTH: number = 10;
    readonly isSilent: boolean = false;

    static fromOptions(options: TransitionOptions): Transition {
        if (!options.id) {
            options.id = uuidv4();
        }
        if (options.isSilent == undefined) {
            options.isSilent = false;
        }

        return new Transition(options.x, options.y, options.text, options.id, options.isSilent);
    }

    constructor(
        x: number,
        y: number,
        text: string,
        id: string,
        isSilent: boolean = false
    ) {
        super(id, text);

        this.x = x;
        this.y = y;
        this.isSilent = isSilent;
        this.text = this.isSilent ? 'τ' : text;
    }

    getOptions(): TransitionOptions {
        return {
            id: this.id,
            x: this.x,
            y: this.y,
            text: this.text,
            isSilent: this.isSilent
        };
    }
}

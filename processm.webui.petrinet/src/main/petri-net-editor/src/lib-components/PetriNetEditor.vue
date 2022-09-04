<template>
    <div style='height: 100%'>
        <v-dialog v-model='isEditPlaceDialogVisible' max-width='300px'>
            <v-card>
                <v-card-title>Edit</v-card-title>
                <v-card-text>
                    <v-form>
                        <v-text-field
                            label='Name'
                            type='text'
                            v-model='editDialogName'
                        />
                        <v-text-field
                            label='Token count'
                            type='number'
                            min='0'
                            v-model='editDialogTokenCount'
                        />
                        <v-checkbox
                            v-model='editPlaceInitial'
                            @click='editPlaceInitial && editPlaceFinal ? editPlaceFinal = false : null'
                            label='Initial'
                        />
                        <v-checkbox
                            v-model='editPlaceFinal'
                            @click='editPlaceInitial && editPlaceFinal ? editPlaceInitial = false : null'
                            label='Final'
                        />
                    </v-form>
                </v-card-text>

                <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color='secondary' text @click='isEditPlaceDialogVisible = false'>
                        Cancel
                    </v-btn>
                    <v-btn color='primary' text @click='this.saveEditDialogChanges'>
                        Save
                    </v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>
        <v-dialog v-model='isEditTransitionDialogVisible' max-width='300px'>
            <v-card>
                <v-card-title>Edit</v-card-title>
                <v-card-text>
                    <v-form>
                        <v-text-field
                            label='Name'
                            type='text'
                            v-model='editDialogName'
                        />
                    </v-form>
                </v-card-text>
                <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color='secondary' text @click='isEditTransitionDialogVisible = false'>
                        Cancel
                    </v-btn>
                    <v-btn color='primary' text @click='this.saveEditDialogChanges'>
                        Save
                    </v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>
        <v-dialog v-model='isExportPnmlDialogVisible' max-width='300px'>
            <v-card>
                <v-card-title>Export PNML</v-card-title>
                <v-card-text>
                    <v-form>
                        <v-text-field
                            label='Filename'
                            min='4'
                            v-model='pnmlFilename'
                        />
                    </v-form>
                </v-card-text>
                <v-card-actions>
                    <v-spacer></v-spacer>
                    <v-btn color='secondary' text @click='isExportPnmlDialogVisible = false'>
                        Cancel
                    </v-btn>
                    <v-btn color='primary' text @click='this.exportPnmlFile'>
                        Save
                    </v-btn>
                </v-card-actions>
            </v-card>
        </v-dialog>

        <v-container
            fluid
            pa-0
            elevation-6
            fill-width
            style='z-index: 1'
        >
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='!isDebuggerEnabled'
                   v-on:click='() => runLayouter()'>
                Run layouter
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='!isDebuggerEnabled'
                   v-on:click='() => runDebugger()'>
                Run debugger
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='!isDebuggerEnabled'
                   type='file' v-on:click='() => selectPnmlFile()'>
                Import PNML
                <input ref='importInput'
                       hidden
                       type='file'
                       accept='.pnml'
                       v-on:change='(event) => importPnml(event)'
                >
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='!isDebuggerEnabled'
                   type='file' v-on:click='() => isExportPnmlDialogVisible = true'>
                Export PNML
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='syncedDebug && !isDebuggerEnabled'
                   type='file' v-on:click='() => calculateMetrics()'>
                Calculate metrics
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='syncedDebug && !isDebuggerEnabled'
                   type='file' v-on:click='() => selectExperimentFiles()'>
                Run experiment
                <input ref='importExperimentInput'
                       hidden
                       type='file'
                       accept='.pnml'
                       v-on:change='(event) => runExperiment(event)'
                       multiple
                >
            </v-btn>
            <v-btn light
                   color='primary'
                   class='ma-2'
                   v-if='isDebuggerEnabled'
                   v-on:click='() => isDebuggerEnabled = false'>
                Stop debugger
            </v-btn>
        </v-container>

        <PetriNetDebugger
            v-if='isDebuggerEnabled'
            :state='this.petriNetManager.getState()'
            class='fill-height' />

        <div v-show='!isDebuggerEnabled'
             class='fill-height'>
            <svg
                id='petri-net-editor-svg'
                height='100%'
                width='100%'
            >
                <defs>
                    <marker
                        id='arrow'
                        markerHeight='25'
                        markerUnits='userSpaceOnUse'
                        markerWidth='25'
                        orient='auto-start-reverse'
                        refX='0'
                        refY='5'
                        viewBox='0 0 10 10'
                    >
                        <path d='M 0 0 L 10 5 L 0 10 z' />
                    </marker>
                </defs>

                <g class='arcs'></g>
                <g class='transitions'></g>
                <g class='places'></g>
            </svg>
            <context-menu
                v-show='!isDebuggerEnabled'
                ref='contextMenu'
                :items='this.contextMenuItems'
                @expand='this.onContextMenuExpand'
            />
        </div>
    </div>
</template>

<script lang='ts'>
import * as d3 from 'd3-selection';
import Component from 'vue-class-component';
import Vue from 'vue';
import ContextMenu from '@/components/context-menu/ContextMenu.vue';
import type { ContextMenuItem } from '@/components/context-menu/ContextMenuItem';
import { PetriNetSvgManager } from '@/lib-components/petri-net-editor/svg/PetriNetSvgManager';
import { ArcDto, PlaceDto, TransitionDto } from '@/lib-components/petri-net-editor/Dto';
import { PropSync } from 'vue-property-decorator';
import { PlaceType } from '@/lib-components/petri-net-editor/model/Place';
import { BlockLayouter } from '@/lib-components/petri-net-editor/layouter/BlockLayouter';
import { SvgPlace } from '@/lib-components/petri-net-editor/svg/SvgPlace';
import { SvgTransition } from '@/lib-components/petri-net-editor/svg/SvgTransition';
import { Layouter } from '@/lib-components/petri-net-editor/layouter/interfaces/Layouter';
import { PnmlSerializer } from '@/lib-components/petri-net-editor/pnml/PnmlSerializer';
import { saveAs } from 'file-saver';
import PetriNetDebugger from '@/components/petri-net-debugger/PetriNetDebugger.vue';
import JSZip from 'jszip';

@Component({
    components: { PetriNetDebugger, ContextMenu }
})
export default class PetriNetEditor extends Vue {
    isEditPlaceDialogVisible: boolean = false;
    isEditTransitionDialogVisible: boolean = false;
    isExportPnmlDialogVisible: boolean = false;
    isDebuggerEnabled: boolean = false;

    editDialogName: string = '';
    editDialogTokenCount: number = 0;

    editPlaceInitial: boolean = false;
    editPlaceFinal: boolean = false;

    pnmlFilename: string = 'petriNet.pnml';

    targetIsPlaceOrTransition = false;
    targetIsDeletable = false;
    isSetTokenVisible = false;
    isSetWidthVisible = false;
    contextMenuTargetId = '';

    @PropSync('places', { default: () => [] })
    syncedPlaces!: PlaceDto[];

    @PropSync('transitions', { default: () => [] })
    syncedTransitions!: TransitionDto[];

    @PropSync('arcs', { default: () => [] })
    syncedArcs!: ArcDto[];

    @PropSync('debug', { default: () => false })
    syncedDebug!: boolean;

    @PropSync('runLayouterOnStart', { default: () => false })
    syncedRunLayouterOnStart!: boolean;

    contextMenuItems: ContextMenuItem[] = [];

    private petriNetManager!: PetriNetSvgManager;

    private layouter!: Layouter;

    // noinspection JSUnusedGlobalSymbols
    mounted() {
        this.petriNetManager = new PetriNetSvgManager(d3.select('#petri-net-editor-svg'));
        // this.createExamplePetriNet();

        this.layouter = new BlockLayouter(this.syncedDebug);

        this.syncedPlaces.forEach(place => this.petriNetManager.createPlace({
            x: 0,
            y: 0,
            text: place.text,
            tokenCount: 0,
            id: place.id,
            type: place.type
        }));
        this.syncedTransitions.forEach(transition => this.petriNetManager.createTransition({
            x: 0,
            y: 0,
            text: transition.text,
            id: transition.id
        }));
        this.syncedArcs.forEach(arc => this.petriNetManager.connect(arc.outElementId, arc.inElementId));

        if (this.syncedRunLayouterOnStart) {
            this.runLayouter();
        }
        this.petriNetManager.updateDimensions();
    }

    createPlace(): void {
        const contextMenu = (this.$refs.contextMenu as Vue).$el as HTMLElement;

        this.petriNetManager.createPlace({
            x: contextMenu.offsetLeft,
            y: contextMenu.offsetTop,
            text: 'New P'
        });
    }

    createTransition(): void {
        const contextMenu = (this.$refs.contextMenu as Vue).$el as HTMLElement;

        this.petriNetManager.createTransition({
            x: contextMenu.offsetLeft,
            y: contextMenu.offsetTop,
            text: 'New T'
        });
    }

    startConnect(): void {
        this.petriNetManager.startConnect(this.contextMenuTargetId);
    }

    onContextMenuExpand(target: Element | null): void {
        const isPlaceOrTransition =
            target instanceof SVGCircleElement || target instanceof SVGRectElement;

        this.targetIsPlaceOrTransition = isPlaceOrTransition;
        this.targetIsDeletable = isPlaceOrTransition || target instanceof SVGLineElement;
        this.isSetTokenVisible = target instanceof SVGCircleElement;
        this.isSetWidthVisible = target instanceof SVGLineElement;
        this.contextMenuTargetId =
            isPlaceOrTransition || target instanceof SVGLineElement ? target?.id ?? '' : '';

        this.contextMenuItems = this.createContextMenuItems();
    }

    showEditDialog() {
        const element = this.petriNetManager.getElement(this.contextMenuTargetId);
        if (element instanceof SvgPlace) {
            this.editDialogName = element.getText();
            this.editDialogTokenCount = element.getTokenCount();

            this.editPlaceInitial = element.placeModel.type == PlaceType.INITIAL;
            this.editPlaceFinal = element.placeModel.type == PlaceType.FINAL;

            this.isEditPlaceDialogVisible = true;
        } else if (element instanceof SvgTransition) {
            this.editDialogName = element.getText();
            this.isEditTransitionDialogVisible = true;
        }
    }

    saveEditDialogChanges() {
        if (this.isEditPlaceDialogVisible) {
            this.isEditPlaceDialogVisible = false;
            const place = this.petriNetManager.getPlace(this.contextMenuTargetId);
            place.setText(this.editDialogName);
            place.setTokenCount(this.editDialogTokenCount);
            if (this.editPlaceInitial) {
                place.setInitial();
            } else if (this.editPlaceFinal) {
                place.setFinal();
            } else {
                place.setNormal();
            }
        } else if (this.isEditTransitionDialogVisible) {
            this.isEditTransitionDialogVisible = false;
            const transition = this.petriNetManager.getTransition(this.contextMenuTargetId);
            transition.setText(this.editDialogName);
        }
    }

    runLayouter(): void {
        const [isCorrect, message] = this.petriNetManager.getState().isCorrectNet();

        if (!isCorrect) {
            alert(`Can't run layouter!\n${message}`);
            return;
        }

        const newState = this.layouter.run(this.petriNetManager.getState());
        this.petriNetManager.setState(newState);
        this.petriNetManager.updateDimensions();
    }

    runDebugger(): void {
        const [isCorrect, message] = this.petriNetManager.getState().isCorrectNet();

        if (!isCorrect) {
            alert(message);
            return;
        }

        this.isDebuggerEnabled = true;
    }

    selectPnmlFile(): void {
        (this.$refs.importInput as HTMLElement).click();
    }

    importPnml(event: any): void {
        const eventTarget = event.currentTarget! as HTMLInputElement;
        eventTarget.value = '';

        const reader = new FileReader();
        reader.onload = (loadEvent) => {
            const data: string = loadEvent.target!.result as string;
            const newState = PnmlSerializer.deserialize(data);
            this.petriNetManager.setState(newState);
            this.layouter.clearOverlay();
        };

        const file = event.target.files[0];
        reader.readAsText(file);
    }

    exportPnmlFile(): void {
        const pnml = PnmlSerializer.serialize(this.petriNetManager.getState(), this.pnmlFilename);
        saveAs(new File([pnml], this.pnmlFilename, { type: 'text/xml;charset=utf-8' }));
        this.isExportPnmlDialogVisible = false;
    }

    private createContextMenuItems(): ContextMenuItem[] {
        return [
            {
                name: 'Connect',
                isVisible: this.targetIsPlaceOrTransition,
                action: () => this.startConnect()
            },
            {
                name: 'Create place',
                isVisible: this.contextMenuTargetId === '',
                action: () => this.createPlace()
            },
            {
                name: 'Create transition',
                isVisible: this.contextMenuTargetId === '',
                action: () => this.createTransition()
            },
            {
                name: 'Edit',
                isVisible: this.targetIsPlaceOrTransition,
                action: () => this.showEditDialog()
            },
            {
                name: 'Delete',
                isVisible: this.targetIsDeletable,
                action: () => this.removeElementOrArc()
            }
        ];
    }

    private removeElementOrArc() {
        if (this.petriNetManager.hasElement(this.contextMenuTargetId)) {
            this.petriNetManager.removeElement(this.contextMenuTargetId);
        } else {
            this.petriNetManager.removeArc(this.contextMenuTargetId);
        }
    }

    private calculateMetrics() {
        const metrics = {
            numberOfIntersectingArcs: this.petriNetManager.getNumberOfIntersectingArcs(),
            hierarchyDepth: this.petriNetManager.getHierarchyDepth(),
            numberOfLayers: this.petriNetManager.getNumberOfLayers(),
            branchingFactor: this.petriNetManager.getBranchingFactor()
        };

        console.log(metrics);
        return metrics;
    }

    selectExperimentFiles(): void {
        (this.$refs.importExperimentInput as HTMLElement).click();
    }

    private async runExperiment(event: Event) {
        const eventTarget = event.currentTarget! as HTMLInputElement;
        eventTarget.value = '';

        const files = eventTarget.files!;
        const zip = new JSZip();
        for (let i = 0; i < files.length; i++) {
            const file = files.item(i)!;

            const fileContent = await new Promise(resolve => {
                const reader = new FileReader();
                reader.onload = () => resolve(reader.result as string);
                reader.readAsText(file);
            }) as string;

            const filename = file.name.replace('.pnml', '');

            const newState = PnmlSerializer.deserialize(fileContent);
            this.petriNetManager.setState(newState);
            this.layouter.clearOverlay();
            const layouterState = this.layouter.run(this.petriNetManager.getState());
            this.petriNetManager.setState(layouterState);

            const metrics = this.calculateMetrics();
            zip.file(`${filename}_metrics.json`, JSON.stringify(metrics));
            const svgWithBlocks = document.getElementById('petri-net-editor-svg')!.outerHTML;
            zip.file(`${filename}_with_blocks.svg`, svgWithBlocks);

            this.layouter.clearOverlay();
            const svgWithoutBlocks = document.getElementById('petri-net-editor-svg')!.outerHTML;
            zip.file(`${filename}_without_blocks.svg`, svgWithoutBlocks);
        }

        const zipContent = await zip.generateAsync({ type: 'blob' });
        saveAs(new File([zipContent], 'result.zip', { type: 'application/zip' }));
    }
}
</script>

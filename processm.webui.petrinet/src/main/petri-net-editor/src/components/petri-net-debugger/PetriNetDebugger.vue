<template>
    <div>
        <svg
            id='petri-net-debugger-svg'
            height='100%'
            width='100%'
            style='background-color: white'
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
            :items='this.contextMenuItems'
            @expand='this.onContextMenuExpand'
        />
    </div>
</template>

<script lang='ts'>
import Component from 'vue-class-component';
import Vue from 'vue';
import { PropSync } from 'vue-property-decorator';
import { PetriNetSvgManager } from '@/lib-components/petri-net-editor/svg/PetriNetSvgManager';
import ContextMenu from '@/components/context-menu/ContextMenu.vue';
import { PetriNetState } from '@/lib-components/petri-net-editor/model/PetriNetState';
import * as d3 from 'd3-selection';
import { PlaceType } from '@/lib-components/petri-net-editor/model/Place';
import { ContextMenuItem } from '@/components/context-menu/ContextMenuItem';

@Component({
    components: { ContextMenu }
})
export default class PetriNetDebugger extends Vue {
    @PropSync('state')
    readonly syncedState!: PetriNetState;

    private petriNetManager!: PetriNetSvgManager;

    private contextMenuItems: ContextMenuItem[] = [];

    private targetIsInvokable = false;
    private targetIsBackwardInvokable = false;
    private contextMenuTargetId = '';

    // noinspection JSUnusedGlobalSymbols
    mounted() {
        this.petriNetManager = new PetriNetSvgManager(d3.select('#petri-net-debugger-svg'));
        this.petriNetManager.setState(this.syncedState);
        this.petriNetManager.updateDimensions();
        this.petriNetManager.disableDragging();

        const places = this.petriNetManager.getPlaces();

        const totalTokenCount = places.reduce((sum, place) => sum + place.getTokenCount(), 0);

        for (const place of this.petriNetManager.getPlaces()) {
            const preTransitions = this.petriNetManager.getState().getPreTransitions(place.placeModel);
            const postTransitions = this.petriNetManager.getState().getPostTransitions(place.placeModel);
            if (preTransitions.length == 0) {
                place.setInitial();
            } else if (postTransitions.length == 0) {
                place.setFinal();
            } else {
                place.setNormal();
            }
        }

        if (totalTokenCount == 0) {
            for (const place of places) {
                place.setTokenCount(0);
            }
            places.filter(place => place.placeModel.type == PlaceType.INITIAL)
                .forEach(place => place.setTokenCount(1));
        }

        d3.selectAll('#petri-net-debugger-svg *')
            .on('mousedown.drag', null);

        this.highlightInvokableTransitions();
    }


    onContextMenuExpand(target: Element | null): void {
        this.targetIsInvokable = target?.hasAttribute('invokable') ?? false;
        this.targetIsBackwardInvokable = target?.hasAttribute('backwardsInvokable') ?? false;
        this.contextMenuTargetId = target?.id ?? '';

        this.contextMenuItems = this.createContextMenuItems();
    }

    private fireTransition(backwards: boolean) {
        if (this.contextMenuTargetId == '') {
            return;
        }

        const transition = this.petriNetManager.getTransition(this.contextMenuTargetId);
        const prePlaces = this.petriNetManager.getState().getPrePlaces(transition.transitionModel);
        const postPlaces = this.petriNetManager.getState().getPostPlaces(transition.transitionModel);

        for (const place of !backwards ? prePlaces : postPlaces) {
            this.petriNetManager.getPlace(place.id)
                .setTokenCount(place.getTokenCount() - 1);
        }

        for (const place of !backwards ? postPlaces : prePlaces) {
            this.petriNetManager.getPlace(place.id)
                .setTokenCount(place.getTokenCount() + 1);
        }

        this.highlightInvokableTransitions();
    }

    private highlightInvokableTransitions() {
        const transitions = this.petriNetManager.getTransitions();
        for (const transition of transitions) {
            const isInvokable = this.petriNetManager.getState()
                .getPrePlaces(transition.transitionModel)
                .every(place => place.getTokenCount() > 0);

            const isBackwardsInvokable = this.petriNetManager.getState()
                .getPostPlaces(transition.transitionModel)
                .every(place => place.getTokenCount() > 0);

            transition.setHighlight(isInvokable || isBackwardsInvokable);
            transition.setInvokable(isInvokable);
            transition.setBackwardsInvokable(isBackwardsInvokable);
        }
    }

    private createContextMenuItems(): ContextMenuItem[] {
        return [
            {
                name: 'Fire',
                isVisible: this.targetIsInvokable,
                action: () => this.fireTransition(false)
            },
            {
                name: 'Fire backwards',
                isVisible: this.targetIsBackwardInvokable,
                action: () => this.fireTransition(true)
            }
        ];
    }
}
</script>

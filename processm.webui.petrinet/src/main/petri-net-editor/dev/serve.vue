<template>
    <v-app id='app' data-app>
        <petri-net-editor
            :debug='true'
            :run-layouter-on-start='true'
            :places='this.places'
            :transitions='this.transitions'
            :arcs='this.arcs' />
    </v-app>
</template>

<script lang='ts'>
import Vue from 'vue';
import PetriNetEditor from '@/lib-components/PetriNetEditor.vue';
import Component from 'vue-class-component';
import { ArcDto, PlaceDto, TransitionDto } from '@/lib-components/petri-net-editor/Dto';
import { v4 as uuidv4 } from 'uuid';
import { PlaceType } from '@/lib-components/petri-net-editor/model/Place';

@Component({ components: { PetriNetEditor } })
export default class Dev extends Vue {
    places: PlaceDto[] = [
        { id: uuidv4(), text: 'P1', type: PlaceType.INITIAL },
        { id: uuidv4(), text: 'P2' },
        { id: uuidv4(), text: 'P3', type: PlaceType.FINAL }
    ];
    transitions: TransitionDto[] = [
        { id: uuidv4(), text: 'T1' }
    ];
    arcs: ArcDto[] = [
        { outElementId: this.places[0].id, inElementId: this.transitions[0].id },
        { outElementId: this.transitions[0].id, inElementId: this.places[1].id },
        { outElementId: this.transitions[0].id, inElementId: this.places[2].id }
    ];
}
</script>

<style>
@import "../src/assets/base.css";
</style>

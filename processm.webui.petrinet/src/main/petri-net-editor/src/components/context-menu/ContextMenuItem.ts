export interface ContextMenuItem {
    name: string;
    isVisible: boolean;

    action(): void;
}

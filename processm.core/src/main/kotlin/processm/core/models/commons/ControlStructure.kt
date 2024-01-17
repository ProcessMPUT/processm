package processm.core.models.commons

/**
 * Represents a control flow structure in a process model. The interpretation depends on the model type.
 */
interface ControlStructure {
    /**
     * The type of the control flow structure.
     */
    val type: ControlStructureType

    /**
     * The number of states that this control structure creates or joins.
     * AND - 1, XOR - n, OR - 2^n
     * See George Cardoso, Control-flow Complexity Measurement of Processes and Weyuker’s Properties, Proceedings of World
     * Academy of Science, Engineering and technology Volume 8 October 2005 ISSN 1307-6884
     */
    val controlFlowComplexity: Int
}

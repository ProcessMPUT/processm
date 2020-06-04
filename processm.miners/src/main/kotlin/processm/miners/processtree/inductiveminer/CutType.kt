package processm.miners.processtree.inductiveminer

enum class CutType {
    Exclusive,
    Sequence,
    Parallel,
    RedoLoop,
    Activity,
    OptionalActivity,
    RedoActivityAtLeastOnce,
    RedoActivityAtLeastZeroTimes,
    FlowerModel
}
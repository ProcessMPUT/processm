package processm.helpers

/**
 * Returns a triple consisting of the same components as [this], but ordered such that `first <= second <= third`
 * @return [this] if the triple is already sorted, a new object otherwise
 */
fun <T : Comparable<T>> Triple<T, T, T>.sort(): Triple<T, T, T> =
    if (first <= second) {
        if (second <= third) {
            this
        } else {
            if (first <= third) {
                Triple(first, third, second)
            } else {
                Triple(third, first, second)
            }
        }
    } else {
        if (third <= second) {
            Triple(third, second, first)
        } else {
            if (first < third)
                Triple(second, first, third)
            else
                Triple(second, third, first)
        }
    }

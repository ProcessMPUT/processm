package processm.core.log.attribute

class LazyString(vararg val backend: CharSequence) : CharSequence {
    override val length: Int

    private val hash: Int

    init {
        val str= toString()
        length = str.length
        hash = str.hashCode()
    }

    operator fun iterator(): CharIterator = object : CharIterator() {

        private val backendIterator = backend.iterator()
        private var partIterator = backendIterator.next().iterator()

        override fun hasNext(): Boolean {
            while (!partIterator.hasNext()) {
                if (!backendIterator.hasNext())
                    return false
                partIterator = backendIterator.next().iterator()
            }
            return partIterator.hasNext()
        }

        override fun nextChar(): Char {
            return partIterator.nextChar()
        }

    }

    override fun get(index: Int): Char {
        var i = index
        for (part in backend) {
            if (i >= part.length)
                i -= part.length
            else
                return part[i]
        }
        throw IndexOutOfBoundsException()
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        require(startIndex <= endIndex)
        require(startIndex >= 0)
        require(endIndex <= length)
        if (startIndex == endIndex)
            return ""
        if (startIndex <= 0 && endIndex >= length)
            return this
        var startIdx = startIndex
        var endIdx = endIndex
        var startPart: Int = -1
        for (idx in backend.indices) {
            val part = backend[idx]
            if (startIdx >= part.length) {
                startIdx -= part.length
                endIdx -= part.length
            } else {
                startPart = idx
                break
            }
        }
        if (startPart < 0)
            throw IndexOutOfBoundsException()
        var endPart = -1
        for (idx in startPart until backend.size) {
            val part = backend[idx]
            if (endIdx >= part.length)
                endIdx -= part.length
            else {
                endPart = idx
                break
            }
        }
        if (startPart == endPart) {
            return backend[startPart].subSequence(startIdx, endIdx)
        } else {
            if (endPart >= 0) {
                assert(startPart < endPart)
                val n = endPart - startPart + 1
                return LazyString(*Array(n) {
                    when (it) {
                        0 -> backend[startPart].subSequence(startIdx, backend[startPart].length)
                        n - 1 -> backend[endPart].subSequence(0, endIdx)
                        else -> backend[startPart + it]
                    }
                })
            } else {
                val n = backend.size - startPart
                return LazyString(*Array(n) {
                    when (it) {
                        0 -> backend[startPart].subSequence(startIdx, backend[startPart].length)
                        else -> backend[startPart + it]
                    }
                })
            }
        }
    }

    override fun toString(): String {
        with(StringBuilder()) {
            backend.forEach { append(it.toString()) }
            return toString()
        }
    }

    override fun hashCode(): Int = hash

    override fun equals(other: Any?): Boolean {
        if(this === other)
            return true
        if (other is CharSequence && length == other.length) {
            val i = iterator()
            val j = other.iterator()
            while (i.hasNext()) {
                assert(j.hasNext())
                if (i.next() != j.next())
                    return false
            }
            return true
        }
        return false
    }
}

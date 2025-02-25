package processm.core.models.bpmn

import jakarta.xml.bind.JAXBElement
import jakarta.xml.bind.annotation.*
import org.w3c.dom.Element
import processm.core.models.bpmn.jaxb.TDefinitions
import processm.logging.logger
import java.lang.reflect.Field
import javax.xml.namespace.QName

/**
 * A helper class to recursively compare two JAXB objects.
 * Heavily uses reflections to detect fields, and then does a bit of guessing to detect appropriate getters.
 * Supports whitespace-agnostic string comparison by replacing any sequence of whitespaces (incl. new lines etc.) to a single space before the comparison.
 *
 * This is somewhat horrible, but:
 * * XmlUnit was way too picky, even configured to ignore more or less everything
 * * Equals and Simple Equals plugins from [JAXB2-Basics](https://github.com/highsource/jaxb2-basics) were throwing StackOverflowError like crazy
 */
internal class JaxbRecursiveComparer(val ignoreNSInQName: Boolean = false) {

    private val seen = HashSet<Pair<Any, Any>>()

    private fun processProp(left: Any, right: Any, prop: String, clazz: Class<in Any>): Boolean {
        val getters = clazz.declaredMethods
                .filter { m -> m.canAccess(left) && m.canAccess(right) }
                .filter { m -> m.parameterCount == 0 }
                .filter { m ->
                    setOf(
                            "get${prop.toLowerCase()}",
                            "is${prop.toLowerCase()}",
                            "has${prop.toLowerCase()}"
                    ).any { m.name.equals(it, true) }
                }
        logger().trace("PROP $prop #getters=${getters.size}")
        return getters.isNotEmpty() && getters.any { getter ->
            this(getter.invoke(left), getter.invoke(right))
        }
    }

    private fun processField(field: Field): Iterable<String> {
        return field.declaredAnnotations.filterIsInstance<XmlElement>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlAttribute>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlElementRef>().map { ann -> ann.name } +
                field.declaredAnnotations.filterIsInstance<XmlAnyAttribute>().map { field.name }
    }

    private fun compareSameClass(left: Any, right: Any): Boolean {
        var clazz: Class<in Any>? = left.javaClass
        logger().trace("SAME $${clazz}")
        var result = true
        while (result && clazz != null && clazz.packageName == TDefinitions::class.java.packageName) {
            var props: Iterable<String> = clazz.annotations
                    .filterIsInstance<XmlType>()
                    .flatMap { clzAnn -> clzAnn.propOrder.toSet() }
            props += clazz.declaredFields
                    .flatMap { field -> processField(field) }
            result = result && props
                    .toSet()
                    .filter { it.isNotEmpty() && it[0].isLetter() }
                    .all { prop -> processProp(left, right, prop, clazz!!) }
            clazz = clazz.superclass
        }
        return result
    }

    companion object {
        private val normalizer = Regex("\\s+")
    }

    private fun compareOther(left: Any, right: Any): Boolean {
        logger().trace("OTHER $left $right (equals: ${left == right})")
        if (left is Element && right is Element) {
            return left.toString() == right.toString()
        }
        if (left is QName && right is QName) {
            if (ignoreNSInQName && left != right && left.localPart == right.localPart) {
                logger().debug("Difference in NS of QNames: $left $right, assuming they are identical")
                return true
            } else
                return left == right
        }
        if (left is String && right is String) {
            return normalizer.replace(left, " ") == normalizer.replace(right, " ")
        }
        if (left is Map<*, *> && right is Map<*, *>) {
            return left.keys == right.keys && left.keys.all { key -> this(left[key], right[key]) }
        }
        return left == right
    }

    operator fun invoke(_left: Any?, _right: Any?): Boolean {
        logger().trace("NULL ${_left == null} ${_right == null}")
        if (_left == null || _right == null)
            return _left == null && _right == null
        var left: Any = _left
        var right: Any = _right
        if (left is JAXBElement<*>)
            left = left.value
        if (right is JAXBElement<*>)
            right = right.value
        logger().trace("CMP ${left.javaClass} ${right.javaClass}")
        if (left is Iterable<*> && right is Iterable<*>) {
            return (left zip right).all { (l, r) -> this(l, r) }
        } else if (left.javaClass.packageName != TDefinitions::class.java.packageName || right.javaClass.packageName != TDefinitions::class.java.packageName) {
            return compareOther(left, right)
        } else if (left::class == right::class) {
            if (Pair(left, right) in seen || Pair(right, left) in seen) {
                return true
            } else {
                seen.add(Pair(left, right))
                return compareSameClass(left, right)
            }
        } else {
            return false
        }
    }
}
